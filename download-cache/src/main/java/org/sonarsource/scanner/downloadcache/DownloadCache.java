/*
 * SonarScanner Download Cache Utility
 * Copyright (C) 2011-2025 SonarSource SA
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
package org.sonarsource.scanner.downloadcache;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for managing Sonar Scanner download cache. You can download files into the cache and
 * later try to retrieve them. The file hash is used as the cache index (name is not reliable as files may come
 * from different SonarQube servers and have the same name but be actually different).
 */
public class DownloadCache {

  private static final Logger LOG = LoggerFactory.getLogger(DownloadCache.class);

  private final Path baseDir;
  private final Path tmpDir;
  private final FileHashes hashes;

  DownloadCache(Path baseDir, FileHashes fileHashes) {
    LOG.debug("Download cache base directory: {}", baseDir);
    this.hashes = fileHashes;
    this.baseDir = mkdirs(baseDir);
    this.tmpDir = mkdirs(baseDir.resolve("_tmp"));
  }

  public DownloadCache(Path baseDir) {
    this(baseDir, new FileHashes());
  }

  public Path getBaseDir() {
    return baseDir;
  }

  /**
   * Look for a file in the cache by its filename and hash. If the file is not
   * present, then return empty.
   */
  public Optional<Path> get(String filename, String hash) {
    Path cachedFile = hashDir(hash).resolve(filename);
    if (Files.exists(cachedFile)) {
      return Optional.of(cachedFile);
    }
    return Optional.empty();
  }

  public CachedFile getOrDownload(String filename, String expectedFileHash, String hashAlgorithm, Downloader downloader) throws HashMismatchException {
    // Does not fail if another process tries to create the directory at the same time.
    Path hashDir = hashDir(expectedFileHash);
    Path targetFile = hashDir.resolve(filename);
    if (Files.exists(targetFile)) {
      return new CachedFile(targetFile, true);
    }
    Path tempFile = newTempFile(filename);
    download(downloader, filename, tempFile);
    String downloadedFileHash = hashes.of(tempFile.toFile(), hashAlgorithm);
    if (!expectedFileHash.equals(downloadedFileHash)) {
      throw new HashMismatchException(expectedFileHash, downloadedFileHash, tempFile.toAbsolutePath());
    }
    mkdirs(hashDir);
    renameQuietly(tempFile, targetFile);
    return new CachedFile(targetFile, false);
  }

  private static void download(Downloader downloader, String filename, Path tempFile) {
    try {
      downloader.download(filename, tempFile);
    } catch (IOException e) {
      throw new IllegalStateException("Fail to download " + filename + " to " + tempFile, e);
    }
  }

  private static void renameQuietly(Path sourceFile, Path targetFile) {
    try {
      Files.move(sourceFile, targetFile, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ex) {
      LOG.warn("Unable to rename {} to {}", sourceFile.toAbsolutePath(), targetFile.toAbsolutePath());
      LOG.warn("A copy/delete will be tempted but with no guarantee of atomicity");
      try {
        Files.move(sourceFile, targetFile);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to move " + sourceFile.toAbsolutePath() + " to " + targetFile, e);
      }
    } catch (FileAlreadyExistsException e) {
      // File was probably cached by another process in the meantime
    } catch (IOException e) {
      throw new IllegalStateException("Failed to move " + sourceFile.toAbsolutePath() + " to " + targetFile, e);
    }
  }

  private Path hashDir(String hash) {
    return baseDir.resolve(hash);
  }

  private Path newTempFile(String filename) {
    int dotLocation = filename.lastIndexOf(".");
    String prefix = filename;
    String suffix = null;
    if (dotLocation > 0) {
      prefix = filename.substring(0, dotLocation);
      suffix = filename.substring(dotLocation + 1);
    }
    try {
      return Files.createTempFile(tmpDir, prefix, suffix);
    } catch (IOException e) {
      throw new IllegalStateException("Fail to create temp file in " + tmpDir, e);
    }
  }

  private static Path mkdirs(Path dir) {
    if (!Files.isDirectory(dir)) {
      LOG.debug("Create: {}", dir);
      try {
        Files.createDirectories(dir);
      } catch (IOException e) {
        throw new IllegalStateException("Unable to create directory: " + dir, e);
      }
    }
    return dir;
  }
}
