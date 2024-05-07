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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonarsource.scanner.lib.internal.ClassloadRules;
import org.sonarsource.scanner.lib.internal.InternalProperties;
import org.sonarsource.scanner.lib.internal.IsolatedLauncherFactory;
import org.sonarsource.scanner.lib.internal.OsResolver;
import org.sonarsource.scanner.lib.internal.Paths2;
import org.sonarsource.scanner.lib.internal.ScannerEngineLauncherFactory;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.http.ServerConnection;
import org.sonarsource.scanner.lib.internal.util.VersionUtils;

import static org.sonarsource.scanner.lib.ScannerProperties.SCANNER_ARCH;
import static org.sonarsource.scanner.lib.ScannerProperties.SCANNER_OS;

/**
 * Entry point to run a Sonar analysis programmatically.
 */
public class ScannerEngineBootstrapper {

  private static final String SONARCLOUD_HOST = "https://sonarcloud.io";
  private static final String SONARCLOUD_REST_API = "https://api.sonarcloud.io";
  static final String SQ_VERSION_NEW_BOOTSTRAPPING = "10.6";

  private final IsolatedLauncherFactory launcherFactory;
  private final ScannerEngineLauncherFactory scannerEngineLauncherFactory;
  private final Map<String, String> bootstrapProperties = new HashMap<>();
  private final ServerConnection serverConnection;
  private final System2 system;

  ScannerEngineBootstrapper(String app, String version, System2 system,
    ServerConnection serverConnection, IsolatedLauncherFactory launcherFactory,
    ScannerEngineLauncherFactory scannerEngineLauncherFactory) {
    this.system = system;
    this.serverConnection = serverConnection;
    this.launcherFactory = launcherFactory;
    this.scannerEngineLauncherFactory = scannerEngineLauncherFactory;
    this.setBootstrapProperty(InternalProperties.SCANNER_APP, app)
      .setBootstrapProperty(InternalProperties.SCANNER_APP_VERSION, version);
  }

  public static ScannerEngineBootstrapper create(String app, String version) {
    System2 system = new System2();
    return new ScannerEngineBootstrapper(app, version, system, new ServerConnection(),
      new IsolatedLauncherFactory(), new ScannerEngineLauncherFactory(system));
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
    var isSonarCloud = isSonarCloud(properties);
    var isSimulation = properties.containsKey(InternalProperties.SCANNER_DUMP_TO_FILE);
    var sonarUserHome = resolveSonarUserHome(properties);
    var fileCache = FileCache.create(sonarUserHome);
    serverConnection.init(properties, sonarUserHome);
    String serverVersion = null;
    if (!isSonarCloud) {
      serverVersion = getServerVersion(serverConnection, isSimulation, properties);
    }

    if (isSimulation) {
      return new SimulationScannerEngineFacade(properties, isSonarCloud, serverVersion);
    } else if (isSonarCloud || VersionUtils.isAtLeastIgnoringQualifier(serverVersion, SQ_VERSION_NEW_BOOTSTRAPPING)) {
      var launcher = scannerEngineLauncherFactory.createLauncher(serverConnection, fileCache, properties);
      return new NewScannerEngineFacade(properties, launcher, isSonarCloud, serverVersion);
    } else {
      var launcher = launcherFactory.createLauncher(rules, serverConnection, fileCache);
      return new InProcessScannerEngineFacade(properties, launcher, false, serverVersion);
    }
  }

  private static Path resolveSonarUserHome(Map<String, String> properties) {
    String sonarUserHome;
    if (properties.containsKey(ScannerProperties.SONAR_USER_HOME)) {
      sonarUserHome = properties.get(ScannerProperties.SONAR_USER_HOME);
    } else {
      var userHome = Objects.requireNonNull(System.getProperty("user.home"), "The system property 'user.home' is expected to be non null");
      sonarUserHome = Paths.get(userHome, ".sonar").toAbsolutePath().toString();
    }
    return Paths.get(sonarUserHome);
  }

  private static String getServerVersion(ServerConnection serverConnection, boolean isSimulation, Map<String, String> properties) {
    if (isSimulation) {
      return properties.getOrDefault(InternalProperties.SCANNER_VERSION_SIMULATION, "5.6");
    }

    try {
      return serverConnection.callRestApi("/analysis/version");
    } catch (Exception e) {
      try {
        return serverConnection.callWebApi("/api/server/version");
      } catch (Exception e2) {
        throw new IllegalStateException("Failed to get server version", e);
      }
    }
  }

  private void initBootstrapDefaultValues() {
    setBootstrapPropertyIfNotAlreadySet(ScannerProperties.HOST_URL, getSonarCloudUrl());
    setBootstrapPropertyIfNotAlreadySet(ScannerProperties.API_BASE_URL,
      isSonarCloud(bootstrapProperties) ? SONARCLOUD_REST_API : (bootstrapProperties.get(ScannerProperties.HOST_URL) + "/api/v2"));
    if (!bootstrapProperties.containsKey(SCANNER_OS)) {
      setBootstrapProperty(SCANNER_OS, new OsResolver(system, new Paths2()).getOs().name().toLowerCase(Locale.ENGLISH));
    }
    setBootstrapPropertyIfNotAlreadySet(SCANNER_ARCH, system.getProperty("os.arch"));
  }

  private String getSonarCloudUrl() {
    return bootstrapProperties.getOrDefault(ScannerProperties.SONARCLOUD_URL, SONARCLOUD_HOST);
  }

  private boolean isSonarCloud(Map<String, String> properties) {
    return getSonarCloudUrl().equals(properties.get(ScannerProperties.HOST_URL));
  }

  private void setBootstrapPropertyIfNotAlreadySet(String key, @Nullable String value) {
    if (!bootstrapProperties.containsKey(key) && value != null) {
      setBootstrapProperty(key, value);
    }
  }

}
