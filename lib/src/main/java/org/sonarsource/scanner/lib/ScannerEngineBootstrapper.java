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

import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sonarsource.scanner.lib.internal.ClassloadRules;
import org.sonarsource.scanner.lib.internal.InternalProperties;
import org.sonarsource.scanner.lib.internal.IsolatedLauncherFactory;

/**
 * Entry point to run a Sonar analysis programmatically.
 */
public class ScannerEngineBootstrapper {

  private static final String SONARCLOUD_HOST = "https://sonarcloud.io";
  private final LogOutput logOutput;
  private final Map<String, String> bootstrapProperties = new HashMap<>();

  public ScannerEngineBootstrapper(String app, String version, final LogOutput logOutput) {
    this.logOutput = logOutput;
    this.setBootstrapProperty(InternalProperties.SCANNER_APP, app)
      .setBootstrapProperty(InternalProperties.SCANNER_APP_VERSION, version);
  }

  /**
   * Declare technical properties needed to bootstrap (sonar.host.url, credentials, proxy, ...).
   */
  public ScannerEngineBootstrapper addBootstrapProperties(Map<String, String> p) {
    bootstrapProperties.putAll(p);
    return this;
  }

  /**
   * Declare a technical property needed to bootstrap (sonar.host.url, credentials, proxy, ...).
   */
  public ScannerEngineBootstrapper setBootstrapProperty(String key, String value) {
    bootstrapProperties.put(key, value);
    return this;
  }

  /**
   * Bootstrap the scanner-engine.
   */
  public ScannerEngineFacade bootstrap() {
    initBootstrapDefaultValues();
    Set<String> unmaskRules = new HashSet<>();
    unmaskRules.add("org.sonarsource.scanner.lib.internal.batch.");
    ClassloadRules rules = new ClassloadRules(Collections.emptySet(), unmaskRules);
    var properties = Map.copyOf(bootstrapProperties);
    var isSonarCloud = getSonarCloudUrl().equals(properties.get(ScannerProperties.HOST_URL));
    String sonarUserHome;
    if (properties.containsKey(ScannerProperties.SONAR_USER_HOME)) {
      sonarUserHome = properties.get(ScannerProperties.SONAR_USER_HOME);
    } else {
      var userHome = Objects.requireNonNull(System.getProperty("user.home"), "The system property 'user.home' is expected to be non null");
      sonarUserHome = Paths.get(userHome, ".sonar").toAbsolutePath().toString();
    }
    var launcherFactory = new IsolatedLauncherFactory(new LoggerAdapter(logOutput), Paths.get(sonarUserHome));
    var launcher = launcherFactory.createLauncher(properties, rules);
    return new ScannerEngineFacade(properties, launcher, logOutput, isSonarCloud);
  }

  private void initBootstrapDefaultValues() {
    setBootstrapPropertyIfNotAlreadySet(ScannerProperties.HOST_URL, getSonarCloudUrl());
  }

  private String getSonarCloudUrl() {
    return bootstrapProperties.getOrDefault("sonar.scanner.sonarcloudUrl", SONARCLOUD_HOST);
  }

  private void setBootstrapPropertyIfNotAlreadySet(String key, String value) {
    if (!bootstrapProperties.containsKey(key)) {
      setBootstrapProperty(key, value);
    }
  }

}
