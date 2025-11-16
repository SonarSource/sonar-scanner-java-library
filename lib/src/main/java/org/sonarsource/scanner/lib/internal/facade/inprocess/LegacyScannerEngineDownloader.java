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
package org.sonarsource.scanner.lib.internal.facade.inprocess;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.scanner.downloadcache.CachedFile;
import org.sonarsource.scanner.downloadcache.DownloadCache;
import org.sonarsource.scanner.downloadcache.Downloader;
import org.sonarsource.scanner.downloadcache.HashMismatchException;
import org.sonarsource.scanner.lib.internal.facade.inprocess.BootstrapIndexDownloader.JarEntry;
import org.sonarsource.scanner.lib.internal.http.ScannerHttpClient;

import static java.lang.String.format;

/**
 * The scanner engine used to be downloaded from /batch/index and /batch/file, and was even made of multiple files to be put on the classpath.
 */
class LegacyScannerEngineDownloader {

  private static final Logger LOG = LoggerFactory.getLogger(LegacyScannerEngineDownloader.class);

  private final DownloadCache downloadCache;
  private final JarExtractor jarExtractor;
  private final ScannerFileDownloader scannerFileDownloader;
  private final BootstrapIndexDownloader bootstrapIndexDownloader;

  LegacyScannerEngineDownloader(ScannerFileDownloader scannerFileDownloader, BootstrapIndexDownloader bootstrapIndexDownloader, DownloadCache downloadCache,
    JarExtractor jarExtractor) {
    this.scannerFileDownloader = scannerFileDownloader;
    this.bootstrapIndexDownloader = bootstrapIndexDownloader;
    this.downloadCache = downloadCache;
    this.jarExtractor = jarExtractor;
  }

  List<CachedFile> getOrDownload() {
    List<CachedFile> files = new ArrayList<>();
    LOG.debug("Extract sonar-scanner-java-library-batch in temp...");
    files.add(new CachedFile(jarExtractor.extractToTemp("sonar-scanner-java-library-batch"), true));
    files.addAll(getOrDownloadScannerEngineFiles());
    return files;
  }

  private List<CachedFile> getOrDownloadScannerEngineFiles() {
    Collection<JarEntry> index = bootstrapIndexDownloader.getIndex();
    return index.stream()
      .map(jar -> {
        try {
          return downloadCache.getOrDownload(jar.getFilename(), jar.getHash(), "MD5", scannerFileDownloader);
        } catch (HashMismatchException e) {
          throw new IllegalStateException("Unable to provision the Scanner Engine", e);
        }
      })
      .collect(Collectors.toList());
  }

  static class ScannerFileDownloader implements Downloader {
    private final ScannerHttpClient connection;

    ScannerFileDownloader(ScannerHttpClient conn) {
      this.connection = conn;
    }

    @Override
    public void download(String filename, Path toFile) throws IOException {
      connection.downloadFromWebApi(format("/batch/file?name=%s", filename), toFile);
    }
  }
}
