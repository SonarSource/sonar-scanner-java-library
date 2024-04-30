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

/**
 * Mostly used properties that can be passed to EmbeddedScanner#addGlobalProperties(java.util.Properties).
 * See <a href="https://docs.sonarqube.org/latest/analysis/analysis-parameters/">documentation</a> for more properties.
 *
 * @since 2.2
 */
public interface ScannerProperties {
  /**
   * URL of the Sonar server, default to SonarCloud
   */
  String HOST_URL = "sonar.host.url";

  /**
   * Working directory containing generated reports and temporary data.
   */
  String WORK_DIR = "sonar.working.directory";

  /**
   * Base dir for various locations (cache, SSL, …). Default to ~/.sonar
   */
  String SONAR_USER_HOME = "sonar.userHome";

  /**
   * Authentication token for connecting to the Sonar server.
   */
  String SONAR_TOKEN = "sonar.token";

}
