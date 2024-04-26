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
 */
public final class ScannerProperties {

  private ScannerProperties() {
    // only constants
  }

  /**
   * URL of the Sonar server, default to SonarCloud
   */
  public static final String HOST_URL = "sonar.host.url";

  /**
   * Working directory containing generated reports and temporary data.
   */
  public static final String WORK_DIR = "sonar.working.directory";

  /**
   * Base dir for various locations (cache, SSL, …). Default to ~/.sonar
   */
  public static final String SONAR_USER_HOME = "sonar.userHome";

  /**
   * Authentication token for connecting to the Sonar server.
   */
  public static final String SONAR_TOKEN = "sonar.token";

  /**
   * Authentication username for connecting to the Sonar server.
   */
  public static final String SONAR_LOGIN = "sonar.login";

  /**
   * Authentication password for connecting to the Sonar server.
   */
  public static final String SONAR_PASSWORD = "sonar.password";

  /**
   * HTTP client properties
   */
  public static final String SONAR_SCANNER_PROXY_PORT = "sonar.scanner.proxyPort";
  public static final String SONAR_SCANNER_CONNECT_TIMEOUT = "sonar.scanner.connectTimeout";
  public static final String SONAR_SCANNER_SOCKET_TIMEOUT = "sonar.scanner.socketTimeout";
  public static final String SONAR_SCANNER_RESPONSE_TIMEOUT = "sonar.scanner.responseTimeout";
  public static final String SONAR_SCANNER_PROXY_HOST = "sonar.scanner.proxyHost";
  public static final String SONAR_SCANNER_PROXY_USER = "sonar.scanner.proxyUser";
  public static final String SONAR_SCANNER_PROXY_PASSWORD = "sonar.scanner.proxyPassword";
  public static final String SONAR_SCANNER_KEYSTORE_PATH = "sonar.scanner.keystorePath";
  public static final String SONAR_SCANNER_KEYSTORE_PASSWORD = "sonar.scanner.keystorePassword";
  public static final String SONAR_SCANNER_TRUSTSTORE_PATH = "sonar.scanner.truststorePath";
  public static final String SONAR_SCANNER_TRUSTSTORE_PASSWORD = "sonar.scanner.truststorePassword";

  /**
   * Skip analysis.
   */
  public static final String SKIP = "sonar.scanner.skip";

  /**
   * Path of the java executable to be used by the scanner-engine.
   */
  public static final String JAVA_EXECUTABLE_PATH = "sonar.scanner.javaExePath";

  /**
   * Flag to skip the JRE provisioning.
   */
  public static final String SKIP_JRE_PROVISIONING = "sonar.scanner.skipJreProvisioning";

  /**
   * Name of the operating system to be used for JRE provisioning.
   * See {@link org.sonarsource.scanner.lib.internal.OsResolver.OperatingSystem} for possible values.
   */
  public static final String SCANNER_OS = "sonar.scanner.os";

  /**
   * Name of the architecture to be used for JRE provisioning.
   */
  public static final String SCANNER_ARCH = "sonar.scanner.arch";

  /**
   * Java options to be used by the scanner-engine.
   */
  public static final String SCANNER_JAVA_OPTS = "sonar.scanner.javaOpts";

  /**
   * Set to true if the JRE was found in the scanner cache.
   */
  public static final String JRE_CACHE_HIT = "sonar.scanner.wasJreCacheHit";

  /**
   * Set to true if the Scanner Engine was found in the scanner cache.
   */
  public static final String SCANNER_ENGINE_CACHE_HIT = "sonar.scanner.wasEngineCacheHit";
}
