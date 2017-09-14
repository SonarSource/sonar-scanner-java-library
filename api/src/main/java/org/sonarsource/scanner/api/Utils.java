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
package org.sonarsource.scanner.api;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonObject.Member;
import com.eclipsesource.json.JsonValue;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

public class Utils {
  private static final String SONARQUBE_SCANNER_PARAMS = "SONARQUBE_SCANNER_PARAMS";

  private Utils() {
    // only util static methods
  }

  public static Properties loadEnvironmentProperties(Map<String, String> env) {
    String scannerParams = env.get(SONARQUBE_SCANNER_PARAMS);
    Properties props = new Properties();

    if (scannerParams != null) {
      try {

        JsonValue jsonValue = Json.parse(scannerParams);
        JsonObject jsonObject = jsonValue.asObject();
        Iterator<Member> it = jsonObject.iterator();

        while (it.hasNext()) {
          Member member = it.next();
          String key = member.getName();
          String value = member.getValue().asString();
          props.put(key, value);
        }
      } catch (Exception e) {
        throw new IllegalStateException("Failed to parse JSON in SONARQUBE_SCANNER_PARAMS environment variable", e);
      }
    }
    return props;
  }

  /**
   * Similar to org.apache.commons.lang.StringUtils#join()
   */
  static String join(String[] array, String delimiter) {
    StringBuilder sb = new StringBuilder();
    Iterator<String> it = Arrays.asList(array).iterator();
    while (it.hasNext()) {
      sb.append(it.next());
      if (!it.hasNext()) {
        break;
      }
      sb.append(delimiter);
    }
    return sb.toString();
  }

  static boolean taskRequiresProject(Map<String, String> props) {
    Object task = props.get(ScannerProperties.TASK);
    return task == null || ScanProperties.SCAN_TASK.equals(task);
  }

  static void writeProperties(File outputFile, Properties p) {
    try (OutputStream output = new FileOutputStream(outputFile)) {
      p.store(output, "Generated by sonar-runner");
    } catch (Exception e) {
      throw new IllegalStateException("Fail to export sonar-runner properties", e);
    }
  }

  public static void deleteQuietly(Path f) {
    try {
      Files.walkFileTree(f, new DeleteQuietlyFileVisitor());
    } catch (IOException e) {
      // ignore
    }
  }

  private static class DeleteQuietlyFileVisitor extends SimpleFileVisitor<Path> {
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
      return deleteAndContinue(file);
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
      return deleteAndContinue(file);
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
      return deleteAndContinue(dir);
    }

    private static FileVisitResult deleteAndContinue(Path path) {
      try {
        Files.delete(path);
      } catch (IOException e) {
        // ignore
      }
      return FileVisitResult.CONTINUE;
    }
  }
}
