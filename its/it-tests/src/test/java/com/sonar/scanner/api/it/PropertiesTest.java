/*
 * SonarQube Scanner API - ITs
 * Copyright (C) 2011-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package com.sonar.scanner.api.it;

import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.junit4.OrchestratorRule;
import com.sonar.scanner.api.it.tools.SimpleScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertiesTest {
  @ClassRule
  public static final OrchestratorRule ORCHESTRATOR = ScannerApiTestSuite.ORCHESTRATOR;

  @Test
  public void testRuntimeEnvironmentPassedAsUserAgent() throws IOException {
    SimpleScanner scanner = new SimpleScanner();
    Map<String, String> params = new HashMap<>();
    BuildResult buildResult = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl(), params);
    assertThat(buildResult.getLastStatus()).isEqualTo(0);

    Path accessLogs = ORCHESTRATOR.getServer().getAppLogs().toPath().resolveSibling("access.log");
    String accessLogsContent = new String(Files.readAllBytes(accessLogs));
    assertThat(accessLogsContent).contains("\"Simple Scanner/1.0\"");
  }

  private static Path project(String projectName) {
    return Paths.get("..", "projects", projectName);
  }

}
