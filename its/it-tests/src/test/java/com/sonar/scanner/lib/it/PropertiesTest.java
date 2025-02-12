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
import com.sonar.orchestrator.junit4.OrchestratorRule;
import com.sonar.scanner.lib.it.tools.SimpleScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertiesTest {
  @ClassRule
  public static final OrchestratorRule ORCHESTRATOR = ScannerJavaLibraryTestSuite.ORCHESTRATOR;

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void testRuntimeEnvironmentPassedAsUserAgent() throws IOException {
    SimpleScanner scanner = new SimpleScanner();
    BuildResult buildResult = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl(), Map.of(), Map.of());
    assertThat(buildResult.getLastStatus()).isZero();
    assertThat(buildResult.getLogs()).contains("2 files indexed");

    Path accessLogs = ORCHESTRATOR.getServer().getAppLogs().toPath().resolveSibling("access.log");
    String accessLogsContent = new String(Files.readAllBytes(accessLogs));
    assertThat(accessLogsContent).contains("\"Simple Scanner/1.0\"");
  }

  @Test
  public void passConfigurationUsingEnvVariables() throws IOException {
    SimpleScanner scanner = new SimpleScanner();
    BuildResult buildResult = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl(), Map.of(),
      Map.of("SONAR_SCANNER_JSON_PARAMS", "{\"sonar.exclusions\": \"**/Hello.js\"}"));
    assertThat(buildResult.getLastStatus()).isZero();

    assertThat(buildResult.getLogs()).contains("1 file indexed");
  }

  @Test
  public void cacheIsInUserHomeByDefault() throws IOException {
    SimpleScanner scanner = new SimpleScanner();
    BuildResult buildResult = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl(), Map.of(), Map.of());
    assertThat(buildResult.getLastStatus()).isZero();

    assertThat(Paths.get(System.getProperty("user.home")).resolve(".sonar/cache")).isDirectoryRecursivelyContaining(("glob:**/*scanner-*.jar"));
  }

  @Test
  public void overrideHomeDirectoryWithEnv() throws IOException {
    var userHome = temp.newFolder();
    SimpleScanner scanner = new SimpleScanner();
    BuildResult buildResult = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl(), Map.of(),
      Map.of("SONAR_USER_HOME", userHome.getAbsolutePath()));
    assertThat(buildResult.getLastStatus()).isZero();

    assertThat(userHome.toPath().resolve("cache")).isDirectoryRecursivelyContaining(("glob:**/*scanner-*.jar"));
  }

  @Test
  public void overrideHomeDirectoryWithProps() throws IOException {
    var userHome = temp.newFolder();
    SimpleScanner scanner = new SimpleScanner();
    BuildResult buildResult = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl(), Map.of("sonar.userHome", userHome.getAbsolutePath()), Map.of());
    assertThat(buildResult.getLastStatus()).isZero();

    assertThat(userHome.toPath().resolve("cache")).isDirectoryRecursivelyContaining(("glob:**/*scanner-*.jar"));
  }

  private static Path project(String projectName) {
    return Paths.get("..", "projects", projectName);
  }

}
