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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.sonarsource.scanner.lib.internal.BootstrapIndexDownloader.JarEntry;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.cache.Logger;
import org.sonarsource.scanner.lib.internal.http.ServerConnection;

import static java.lang.String.format;

class JarDownloader {
  private final FileCache fileCache;
  private final JarExtractor jarExtractor;
  private final Logger logger;
  private final ScannerFileDownloader scannerFileDownloader;
  private final BootstrapIndexDownloader bootstrapIndexDownloader;

  JarDownloader(ScannerFileDownloader scannerFileDownloader, BootstrapIndexDownloader bootstrapIndexDownloader, FileCache fileCache, JarExtractor jarExtractor, Logger logger) {
    this.scannerFileDownloader = scannerFileDownloader;
    this.bootstrapIndexDownloader = bootstrapIndexDownloader;
    this.logger = logger;
    this.fileCache = fileCache;
    this.jarExtractor = jarExtractor;
  }

  List<File> download() {
    List<File> files = new ArrayList<>();
    logger.debug("Extract sonar-scanner-java-library-batch in temp...");
    files.add(jarExtractor.extractToTemp("sonar-scanner-java-library-batch").toFile());
    files.addAll(getScannerEngineFiles());
    return files;
  }

  private List<File> getScannerEngineFiles() {
    Collection<JarEntry> index = bootstrapIndexDownloader.getIndex();
    return index.stream()
      .map(jar -> fileCache.get(jar.getFilename(), jar.getHash(), scannerFileDownloader))
      .collect(Collectors.toList());
  }

  static class ScannerFileDownloader implements FileCache.Downloader {
    private final ServerConnection connection;

    ScannerFileDownloader(ServerConnection conn) {
      this.connection = conn;
    }

    @Override
    public void download(String filename, File toFile) throws IOException {
      connection.downloadFile(format("/batch/file?name=%s", filename), toFile.toPath());
    }
  }
}
