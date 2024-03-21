/*
 * SonarQube Scanner Commons
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
package org.sonarsource.scanner.api.internal;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import org.sonarsource.scanner.api.CompressionUtils;
import org.sonarsource.scanner.api.internal.cache.FileCache;

import static java.lang.String.format;
import static org.sonarsource.scanner.api.Utils.deleteQuietly;

public class JreDownloader {
  private static final String EXTENSION_ZIP = "zip";
  private static final String EXTENSION_GZ = "gz";
  private final ServerConnection serverConnection;
  private final FileCache fileCache;

  public JreDownloader(ServerConnection serverConnection, FileCache fileCache) {
    this.serverConnection = serverConnection;
    this.fileCache = fileCache;
  }

  public File download(OsArchProvider.OsArch osArch) {
    var jreInfo = getJreInfo(serverConnection, osArch);
    File cachedFile = fileCache.get(jreInfo.filename, jreInfo.checksum,
      new JreArchiveDownloader(serverConnection));
    File extractedDirectory = extractArchive(cachedFile);
    return new File(extractedDirectory, jreInfo.javaPath);
  }

  private static JreInfo getJreInfo(ServerConnection serverConnection, OsArchProvider.OsArch osArch) {
    try {
      String jreInfoResponse = serverConnection.downloadString(
        String.format("/api/v2/scanner/jre/info?os=%s&arch=%s", osArch.getOs(), osArch.getArch()));
      return new Gson().fromJson(jreInfoResponse, JreInfo.class);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to get JRE info", e);
    }
  }

  private static class JreInfo {

    @SerializedName("filename")
    private String filename;

    @SerializedName("checksum")
    private String checksum;

    @SerializedName("javaPath")
    private String javaPath;

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
    while (tryCount++ < 10) {
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

  private static class JreArchiveDownloader implements FileCache.Downloader {
    private final ServerConnection connection;

    JreArchiveDownloader(ServerConnection connection) {
      this.connection = connection;
    }

    @Override
    public void download(String filename, File toFile) throws IOException {
      connection.downloadFile(format("/api/v2/scanner/jre/download?filename=%s", filename), toFile.toPath());
    }
  }
}
