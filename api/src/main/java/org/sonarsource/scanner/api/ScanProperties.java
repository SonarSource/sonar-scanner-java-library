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
 * Most commonly used properties for a SonarQube analysis. These properties are passed to EmbeddedScanner#runAnalysis(java.util.Properties).
 * See <a href="http://docs.sonarqube.org/display/SONAR/Analysis+Parameters">documentation</a> for more properties.
 *
 * @since 2.2
 */
public interface ScanProperties {

  /**
   * Default task
   *
   * @see ScannerProperties#TASK
   */
  String SCAN_TASK = "scan";

  /**
   * Required project key
   */
  String PROJECT_KEY = "sonar.projectKey";

  /**
   * Used to define the exact key of each module. 
   * If {@link #PROJECT_KEY} is used instead on a module, then final key of the module will be &lt;parent module key&gt;:&lt;PROJECT_KEY&gt;.
   * @since SonarQube 4.1
   */
  String MODULE_KEY = "sonar.moduleKey";

  String PROJECT_NAME = "sonar.projectName";

  String PROJECT_VERSION = "sonar.projectVersion";

  /**
   * Optional description
   */
  String PROJECT_DESCRIPTION = "sonar.projectDescription";

  /**
   * Required paths to source directories, separated by commas, for example: "srcDir1,srcDir2"
   */
  String PROJECT_SOURCE_DIRS = "sonar.sources";

  /**
   * Optional paths to test directories, separated by commas, for example: "testDir1,testDir2"
   */
  String PROJECT_TEST_DIRS = "sonar.tests";

  /**
   * Property used to specify the base directory of the project to analyse. Default is ".".
   */
  String PROJECT_BASEDIR = "sonar.projectBaseDir";

  /**
   * Encoding of source and test files. By default it's the platform encoding.
   */
  String PROJECT_SOURCE_ENCODING = "sonar.sourceEncoding";

  /**
   * Skip analysis.
   */
  String SKIP = "sonar.scanner.skip";

}
