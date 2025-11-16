/*
 * SonarScanner Java Library
 * Copyright (C) 2011-2025 SonarSource Sàrl
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
package org.sonarsource.scanner.lib.internal.facade.forked;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.scanner.downloadcache.CachedFile;
import org.sonarsource.scanner.downloadcache.DownloadCache;
import org.sonarsource.scanner.downloadcache.Downloader;
import org.sonarsource.scanner.downloadcache.HashMismatchException;
import org.sonarsource.scanner.lib.internal.MessageException;
import org.sonarsource.scanner.lib.internal.http.ScannerHttpClient;
import org.sonarsource.scanner.lib.internal.util.CompressionUtils;
import org.sonarsource.scanner.lib.internal.util.ProcessWrapperFactory;
import org.sonarsource.scanner.lib.internal.util.System2;

import static java.lang.String.format;
import static org.sonarsource.scanner.lib.ScannerProperties.JAVA_EXECUTABLE_PATH;
import static org.sonarsource.scanner.lib.ScannerProperties.SCANNER_ARCH;
import static org.sonarsource.scanner.lib.ScannerProperties.SCANNER_OS;
import static org.sonarsource.scanner.lib.ScannerProperties.SKIP_JRE_PROVISIONING;
import static org.sonarsource.scanner.lib.internal.util.Utils.deleteQuietly;

public class JavaRunnerFactory {

  private static final Logger LOG = LoggerFactory.getLogger(JavaRunnerFactory.class);

  static final String API_PATH_JRE = "/analysis/jres";
  private static final String EXTENSION_ZIP = "zip";
  private static final String EXTENSION_GZ = "gz";

  private final System2 system;
  private final ProcessWrapperFactory processWrapperFactory;

  public JavaRunnerFactory(System2 system, ProcessWrapperFactory processWrapperFactory) {
    this.system = system;
    this.processWrapperFactory = processWrapperFactory;
  }

  public JavaRunner createRunner(ScannerHttpClient scannerHttpClient, DownloadCache downloadCache, Map<String, String> properties) {
    String javaExecutablePropValue = properties.get(JAVA_EXECUTABLE_PATH);
    if (javaExecutablePropValue != null) {
      LOG.info("Using the configured java executable '{}'", javaExecutablePropValue);
      return new JavaRunner(Paths.get(javaExecutablePropValue), JreCacheHit.DISABLED);
    }
    boolean skipJreProvisioning = Boolean.parseBoolean(properties.get(SKIP_JRE_PROVISIONING));
    if (skipJreProvisioning) {
      LOG.info("JRE provisioning is disabled");
    } else {
      var cachedFile = getJreFromServer(scannerHttpClient, downloadCache, properties, true);
      if (cachedFile.isPresent()) {
        return new JavaRunner(cachedFile.get().getPath(), cachedFile.get().didCacheHit() ? JreCacheHit.HIT : JreCacheHit.MISS);
      }
    }
    String javaHome = system.getEnvironmentVariable("JAVA_HOME");
    var javaExe = "java" + (isOsWindows() ? ".exe" : "");
    if (javaHome != null) {
      var javaExecutable = Paths.get(javaHome, "bin", javaExe);
      if (Files.exists(javaExecutable)) {
        LOG.info("Using the java executable '{}' from JAVA_HOME", javaExecutable);
        return new JavaRunner(javaExecutable, JreCacheHit.DISABLED);
      }
    }
    LOG.info("The java executable in the PATH will be used");
    return new JavaRunner(isOsWindows() ? findJavaInPath(javaExe) : Paths.get(javaExe), JreCacheHit.DISABLED);
  }

  private boolean isOsWindows() {
    String osName = system.getProperty("os.name");
    return osName != null && osName.startsWith("Windows");
  }

  private Path findJavaInPath(String javaExe) {
    // Windows will search current directory in addition to the PATH variable, which is unsecure.
    // To avoid it we use where.exe to find the java binary only in PATH.
    try {
      ProcessWrapperFactory.ProcessWrapper process = processWrapperFactory.create("C:\\Windows\\System32\\where.exe", "$PATH:" + javaExe);

      Path javaExecutable;
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        javaExecutable = Paths.get(reader.lines().findFirst().orElseThrow());
        LOG.debug("Found java executable in PATH at '{}'", javaExecutable.toAbsolutePath());
      }

      int exit = process.waitFor();
      if (exit != 0) {
        throw new IllegalStateException(format("Command execution exited with code: %d", exit));
      }

      return javaExecutable;
    } catch (Exception e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Cannot find java executable in PATH", e);
    }
  }

  private static Optional<CachedFile> getJreFromServer(ScannerHttpClient scannerHttpClient, DownloadCache downloadCache, Map<String, String> properties, boolean retry) {
    String os = properties.get(SCANNER_OS);
    String arch = properties.get(SCANNER_ARCH);
    LOG.info("JRE provisioning: os[{}], arch[{}]", os, arch);

    try {
      var jreMetadata = getJreMetadata(scannerHttpClient, os, arch);
      if (jreMetadata.isEmpty()) {
        LOG.info("No JRE found for this OS/architecture");
        return Optional.empty();
      }
      var cachedFile = downloadCache.getOrDownload(jreMetadata.get().getFilename(), jreMetadata.get().getSha256(), "SHA-256",
        new JreDownloader(scannerHttpClient, jreMetadata.get()));
      var extractedDirectory = extractArchive(cachedFile.getPath());
      return Optional.of(new CachedFile(extractedDirectory.resolve(jreMetadata.get().javaPath), cachedFile.didCacheHit()));
    } catch (HashMismatchException e) {
      if (retry) {
        // A new JRE might have been published between the metadata fetch and the download
        LOG.warn("Failed to get the JRE, retrying...");
        return getJreFromServer(scannerHttpClient, downloadCache, properties, false);
      }
      throw new IllegalStateException("Unable to provision the JRE", e);
    }
  }

  private static Optional<JreMetadata> getJreMetadata(ScannerHttpClient scannerHttpClient, String os, String arch) {
    try {
      String response = scannerHttpClient.callRestApi(format(API_PATH_JRE + "?os=%s&arch=%s", os, arch));
      Type listType = new TypeToken<ArrayList<JreMetadata>>() {
      }.getType();
      List<JreMetadata> jres = new Gson().fromJson(response, listType);
      return jres.stream().findFirst();
    } catch (Exception e) {
      throw new MessageException("Failed to query JRE metadata: " + e.getMessage(), e);
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

  private static Path extractArchive(Path cachedFile) {
    String filename = cachedFile.getFileName().toString();
    var destDir = cachedFile.getParent().resolve(filename + "_extracted");
    var lockFile = cachedFile.getParent().resolve(filename + "_extracted.lock");
    if (!Files.exists(destDir)) {
      try (FileOutputStream out = new FileOutputStream(lockFile.toFile())) {
        FileLock lock = createLockWithRetries(out.getChannel());
        try {
          // Recheck in case of concurrent processes
          if (!Files.exists(destDir)) {
            var tempDir = Files.createTempDirectory(cachedFile.getParent(), "jre");
            extract(cachedFile, tempDir);
            Files.move(tempDir, destDir);
          }
        } finally {
          lock.release();
        }
      } catch (IOException e) {
        throw new IllegalStateException("Failed to extract archive", e);
      } finally {
        deleteQuietly(lockFile);
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

  private static void extract(Path compressedFile, Path targetDir) throws IOException {
    var filename = compressedFile.getFileName().toString();
    String extension = filename.substring(filename.lastIndexOf('.') + 1);
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

  static class JreDownloader implements Downloader {
    private final ScannerHttpClient connection;
    private final JreMetadata jreMetadata;

    JreDownloader(ScannerHttpClient connection, JreMetadata jreMetadata) {
      this.connection = connection;
      this.jreMetadata = jreMetadata;
    }

    @Override
    public void download(String filename, Path toFile) throws IOException {
      if (StringUtils.isNotBlank(jreMetadata.getDownloadUrl())) {
        connection.downloadFromExternalUrl(jreMetadata.getDownloadUrl(), toFile);
      } else {
        connection.downloadFromRestApi(API_PATH_JRE + "/" + jreMetadata.id, toFile);
      }
    }
  }
}
