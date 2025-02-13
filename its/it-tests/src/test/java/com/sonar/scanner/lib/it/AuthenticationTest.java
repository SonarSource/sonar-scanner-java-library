/*
 * SonarScanner Java Library - ITs
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
package com.sonar.scanner.lib.it;

import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.container.Server;
import com.sonar.orchestrator.junit4.OrchestratorRule;
import com.sonar.scanner.lib.it.tools.SimpleScanner;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthenticationTest {

  @ClassRule
  public static final OrchestratorRule ORCHESTRATOR = ScannerJavaLibraryTestSuite.ORCHESTRATOR;

  @Test
  public void useTokenAuthenticationByProperties() throws IOException {
    SimpleScanner scanner = new SimpleScanner();
    BuildResult buildResult = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl(), Map.of(
      ScannerJavaLibraryTestSuite.ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(10, 0) ? "sonar.token" : "sonar.login",
      ScannerJavaLibraryTestSuite.ORCHESTRATOR.getDefaultAdminToken()
    ), Map.of(), false);
    assertThat(buildResult.getLastStatus()).isZero();
  }

  @Test
  public void useTokenAuthenticationByEnvVariable() throws IOException {
    SimpleScanner scanner = new SimpleScanner();
    BuildResult buildResult = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl(), Map.of(),
      Map.of("SONAR_TOKEN", ScannerJavaLibraryTestSuite.ORCHESTRATOR.getDefaultAdminToken()), false);
    assertThat(buildResult.getLastStatus()).isZero();
  }

  @Test
  public void useLoginPasswordAuthentication() throws IOException {
    // Support of login/password authentication has been dropped in SQS 25.1
    Assume.assumeFalse(ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(25, 1));
    SimpleScanner scanner = new SimpleScanner();
    BuildResult buildResult = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl(), Map.of(
      "sonar.login", Server.ADMIN_LOGIN,
      "sonar.password", Server.ADMIN_PASSWORD
    ), Map.of(), false);
    assertThat(buildResult.getLastStatus()).isZero();
  }

  private static Path project(String projectName) {
    return Paths.get("..", "projects", projectName);
  }


}
