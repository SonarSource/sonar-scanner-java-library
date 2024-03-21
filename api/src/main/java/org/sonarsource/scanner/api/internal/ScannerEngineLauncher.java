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

import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sonarsource.scanner.api.ScanProperties;
import org.sonarsource.scanner.api.internal.cache.Logger;

public class ScannerEngineLauncher {

  private static final List<String> SENSITIVE_PROPERTIES = Arrays.asList(ScanProperties.TOKEN, ScanProperties.LOGIN);
  private static final String ENV_VAR_SONAR_TOKEN = "SONAR_TOKEN";
  private final JavaRunner javaRunner;
  private final File scannerEngineJar;
  private final Logger logger;

  public ScannerEngineLauncher(JavaRunner javaRunner, File scannerEngineJar, Logger logger) {
    this.javaRunner = javaRunner;
    this.scannerEngineJar = scannerEngineJar;
    this.logger = logger;
  }

  public void execute(Map<String, String> properties) {
    File propertyFile = buildPropertyFile(properties);
    javaRunner.execute(buildCommand(propertyFile), buildEnvVars(properties));
    try {
      Files.delete(propertyFile.toPath());
    } catch (IOException e) {
      logger.error("Failed to delete property file", e);
    }
  }

  private File buildPropertyFile(Map<String, String> properties) {
    try {
      File propertyFile = File.createTempFile("sonar-scanner", ".json");
      try (JsonWriter writer = new JsonWriter(new FileWriter(propertyFile, StandardCharsets.UTF_8))) {
        writer.beginArray();
        for (Map.Entry<String, String> prop : properties.entrySet()) {
          if (!SENSITIVE_PROPERTIES.contains(prop.getKey())) {
            writer.beginObject();
            writer.name("key").value(prop.getKey());
            writer.name("value").value(prop.getValue());
            writer.endObject();
          }
        }
        writer.endArray();
      }
      return propertyFile;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create property file", e);
    }
  }

  private List<String> buildCommand(File propertyFile) {
    List<String> command = new ArrayList<>();
    //command.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5007");
    command.add("-jar");
    command.add(scannerEngineJar.getAbsolutePath());
    command.add(propertyFile.getAbsolutePath());
    return command;
  }

  private Map<String, String> buildEnvVars(Map<String, String> properties) {
    Map<String, String> envVars = new HashMap<>();
    String token = properties.get(ScanProperties.TOKEN);
    if (token == null) {
      token = properties.get(ScanProperties.LOGIN);
    }
    envVars.put(ENV_VAR_SONAR_TOKEN, token);
    return envVars;
  }
}
