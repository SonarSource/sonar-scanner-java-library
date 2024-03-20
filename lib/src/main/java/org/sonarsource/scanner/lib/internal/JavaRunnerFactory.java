/*
 * SonarScanner Java Library
 * Copyright (C) 2011-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.scanner.lib.internal;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.sonarsource.scanner.lib.System2;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.cache.HashMismatchException;
import org.sonarsource.scanner.lib.internal.cache.Logger;
import org.sonarsource.scanner.lib.internal.http.ServerConnection;
import org.sonarsource.scanner.lib.internal.util.CompressionUtils;

import static java.lang.String.format;
import static org.sonarsource.scanner.lib.ScannerProperties.JAVA_EXECUTABLE_PATH;
import static org.sonarsource.scanner.lib.ScannerProperties.SCANNER_ARCH;
import static org.sonarsource.scanner.lib.ScannerProperties.SCANNER_OS;
import static org.sonarsource.scanner.lib.ScannerProperties.SKIP_JRE_PROVISIONING;
import static org.sonarsource.scanner.lib.Utils.deleteQuietly;

public class JavaRunnerFactory {

  static final String API_PATH_JRE = "/analysis/jres";
  private static final String EXTENSION_ZIP = "zip";
  private static final String EXTENSION_GZ = "gz";

  private final Logger logger;
  private final System2 system;

  public JavaRunnerFactory(Logger logger, System2 system) {
    this.logger = logger;
    this.system = system;
  }

  public JavaRunner createRunner(ServerConnection serverConnection, FileCache fileCache, Map<String, String> properties) {
    File javaExecutable = resolveJre(serverConnection, fileCache, properties);
    return new JavaRunner(javaExecutable, logger);
  }

  private File resolveJre(ServerConnection serverConnection, FileCache fileCache, Map<String, String> properties) {
    File javaExecutable = null;

    String javaExecutablePath = properties.get(JAVA_EXECUTABLE_PATH);
    if (javaExecutablePath != null) {
      javaExecutable = new File(javaExecutablePath);
      logger.info(format("JRE provisioning is disabled, the java executable %s will be used.", javaExecutable));
    } else {
      boolean skipJreProvisioning = Boolean.parseBoolean(properties.get(SKIP_JRE_PROVISIONING));
      if (skipJreProvisioning) {
        String javaHome = system.getEnvironmentVariable("JAVA_HOME");
        if (javaHome != null) {
          javaExecutable = new File(javaHome, "bin/java" + (SystemUtils.IS_OS_WINDOWS ? ".exe" : ""));
          logger.info(format("JRE provisioning is disabled, the java executable %s will be used.", javaExecutable));
        } else {
          logger.info("JRE provisioning is disabled, the java executable in the PATH will be used.");
        }
      } else {
        javaExecutable = getJreFromServer(serverConnection, fileCache, properties, true);
      }
    }

    return javaExecutable;
  }

  private File getJreFromServer(ServerConnection serverConnection, FileCache fileCache, Map<String, String> properties, boolean retry) {
    String os = properties.get(SCANNER_OS);
    String arch = properties.get(SCANNER_ARCH);
    logger.info(format("JRE provisioning: os[%s], arch[%s]", os, arch));

    try {
      var jreMetadata = getJreMetadata(serverConnection, os, arch);
      File cachedFile = fileCache.get(jreMetadata.getFilename(), jreMetadata.getSha256(), "SHA-256",
        new JreDownloader(serverConnection, jreMetadata));
      File extractedDirectory = extractArchive(cachedFile);
      return new File(extractedDirectory, jreMetadata.javaPath);
    } catch (HashMismatchException e) {
      if (retry) {
        // A new JRE might have been published between the metadata fetch and the download
        logger.error("Failed to get the JRE, retrying...");
        return getJreFromServer(serverConnection, fileCache, properties, false);
      }
      throw e;
    }
  }

  private static JreMetadata getJreMetadata(ServerConnection serverConnection, String os, String arch) {
    try {
      String response = serverConnection.callServerApi(format(API_PATH_JRE + "?os=%s&arch=%s", os, arch));
      Type listType = new TypeToken<ArrayList<JreMetadata>>() {
      }.getType();
      List<JreMetadata> jres = new Gson().fromJson(response, listType);
      if (jres.isEmpty()) {
        throw new IllegalStateException("No JRE metadata found for os[" + os + "] and arch[" + arch + "]");
      }
      return jres.get(0);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to get JRE metadata", e);
    }
  }

  static class JreMetadata extends ResourceMetadata {
    @SerializedName("id")
    private final String id;
    @SerializedName("javaPath")
    private final String javaPath;

    JreMetadata(String filename, String sha256, @Nullable String downloadUrl, String id, String javaPath) {
      super(filename, sha256, downloadUrl);
      this.id = id;
      this.javaPath = javaPath;
    }
  }

  private static File extractArchive(File cachedFile) {
    String filename = cachedFile.getName();
    File destDir = new File(cachedFile.getParentFile(), filename + "_unzip");
    File lockFile = new File(cachedFile.getParentFile(), filename + "_unzip.lock");
    if (!destDir.exists()) {
      try (FileOutputStream out = new FileOutputStream(lockFile)) {
        FileLock lock = createLockWithRetries(out.getChannel());
        try {
          // Recheck in case of concurrent processes
          if (!destDir.exists()) {
            File tempDir = Files.createTempDirectory(cachedFile.getParentFile().toPath(), "jre").toFile();
            extract(cachedFile, tempDir);
            Files.move(tempDir.toPath(), destDir.toPath());
          }
        } finally {
          lock.release();
        }
      } catch (IOException e) {
        throw new IllegalStateException("Failed to extract archive", e);
      } finally {
        deleteQuietly(lockFile.toPath());
      }
    }
    return destDir;
  }

  private static FileLock createLockWithRetries(FileChannel channel) throws IOException {
    int tryCount = 0;
    while (tryCount < 10) {
      tryCount++;
      try {
        return channel.lock();
      } catch (OverlappingFileLockException ofle) {
        // ignore overlapping file exception
      }
      try {
        Thread.sleep(200L * tryCount);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    throw new IOException("Unable to get lock after " + tryCount + " tries");
  }

  private static void extract(File compressedFile, File targetDir) throws IOException {
    String extension = compressedFile.getName().substring(compressedFile.getName().lastIndexOf('.') + 1);
    switch (extension) {
      case EXTENSION_ZIP:
        CompressionUtils.unzip(compressedFile, targetDir);
        break;
      case EXTENSION_GZ:
        CompressionUtils.extractTarGz(compressedFile, targetDir);
        break;
      default:
        throw new IllegalArgumentException("Unsupported compressed archive extension: " + extension);
    }
  }

  static class JreDownloader implements FileCache.Downloader {
    private final ServerConnection connection;
    private final JreMetadata jreMetadata;

    JreDownloader(ServerConnection connection, JreMetadata jreMetadata) {
      this.connection = connection;
      this.jreMetadata = jreMetadata;
    }

    @Override
    public void download(String filename, File toFile) throws IOException {
      if (StringUtils.isNotBlank(jreMetadata.getDownloadUrl())) {
        connection.downloadFile(jreMetadata.getDownloadUrl(), toFile.toPath(), false);
      } else {
        connection.downloadServerFile(API_PATH_JRE + "/" + jreMetadata.id, toFile.toPath());
      }
    }
  }
}
