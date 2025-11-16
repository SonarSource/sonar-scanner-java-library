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
package org.sonarsource.scanner.lib.internal.facade;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.scanner.lib.AnalysisProperties;
import org.sonarsource.scanner.lib.ScannerProperties;

class Dirs {

  private static final Logger LOG = LoggerFactory.getLogger(Dirs.class);

  void init(Map<String, String> p) {
    String pathString = Optional.ofNullable(p.get(AnalysisProperties.PROJECT_BASEDIR)).orElse("");
    Path absoluteProjectPath = Paths.get(pathString).toAbsolutePath().normalize();
    if (!Files.isDirectory(absoluteProjectPath)) {
      throw new IllegalStateException("Project home must be an existing directory: " + pathString);
    }
    p.put(AnalysisProperties.PROJECT_BASEDIR, absoluteProjectPath.toString());

    Path workDirPath;
    pathString = Optional.ofNullable(p.get(ScannerProperties.WORK_DIR)).orElse("");
    if (pathString.trim().isEmpty()) {
      workDirPath = absoluteProjectPath.resolve(".scannerwork");
    } else {
      workDirPath = Paths.get(pathString);
      if (!workDirPath.isAbsolute()) {
        workDirPath = absoluteProjectPath.resolve(pathString);
      }
    }
    var normalized = workDirPath.normalize().toString();
    p.put(ScannerProperties.WORK_DIR, normalized);
    LOG.debug("Work directory: {}", normalized);
  }
}
