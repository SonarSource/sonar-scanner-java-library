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
package org.sonarsource.scanner.lib.internal.cache;

import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nullable;

public class FileCacheBuilder {
  private final Logger logger;
  private Path sonarUserHome;

  public FileCacheBuilder(Logger logger) {
    this.logger = logger;
  }

  public FileCacheBuilder setSonarUserHome(@Nullable String userHomeProperty) {
    this.sonarUserHome = (userHomeProperty == null) ? null : Paths.get(userHomeProperty);
    return this;
  }

  public FileCache build() {
    if (sonarUserHome == null) {
      sonarUserHome = findDefaultHome();
    }
    var cacheDir = sonarUserHome.resolve("cache");
    return FileCache.create(cacheDir, logger);
  }

  private static Path findDefaultHome() {
    return Paths.get("").resolve(".sonar");
  }
}
