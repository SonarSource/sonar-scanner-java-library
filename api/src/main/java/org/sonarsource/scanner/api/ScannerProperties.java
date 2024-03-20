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
package org.sonarsource.scanner.api;

/**
 * Mostly used properties that can be passed to EmbeddedScanner#addGlobalProperties(java.util.Properties).
 * See <a href="https://docs.sonarqube.org/latest/analysis/analysis-parameters/">documentation</a> for more properties.
 *
 * @since 2.2
 */
public interface ScannerProperties {
  /**
   * HTTP URL of Sonar server, "http://localhost:9000" by default
   */
  String HOST_URL = "sonar.host.url";

  /**
   * Task to execute, "scan" by default
   */
  String TASK = "sonar.task";

  /**
   * Working directory containing generated reports and temporary data.
   */
  String WORK_DIR = "sonar.working.directory";

  /**
   * Home directory to be used by the scanner.
   */
  String USER_HOME = "sonar.userHome";

  /**
   * Path of the java executable to be used by the scanner-engine.
   */
  String SCANNER_JAVA_EXECUTABLE = "sonar.scanner.javaExecutable";

  /**
   * Name of the operating system to be used for JRE auto-provisioning.
   * See {@link OperatingSystem} for possible values.
   */
  String SCANNER_OS = "sonar.scanner.os";

  /**
   * Name of the architecture to be used for JRE auto-provisioning.
   */
  String SCANNER_ARCH = "sonar.scanner.arch";
}
