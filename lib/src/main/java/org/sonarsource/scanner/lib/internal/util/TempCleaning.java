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
package org.sonarsource.scanner.lib.internal.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.scanner.lib.internal.IsolatedLauncherFactory;

/**
 * The file sonar-runner-batch.jar is locked by the classloader on Windows and can't be dropped at the end of the execution.
 * See {@link IsolatedLauncherFactory}
 */
public class TempCleaning {

  private static final Logger LOG = LoggerFactory.getLogger(TempCleaning.class);

  static final int ONE_DAY_IN_MILLISECONDS = 24 * 60 * 60 * 1000;

  final Path tempDir;

  public TempCleaning() {
    this(Paths.get(System.getProperty("java.io.tmpdir")));
  }

  /**
   * For unit tests
   */
  TempCleaning(Path tempDir) {
    this.tempDir = tempDir;
  }

  public void clean() {
    LOG.debug("Start temp cleaning...");
    long cutoff = System.currentTimeMillis() - ONE_DAY_IN_MILLISECONDS;

    try (Stream<Path> files = Files.list(tempDir)) {
      files
        .filter(p -> p.getFileName().toString().startsWith("sonar-scanner-java-library-batch"))
        .filter(p -> lastModifiedTime(p) < cutoff)
        .forEach(Utils::deleteQuietly);
      LOG.debug("Temp cleaning done");
    } catch (IOException e) {
      LOG.warn("Failed to clean files in {}", tempDir, e);
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
