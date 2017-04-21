/*
 * SonarQube Scanner API - ITs
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
package com.sonar.scanner.api.it.tools;

import com.sonar.orchestrator.build.BuildResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class SimpleScanner {
  private static final Path JAR_PATH = Paths.get("..", "it-simple-scanner", "target", "simple-scanner.jar")
    .toAbsolutePath()
    .normalize();
  private final Path javaBin;
  private final CommandExecutor exec;

  public SimpleScanner() {
    if (!Files.exists(JAR_PATH)) {
      throw new IllegalStateException("Could not find simple-scanner artifact in " + JAR_PATH.toString());
    }
    javaBin = Paths.get(System.getProperty("java.home"), "bin", "java");
    if (!Files.exists(javaBin)) {
      throw new IllegalStateException("Could not find java in " + javaBin.toString());
    }

    exec = new CommandExecutor(javaBin);
  }

  public BuildResult executeSimpleProject(Path baseDir, String host) throws IOException {
    return executeSimpleProject(baseDir, host, Collections.emptyMap());
  }

  public BuildResult executeSimpleProject(Path baseDir, String host, Map<String, String> extraProps) throws IOException {
    List<String> params = new ArrayList<>();
    Map<String, String> props = getSimpleProjectProperties(baseDir, host, extraProps);

    props.forEach((k, v) -> params.add("-D" + k + "=" + v));
    params.add("-Djavax.net.debug=all");
    params.add("-jar");
    params.add(JAR_PATH.toString());

    int status = exec.execute(params.toArray(new String[params.size()]));
    BuildResult result = new BuildResult();

    result.addStatus(status);
    result.getLogsWriter().append(exec.getLogs());
    return result;
  }

  private Map<String, String> getSimpleProjectProperties(Path baseDir, String host, Map<String, String> extraProps) throws IOException {
    Properties analysisProperties = new Properties();
    Path propertiesFile = baseDir.resolve("sonar-project.properties");
    analysisProperties.load(Files.newInputStream(propertiesFile));
    analysisProperties.setProperty("sonar.projectBaseDir", baseDir.toAbsolutePath().toString());
    analysisProperties.setProperty("sonar.host.url", host);
    analysisProperties.putAll(extraProps);
    return (Map) analysisProperties;
  }
}
