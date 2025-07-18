/*
 * SonarScanner Java Library
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
package org.sonarsource.scanner.lib.internal.facade.inprocess;

import org.sonarsource.scanner.downloadcache.DownloadCache;
import org.sonarsource.scanner.lib.internal.http.ScannerHttpClient;

class LegacyScannerEngineDownloaderFactory {
  private final ScannerHttpClient scannerHttpClient;
  private final DownloadCache downloadCache;

  LegacyScannerEngineDownloaderFactory(ScannerHttpClient conn, DownloadCache downloadCache) {
    this.scannerHttpClient = conn;
    this.downloadCache = downloadCache;
  }

  LegacyScannerEngineDownloader create() {
    BootstrapIndexDownloader bootstrapIndexDownloader = new BootstrapIndexDownloader(scannerHttpClient);
    LegacyScannerEngineDownloader.ScannerFileDownloader scannerFileDownloader = new LegacyScannerEngineDownloader.ScannerFileDownloader(scannerHttpClient);
    JarExtractor jarExtractor = new JarExtractor();
    return new LegacyScannerEngineDownloader(scannerFileDownloader, bootstrapIndexDownloader, downloadCache, jarExtractor);
  }
}
