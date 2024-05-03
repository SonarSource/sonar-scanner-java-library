/*
 * Simple Scanner for ITs
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
package com.sonar.scanner.lib.it;

import java.util.HashMap;
import java.util.Map;
import org.sonarsource.scanner.lib.EnvironmentConfig;
import org.sonarsource.scanner.lib.ScannerEngineBootstrapper;

public class Main {
  public static void main(String[] args) {
    try {

      Map<String, String> props = new HashMap<>(EnvironmentConfig.load());

      for (String k : System.getProperties().stringPropertyNames()) {
        if (k.startsWith("sonar.")) {
          props.put(k, System.getProperty(k));
        }
      }

      runProject(props);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
    System.exit(0);
  }

  private static void runProject(Map<String, String> props) throws Exception {

    try (var scannerEngine = ScannerEngineBootstrapper.create("Simple Scanner", "1.0")
      .addBootstrapProperties(props)
      .bootstrap()) {
      scannerEngine.analyze(props);
    }
  }
}
