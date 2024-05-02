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

import java.nio.file.Path;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.cache.FileCacheBuilder;
import org.sonarsource.scanner.lib.internal.cache.Logger;
import org.sonarsource.scanner.lib.internal.http.ServerConnection;

class JarDownloaderFactory {
  private final ServerConnection serverConnection;
  private final Logger logger;
  private final Path sonarUserHome;

  JarDownloaderFactory(ServerConnection conn, Logger logger, Path sonarUserHome) {
    this.serverConnection = conn;
    this.logger = logger;
    this.sonarUserHome = sonarUserHome;
  }

  JarDownloader create() {
    FileCache fileCache = new FileCacheBuilder(logger, sonarUserHome)
      .build();
    BootstrapIndexDownloader bootstrapIndexDownloader = new BootstrapIndexDownloader(serverConnection, logger);
    JarDownloader.ScannerFileDownloader scannerFileDownloader = new JarDownloader.ScannerFileDownloader(serverConnection);
    JarExtractor jarExtractor = new JarExtractor();
    return new JarDownloader(scannerFileDownloader, bootstrapIndexDownloader, fileCache, jarExtractor, logger);
  }
}
