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

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.scanner.lib.internal.ArchResolver;
import org.sonarsource.scanner.lib.internal.InternalProperties;
import org.sonarsource.scanner.lib.internal.IsolatedLauncherFactory;
import org.sonarsource.scanner.lib.internal.OsResolver;
import org.sonarsource.scanner.lib.internal.Paths2;
import org.sonarsource.scanner.lib.internal.ScannerEngineLauncherFactory;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.http.HttpConfig;
import org.sonarsource.scanner.lib.internal.http.ScannerHttpClient;
import org.sonarsource.scanner.lib.internal.util.VersionUtils;

import static org.sonarsource.scanner.lib.ScannerProperties.SCANNER_ARCH;
import static org.sonarsource.scanner.lib.ScannerProperties.SCANNER_OS;

/**
 * Entry point to run a Sonar analysis programmatically.
 */
public class ScannerEngineBootstrapper {

  private static final Logger LOG = LoggerFactory.getLogger(ScannerEngineBootstrapper.class);

  private static final String SONARCLOUD_HOST = "https://sonarcloud.io";
  private static final String SONARCLOUD_REST_API = "https://api.sonarcloud.io";
  static final String SQ_VERSION_NEW_BOOTSTRAPPING = "10.6";

  private final IsolatedLauncherFactory launcherFactory;
  private final ScannerEngineLauncherFactory scannerEngineLauncherFactory;
  private final Map<String, String> bootstrapProperties = new HashMap<>();
  private final ScannerHttpClient scannerHttpClient;
  private final System2 system;

  ScannerEngineBootstrapper(String app, String version, System2 system,
    ScannerHttpClient scannerHttpClient, IsolatedLauncherFactory launcherFactory,
    ScannerEngineLauncherFactory scannerEngineLauncherFactory) {
    this.system = system;
    this.scannerHttpClient = scannerHttpClient;
    this.launcherFactory = launcherFactory;
    this.scannerEngineLauncherFactory = scannerEngineLauncherFactory;
    this.setBootstrapProperty(InternalProperties.SCANNER_APP, app)
      .setBootstrapProperty(InternalProperties.SCANNER_APP_VERSION, version);
  }

