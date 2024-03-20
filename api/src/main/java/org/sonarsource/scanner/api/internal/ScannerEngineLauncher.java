/*
 * SonarQube Scanner Commons
 * Copyright (C) 2011-2023 SonarSource SA
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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScannerEngineLauncher {

  private final JavaRunner javaRunner;
  private final File scannerEngineJar;

  public ScannerEngineLauncher(JavaRunner javaRunner, File scannerEngineJar) {
    this.javaRunner = javaRunner;
    this.scannerEngineJar = scannerEngineJar;
  }

  public void execute(Path propertyFile, String token) {
    Map<String, String> envVars = new HashMap<>();
    envVars.put("SONAR_TOKEN", token);
    javaRunner.execute(buildCommand(propertyFile), envVars);
  }

  private List<String> buildCommand(Path propertyFile) {
    List<String> command = new ArrayList<>();
    //command.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5007");
    command.add("-jar");
    command.add(scannerEngineJar.getAbsolutePath());
    command.add(propertyFile.toString());
    return command;
  }
}
