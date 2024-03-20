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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.sonarsource.scanner.lib.ScannerProperties;
import org.sonarsource.scanner.lib.internal.cache.Logger;

public class ScannerEngineLauncher {

  private static final String JSON_FIELD_SCANNER_PROPERTIES = "scannerProperties";
  private final JavaRunner javaRunner;
  private final File scannerEngineJar;
  private final Logger logger;

  public ScannerEngineLauncher(JavaRunner javaRunner, File scannerEngineJar, Logger logger) {
    this.javaRunner = javaRunner;
    this.scannerEngineJar = scannerEngineJar;
    this.logger = logger;
  }

  public void execute(Map<String, String> properties) {
    logger.info("Starting scanner-engine");
    javaRunner.execute(buildArgs(properties), buildJsonProperties(properties));
  }

  private List<String> buildArgs(Map<String, String> properties) {
    List<String> args = new ArrayList<>();
    String javaOpts = properties.get(ScannerProperties.SCANNER_JAVA_OPTS);
    if (javaOpts != null) {
      args.add(javaOpts);
    }
    args.add("-jar");
    args.add(scannerEngineJar.getAbsolutePath());
    return args;
  }

  private static String buildJsonProperties(Map<String, String> properties) {
    JsonObject jsonObject = new JsonObject();
    jsonObject.add(JSON_FIELD_SCANNER_PROPERTIES, new Gson().toJsonTree(properties));
    return new Gson().toJson(jsonObject);
  }
}
