/*
 * SonarQube Scanner API - ITs
 * Copyright (C) 2011-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package com.sonar.scanner.api.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.junit.ClassRule;
import org.junit.Test;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.scanner.api.it.tools.SimpleScanner;

public class PropertiesTest {
  @ClassRule
  public static final Orchestrator ORCHESTRATOR = ScannerApiTestSuite.ORCHESTRATOR;

  @Test
  public void testRuntimeEnvironment() throws IOException {
    SimpleScanner scanner = new SimpleScanner();
    Map<String, String> params = new HashMap<>();
    BuildResult buildResult = scanner.executeSimpleProject(project("java-sample"), ORCHESTRATOR.getServer().getUrl(), params);
    assertThat(buildResult.getLastStatus()).isEqualTo(0);

    Path accessLogs = ORCHESTRATOR.getServer().getAppLogs().toPath().resolveSibling("access.log");
    String accessLogsContent = new String(Files.readAllBytes(accessLogs));
    assertThat(accessLogsContent).doesNotContain("\"null/null\"");
    assertThat(accessLogsContent).contains("\"SonarQubeScanner/");
  }

  @Test
  public void testResolutionProperties() throws IOException {
    SimpleScanner scanner = new SimpleScanner();
    Map<String, String> params = new HashMap<>();
    params.put("sonar.login", "admin");
    params.put("sonar.password", "${sonar.login}");
    BuildResult buildResult = scanner.executeSimpleProject(project("java-sample"), ORCHESTRATOR.getServer().getUrl(), params);
    assertThat(buildResult.getLastStatus()).isEqualTo(0);
  }

  private static Path project(String projectName) {
    return Paths.get("..", "projects", projectName);
  }

}
