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
package org.sonarsource.scanner.lib;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

/**
 * Utility class to load configuration from environment variables.
 */
public class EnvironmentConfig {

  private static final String SONAR_SCANNER_JSON_PARAMS = "SONAR_SCANNER_JSON_PARAMS";
  private static final String SONARQUBE_SCANNER_PARAMS = "SONARQUBE_SCANNER_PARAMS";
  private static final String GENERIC_ENV_PREFIX = "SONAR_SCANNER_";
  private static final String SONAR_HOST_URL_ENV_VAR = "SONAR_HOST_URL";
  private static final String SONAR_USER_HOME_ENV_VAR = "SONAR_USER_HOME";
  private static final String TOKEN_ENV_VARIABLE = "SONAR_TOKEN";

  private EnvironmentConfig() {
    // only static methods
  }

  public static Map<String, String> load(LogOutput logger) {
    return load(System.getenv(), logger);
  }

  static Map<String, String> load(Map<String, String> env, LogOutput logger) {
    var loadedProps = new HashMap<String, String>();
    Optional.ofNullable(env.get(SONAR_HOST_URL_ENV_VAR)).ifPresent(url -> loadedProps.put(ScannerProperties.HOST_URL, url));
    Optional.ofNullable(env.get(SONAR_USER_HOME_ENV_VAR)).ifPresent(path -> loadedProps.put(ScannerProperties.SONAR_USER_HOME, path));
    Optional.ofNullable(env.get(TOKEN_ENV_VARIABLE)).ifPresent(path -> loadedProps.put(ScannerProperties.SONAR_TOKEN, path));
    env.forEach((key, value) -> {
      if (!key.equals(SONAR_SCANNER_JSON_PARAMS) && key.startsWith(GENERIC_ENV_PREFIX)) {
        processEnvVariable(key, value, loadedProps, logger);
      }
    });
    var jsonParams = env.get(SONAR_SCANNER_JSON_PARAMS);
    var oldJsonParams = env.get(SONARQUBE_SCANNER_PARAMS);
    if (jsonParams != null) {
      if (oldJsonParams != null && !oldJsonParams.equals(jsonParams)) {
        logger.log("Ignoring environment variable '" + SONARQUBE_SCANNER_PARAMS + "' because '" + SONAR_SCANNER_JSON_PARAMS + "' is set", LogOutput.Level.WARN);
      }
      parseJsonPropertiesFromEnv(jsonParams, loadedProps, SONAR_SCANNER_JSON_PARAMS, logger);
    } else if (oldJsonParams != null) {
      parseJsonPropertiesFromEnv(oldJsonParams, loadedProps, SONARQUBE_SCANNER_PARAMS, logger);
    }
    return loadedProps;
  }

  private static void parseJsonPropertiesFromEnv(String jsonParams, Map<String, String> inputProperties, String envVariableName, LogOutput logger) {
    try {
      var jsonProperties = new Gson().<Map<String, String>>fromJson(jsonParams, Map.class);
      if (jsonProperties != null) {
        jsonProperties.forEach((key, value) -> {
          if (inputProperties.containsKey(key)) {
            if (!inputProperties.get(key).equals(value)) {
              logger.log("Ignoring property '" + key + "' from env variable '" + envVariableName + "' because it is already defined", LogOutput.Level.WARN);
            }
          } else {
            inputProperties.put(key, value);
          }
        });
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to parse JSON properties from environment variable '" + envVariableName + "'", e);
    }
  }

  private static void processEnvVariable(String key, String value, Map<String, String> inputProperties, LogOutput logger) {
    var suffix = key.substring(GENERIC_ENV_PREFIX.length());
    if (suffix.isEmpty()) {
      return;
    }
    var toCamelCase = Stream.of(suffix.split("_"))
      .map(String::toLowerCase)
      .reduce((a, b) -> a + StringUtils.capitalize(b)).orElseThrow();
    var propKey = "sonar.scanner." + toCamelCase;
    if (inputProperties.containsKey(propKey)) {
      if (!inputProperties.get(propKey).equals(value)) {
        logger.log("Ignoring environment variable '" + key + "' because it is already defined in the properties", LogOutput.Level.WARN);
      }
    } else {
      inputProperties.put(propKey, value);
    }
  }

}
