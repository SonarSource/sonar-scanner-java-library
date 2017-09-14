/*
 * SonarQube Scanner API
 * Copyright (C) 2011-2017 SonarSource SA
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

import javax.annotation.Nullable;
import org.sonarsource.scanner.api.internal.JarDownloader.ScannerFileDownloader;
import org.sonarsource.scanner.api.internal.cache.FileCache;
import org.sonarsource.scanner.api.internal.cache.FileCacheBuilder;
import org.sonarsource.scanner.api.internal.cache.Logger;

class JarDownloaderFactory {
  private final ServerConnection serverConnection;
  private final Logger logger;
  private final String userHome;

  JarDownloaderFactory(ServerConnection conn, Logger logger, @Nullable String userHome) {
    this.serverConnection = conn;
    this.logger = logger;
    this.userHome = userHome;
  }

  JarDownloader create() {
    FileCache fileCache = new FileCacheBuilder(logger)
      .setUserHome(userHome)
      .build();
    BootstrapIndexDownloader bootstrapIndexDownloader = new BootstrapIndexDownloader(serverConnection, logger);
    ScannerFileDownloader scannerFileDownloader = new ScannerFileDownloader(serverConnection);
    JarExtractor jarExtractor = new JarExtractor();
    return new JarDownloader(scannerFileDownloader, bootstrapIndexDownloader, fileCache, jarExtractor, logger);
  }
}
