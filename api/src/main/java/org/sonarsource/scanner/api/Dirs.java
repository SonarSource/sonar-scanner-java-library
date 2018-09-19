/*
 * SonarQube Scanner API
 * Copyright (C) 2011-2018 SonarSource SA
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
package org.sonarsource.scanner.api;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import org.sonarsource.scanner.api.internal.cache.Logger;

class Dirs {

  private final Logger logger;

  Dirs(Logger logger) {
    this.logger = logger;
  }

  void init(Map<String, String> p) {
    boolean onProject = Utils.taskRequiresProject(p);
    if (onProject) {
      initProjectDirs(p);
    } else {
      initTaskDirs(p);
    }
  }

  private void initProjectDirs(Map<String, String> p) {
    String pathString = Optional.ofNullable(p.get(ScanProperties.PROJECT_BASEDIR)).orElse("");
    Path absoluteProjectPath = Paths.get(pathString).toAbsolutePath().normalize();
    if (!Files.isDirectory(absoluteProjectPath)) {
      throw new IllegalStateException("Project home must be an existing directory: " + pathString);
    }
    p.put(ScanProperties.PROJECT_BASEDIR, absoluteProjectPath.toString());

    Path workDirPath;
    pathString = Optional.ofNullable(p.get(ScannerProperties.WORK_DIR)).orElse("");
    if ("".equals(pathString.trim())) {
      workDirPath = absoluteProjectPath.resolve(".scannerwork");
    } else {
      workDirPath = Paths.get(pathString);
      if (!workDirPath.isAbsolute()) {
        workDirPath = absoluteProjectPath.resolve(pathString);
      }
    }
    p.put(ScannerProperties.WORK_DIR, workDirPath.normalize().toString());
    logger.debug("Work directory: " + workDirPath.normalize().toString());
  }

  /**
   * Non-scan task
   */
  private static void initTaskDirs(Map<String, String> p) {
    String path = Optional.ofNullable(p.get(ScannerProperties.WORK_DIR)).orElse(".");
    File workDir = new File(path);
    p.put(ScannerProperties.WORK_DIR, workDir.getAbsolutePath());
  }
}
