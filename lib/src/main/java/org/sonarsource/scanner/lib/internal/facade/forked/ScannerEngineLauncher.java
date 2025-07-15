/*
 * SonarScanner Java Library
 * Copyright (C) 2011-2025 SonarSource SA
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
package org.sonarsource.scanner.lib.internal.facade.forked;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.scanner.lib.ScannerProperties;
import org.sonarsource.scanner.lib.internal.cache.CachedFile;
import org.sonarsource.scanner.lib.internal.http.OkHttpClientFactory;

public class ScannerEngineLauncher {
  private static final Set<String> SENSITIVE_JVM_ARGUMENTS = Set.of(
    "sonar.login",
    "password",
    "token");

  private static final Logger LOG = LoggerFactory.getLogger(ScannerEngineLauncher.class);

  private static final String JSON_FIELD_SCANNER_PROPERTIES = "scannerProperties";
  private final JavaRunner javaRunner;
  private final CachedFile scannerEngineJar;

  public ScannerEngineLauncher(JavaRunner javaRunner, CachedFile scannerEngineJar) {
    this.javaRunner = javaRunner;
    this.scannerEngineJar = scannerEngineJar;
  }

  public boolean execute(Map<String, String> properties) {
    return javaRunner.execute(buildArgs(properties), buildJsonProperties(properties), ScannerEngineLauncher::tryParse);
  }

  static void tryParse(String stdout) {
    try {
      var log = new Gson().fromJson(stdout, Log.class);
      StringBuilder sb = new StringBuilder();
      if (log.message != null) {
        sb.append(log.message);
      }
      if (log.message != null && log.stacktrace != null) {
        sb.append("\n");
      }
      if (log.stacktrace != null) {
        sb.append(log.stacktrace);
      }
      log(log.level, sb.toString());
    } catch (Exception e) {
      LOG.info("[stdout] {}", stdout);
    }
  }

  private static void log(String level, String msg) {
    switch (level) {
      case "ERROR":
        LOG.error(msg);
        break;
      case "WARN":
        LOG.warn(msg);
        break;
      case "DEBUG":
        LOG.debug(msg);
        break;
      case "TRACE":
        LOG.trace(msg);
        break;
      case "INFO":
      default:
        LOG.info(msg);
    }
  }

  private static class Log {
    @SerializedName("level")
    private String level;
    @SerializedName("message")
    private String message;
    @SerializedName("stacktrace")
    private String stacktrace;
  }

  private List<String> buildArgs(Map<String, String> properties) {
    List<String> args = new ArrayList<>();
    String javaOpts = properties.get(ScannerProperties.SCANNER_JAVA_OPTS);
    if (javaOpts != null) {
      var split = split(javaOpts);
      LOG.info("SONAR_SCANNER_JAVA_OPTS={}", redactSensitiveArguments(split));
      args.addAll(split);
    }
    args.add("-D" + OkHttpClientFactory.BC_IGNORE_USELESS_PASSWD + "=true");
    args.add("-jar");
    args.add(scannerEngineJar.getPathInCache().toAbsolutePath().toString());
    return args;
  }

  private static String redactSensitiveArguments(List<String> scannerOpts) {
    return scannerOpts.stream()
      .map(ScannerEngineLauncher::redactArgumentIfSensistive)
      .collect(Collectors.joining(" "));
  }

  private static String redactArgumentIfSensistive(String argument) {
    String[] elems = argument.split("=");
    if (elems.length > 0 && SENSITIVE_JVM_ARGUMENTS.stream().anyMatch(p -> elems[0].toLowerCase(Locale.ENGLISH).contains(p))) {
      return elems[0] + "=*";
    }
    return argument;
  }

  private static List<String> split(String value) {
    return Arrays.stream(value.split("\\s+"))
      .map(String::trim)
      .filter(s -> !s.isEmpty())
      .collect(Collectors.toList());
  }

  private static String buildJsonProperties(Map<String, String> properties) {
    JsonArray propertiesArray = new JsonArray();
    properties.entrySet().stream()
      .filter(prop -> prop.getKey() != null)
      .sorted(Map.Entry.comparingByKey()).forEach(prop -> {
        JsonObject property = new JsonObject();
        property.addProperty("key", prop.getKey());
        property.addProperty("value", Optional.ofNullable(prop.getValue()).orElse(""));
        propertiesArray.add(property);
      });
    JsonObject jsonObject = new JsonObject();
    jsonObject.add(JSON_FIELD_SCANNER_PROPERTIES, propertiesArray);
    return new Gson().toJson(jsonObject);
  }

  @CheckForNull
  public Boolean getEngineCacheHit() {
    return scannerEngineJar.getCacheHit();
  }

  public JreCacheHit getJreCacheHit() {
    return javaRunner.getJreCacheHit();
  }
}
