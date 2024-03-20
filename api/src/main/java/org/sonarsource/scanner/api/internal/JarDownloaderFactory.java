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

import org.sonarsource.scanner.api.internal.JarDownloader.ScannerFileDownloader;
import org.sonarsource.scanner.api.internal.cache.FileCache;
import org.sonarsource.scanner.api.internal.cache.Logger;

public class JarDownloaderFactory {
  private final ServerConnection serverConnection;
  private final Logger logger;
  private final FileCache fileCache;

  public JarDownloaderFactory(ServerConnection conn, Logger logger, FileCache fileCache) {
    this.serverConnection = conn;
    this.logger = logger;
    this.fileCache = fileCache;
  }

  public JarDownloader create() {
    BootstrapIndexDownloader bootstrapIndexDownloader = new BootstrapIndexDownloader(serverConnection, logger);
    ScannerFileDownloader scannerFileDownloader = new ScannerFileDownloader(serverConnection);
    JarExtractor jarExtractor = new JarExtractor();
    return new JarDownloader(scannerFileDownloader, bootstrapIndexDownloader, fileCache, jarExtractor, logger);
  }
}
