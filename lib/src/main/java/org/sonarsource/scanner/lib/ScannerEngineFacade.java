/*
 * SonarScanner Java Library
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
package org.sonarsource.scanner.lib;

import java.util.Map;

public interface ScannerEngineFacade extends AutoCloseable {

  /**
   * Get the properties that will be passed to the bootstrapped scanner engine.
   */
  Map<String, String> getBootstrapProperties();

  /**
   * Get the version of the SonarQube Server that the scanner is connected to. Don't call this method if the scanner is connected to SonarQube Cloud.
   *
   * @return the version of the SonarQube Server
   * @throws UnsupportedOperationException if the scanner is connected to SonarQube Cloud
   */
  String getServerVersion();

  /**
   * @return true if the scanner is connected to SonarQube Cloud, false otherwise
   */
  boolean isSonarCloud();


  /**
   * Run the analysis. In case of failure, a log message should have been emitted.
   *
   * @return true if the analysis succeeded, false otherwise.
   */
  boolean analyze(Map<String, String> analysisProps);

  /**
   * Get the label of the server that the scanner is connected to. Distinguishes SonarQube Cloud, SonarQube Server and SonarQube Community Build.
   *
   * @return whether scanner is connected to SonarQube Cloud, Server or Community Build.
   */
  String getServerLabel();
}
