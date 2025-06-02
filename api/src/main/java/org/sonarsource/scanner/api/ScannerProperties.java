/*
 * SonarQube Scanner API
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
  
}
