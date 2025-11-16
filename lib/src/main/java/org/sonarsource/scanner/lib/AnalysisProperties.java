/*
 * SonarScanner Java Library
 * Copyright (C) 2011-2025 SonarSource Sàrl
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
 * Most commonly used properties for an analysis.
 * See <a href="https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/analysis-parameters/">documentation</a>
 * for more details.
 */
public final class AnalysisProperties {

  private AnalysisProperties() {
    // only constants
  }

  /**
   * Required project key
   */
  public static final String PROJECT_KEY = "sonar.projectKey";

  /**
   * Used to define the exact key of each module.
   * If {@link #PROJECT_KEY} is used instead on a module, then final key of the module will be &lt;parent module key&gt;:&lt;PROJECT_KEY&gt;.
   * @since SonarQube 4.1
   */
  public static final String MODULE_KEY = "sonar.moduleKey";

  public static final String PROJECT_NAME = "sonar.projectName";

  public static final String PROJECT_VERSION = "sonar.projectVersion";

  /**
   * Optional description
   */
  public static final String PROJECT_DESCRIPTION = "sonar.projectDescription";

  /**
   * Required paths to source directories, separated by commas, for example: "srcDir1,srcDir2"
   */
  public static final String PROJECT_SOURCE_DIRS = "sonar.sources";

  /**
   * Optional paths to test directories, separated by commas, for example: "testDir1,testDir2"
   */
  public static final String PROJECT_TEST_DIRS = "sonar.tests";

  /**
   * Property used to specify the base directory of the project to analyse. Default is ".".
   */
  public static final String PROJECT_BASEDIR = "sonar.projectBaseDir";

  /**
   * Encoding of source and test files. By default, it's the platform encoding.
   */
  public static final String PROJECT_SOURCE_ENCODING = "sonar.sourceEncoding";
}
