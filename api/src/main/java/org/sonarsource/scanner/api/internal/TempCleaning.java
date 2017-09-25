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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.sonarsource.scanner.api.Utils;
import org.sonarsource.scanner.api.internal.cache.Logger;

/**
 * The file sonar-runner-batch.jar is locked by the classloader on Windows and can't be dropped at the end of the execution.
 * See {@link IsolatedLauncherFactory}
 */
class TempCleaning {
  static final int ONE_DAY_IN_MILLISECONDS = 24 * 60 * 60 * 1000;

  final Path tempDir;

  private final Logger logger;

  TempCleaning(Logger logger) {
    this(Paths.get(System.getProperty("java.io.tmpdir")), logger);
  }

  /**
   * For unit tests
   */
  TempCleaning(Path tempDir, Logger logger) {
    this.logger = logger;
    this.tempDir = tempDir;
  }

  void clean() {
    logger.debug("Start temp cleaning...");
    long cutoff = System.currentTimeMillis() - ONE_DAY_IN_MILLISECONDS;

    try (Stream<Path> files = Files.list(tempDir)) {
      files
        .filter(p -> p.getFileName().toString().startsWith("ssonar-scanner-api-batch"))
        .filter(p -> lastModifiedTime(p) < cutoff)
        .forEach(Utils::deleteQuietly);
      logger.debug("Temp cleaning done");
    } catch (IOException e) {
      logger.warn("Failed to clean files in " + tempDir.toString() + ": " + e.getMessage());
    }
  }

  private static long lastModifiedTime(Path file) {
    try {
      return Files.getLastModifiedTime(file).toMillis();
    } catch (IOException e) {
      // ignore this file
      return System.currentTimeMillis();
    }
  }
}