  public static ScannerEngineBootstrapper create(String app, String version) {
    System2 system = new System2();
    return new ScannerEngineBootstrapper(app, version, system, new ScannerHttpClient(),
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
    if (LOG.isDebugEnabled()) {
      LOG.debug("Scanner max available memory: {}", FileUtils.byteCountToDisplaySize(Runtime.getRuntime().maxMemory()));
    }
    initBootstrapDefaultValues();
    var properties = Map.copyOf(bootstrapProperties);
    var isSonarCloud = isSonarCloud(properties);
    var isSimulation = properties.containsKey(InternalProperties.SCANNER_DUMP_TO_FILE);
    var sonarUserHome = resolveSonarUserHome(properties);
    var fileCache = FileCache.create(sonarUserHome);
    var httpConfig = new HttpConfig(bootstrapProperties, sonarUserHome);
    scannerHttpClient.init(httpConfig);
    String serverVersion = null;
    if (!isSonarCloud) {
      serverVersion = getServerVersion(scannerHttpClient, isSimulation, properties);
    }

    if (isSimulation) {
      return new SimulationScannerEngineFacade(properties, isSonarCloud, serverVersion);
    } else if (isSonarCloud || VersionUtils.isAtLeastIgnoringQualifier(serverVersion, SQ_VERSION_NEW_BOOTSTRAPPING)) {
      var launcher = scannerEngineLauncherFactory.createLauncher(scannerHttpClient, fileCache, properties);
      return new NewScannerEngineFacade(properties, launcher, isSonarCloud, serverVersion);
    } else {
      var launcher = launcherFactory.createLauncher(scannerHttpClient, fileCache);
      var adaptedProperties = adaptDeprecatedProperties(properties, httpConfig);
      return new InProcessScannerEngineFacade(adaptedProperties, launcher, false, serverVersion);
    }
  }

  /**
   * Older SonarQube versions used to rely on some different properties, or even {@link System} properties.
   * For backward compatibility, we adapt the new properties to the old format.
   */
  @Nonnull
  Map<String, String> adaptDeprecatedProperties(Map<String, String> properties, HttpConfig httpConfig) {
    var adaptedProperties = new HashMap<>(properties);
    if (!adaptedProperties.containsKey(HttpConfig.READ_TIMEOUT_SEC_PROPERTY)) {
      adaptedProperties.put(HttpConfig.READ_TIMEOUT_SEC_PROPERTY, "" + httpConfig.getSocketTimeout().get(ChronoUnit.SECONDS));
    }
    var proxy = httpConfig.getProxy();
    if (proxy != null) {
      setSystemPropertyIfNotAlreadySet("http.proxyHost", ((InetSocketAddress) proxy.address()).getHostString());
      setSystemPropertyIfNotAlreadySet("https.proxyHost", ((InetSocketAddress) proxy.address()).getHostString());
      setSystemPropertyIfNotAlreadySet("http.proxyPort", "" + ((InetSocketAddress) proxy.address()).getPort());
      setSystemPropertyIfNotAlreadySet("https.proxyPort", "" + ((InetSocketAddress) proxy.address()).getPort());
    }
    setSystemPropertyIfNotAlreadySet("http.proxyUser", httpConfig.getProxyUser());
    setSystemPropertyIfNotAlreadySet("http.proxyPassword", httpConfig.getProxyPassword());

    var keyStore = httpConfig.getSslConfig().getKeyStore();
    if (keyStore != null) {
      setSystemPropertyIfNotAlreadySet("javax.net.ssl.keyStore", keyStore.getPath().toString());
      setSystemPropertyIfNotAlreadySet("javax.net.ssl.keyStorePassword", keyStore.getKeyStorePassword());
    }
    var trustStore = httpConfig.getSslConfig().getTrustStore();
    if (trustStore != null) {
      setSystemPropertyIfNotAlreadySet("javax.net.ssl.trustStore", trustStore.getPath().toString());
      setSystemPropertyIfNotAlreadySet("javax.net.ssl.trustStorePassword", trustStore.getKeyStorePassword());
    }

    return Map.copyOf(adaptedProperties);
  }

  private void setSystemPropertyIfNotAlreadySet(String key, String value) {
    if (system.getProperty(key) == null && StringUtils.isNotBlank(value)) {
      System.setProperty(key, value);
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

  private static String getServerVersion(ScannerHttpClient scannerHttpClient, boolean isSimulation, Map<String, String> properties) {
    if (isSimulation) {
      return properties.getOrDefault(InternalProperties.SCANNER_VERSION_SIMULATION, "5.6");
    }

    try {
      return scannerHttpClient.callRestApi("/analysis/version");
    } catch (Exception e) {
      try {
        return scannerHttpClient.callWebApi("/api/server/version");
      } catch (Exception e2) {
        var ex = new IllegalStateException("Failed to get server version", e2);
        ex.addSuppressed(e);
        throw ex;
      }
    }
  }

  private void initBootstrapDefaultValues() {
    setBootstrapPropertyIfNotAlreadySet(ScannerProperties.HOST_URL, getSonarCloudUrl());
    setBootstrapPropertyIfNotAlreadySet(ScannerProperties.API_BASE_URL,
      isSonarCloud(bootstrapProperties) ? SONARCLOUD_REST_API : (StringUtils.removeEnd(bootstrapProperties.get(ScannerProperties.HOST_URL), "/") + "/api/v2"));
    if (!bootstrapProperties.containsKey(SCANNER_OS)) {
      setBootstrapProperty(SCANNER_OS, new OsResolver(system, new Paths2()).getOs().name().toLowerCase(Locale.ENGLISH));
    }
    if (!bootstrapProperties.containsKey(SCANNER_ARCH)) {
      setBootstrapProperty(SCANNER_ARCH, new ArchResolver().getCpuArch());
    }
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
