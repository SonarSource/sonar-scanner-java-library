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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.scanner.lib.internal.InternalProperties;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.facade.forked.NewScannerEngineFacade;
import org.sonarsource.scanner.lib.internal.facade.forked.ScannerEngineLauncherFactory;
import org.sonarsource.scanner.lib.internal.facade.inprocess.InProcessScannerEngineFacade;
import org.sonarsource.scanner.lib.internal.facade.inprocess.IsolatedLauncherFactory;
import org.sonarsource.scanner.lib.internal.facade.simulation.SimulationScannerEngineFacade;
import org.sonarsource.scanner.lib.internal.http.HttpConfig;
import org.sonarsource.scanner.lib.internal.http.ScannerHttpClient;
import org.sonarsource.scanner.lib.internal.http.ssl.CertificateStore;
import org.sonarsource.scanner.lib.internal.util.ArchResolver;
import org.sonarsource.scanner.lib.internal.util.OsResolver;
import org.sonarsource.scanner.lib.internal.util.Paths2;
import org.sonarsource.scanner.lib.internal.util.System2;
import org.sonarsource.scanner.lib.internal.util.VersionUtils;

import static java.util.Optional.ofNullable;
import static org.sonarsource.scanner.lib.ScannerProperties.SCANNER_ARCH;
import static org.sonarsource.scanner.lib.ScannerProperties.SCANNER_OS;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_KEYSTORE_PASSWORD;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_KEYSTORE_PATH;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_TRUSTSTORE_PASSWORD;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_TRUSTSTORE_PATH;

/**
 * Entry point to run a Sonar analysis programmatically.
 */
public class ScannerEngineBootstrapper {

  private static final Logger LOG = LoggerFactory.getLogger(ScannerEngineBootstrapper.class);

  private static final String SONARCLOUD_HOST = "https://sonarcloud.io";
  private static final String SONARCLOUD_REST_API = "https://api.sonarcloud.io";
  static final String SQ_VERSION_NEW_BOOTSTRAPPING = "10.6";
  private static final String JAVAX_NET_SSL_TRUST_STORE = "javax.net.ssl.trustStore";
  private static final String JAVAX_NET_SSL_TRUST_STORE_PASSWORD = "javax.net.ssl.trustStorePassword";
  private static final String JAVAX_NET_SSL_KEY_STORE = "javax.net.ssl.keyStore";
  private static final String JAVAX_NET_SSL_KEY_STORE_PASSWORD = "javax.net.ssl.keyStorePassword";

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
    adaptJvmSslPropertiesToScannerProperties(bootstrapProperties, system);
    var immutableProperties = Map.copyOf(bootstrapProperties);
    var isSonarCloud = isSonarCloud(immutableProperties);
    var isSimulation = immutableProperties.containsKey(InternalProperties.SCANNER_DUMP_TO_FILE);
    var sonarUserHome = resolveSonarUserHome(immutableProperties);
    var fileCache = FileCache.create(sonarUserHome);
    var httpConfig = new HttpConfig(immutableProperties, sonarUserHome);
    scannerHttpClient.init(httpConfig);
    String serverVersion = null;
    if (!isSonarCloud) {
      serverVersion = getServerVersion(scannerHttpClient, isSimulation, immutableProperties);
    }

    if (isSimulation) {
      return new SimulationScannerEngineFacade(immutableProperties, isSonarCloud, serverVersion);
    } else if (isSonarCloud || VersionUtils.isAtLeastIgnoringQualifier(serverVersion, SQ_VERSION_NEW_BOOTSTRAPPING)) {
      var launcher = scannerEngineLauncherFactory.createLauncher(scannerHttpClient, fileCache, immutableProperties);
      return new NewScannerEngineFacade(immutableProperties, launcher, isSonarCloud, serverVersion);
    } else {
      var launcher = launcherFactory.createLauncher(scannerHttpClient, fileCache);
      var adaptedProperties = adaptDeprecatedPropertiesForInProcessBootstrapping(immutableProperties, httpConfig);
      return new InProcessScannerEngineFacade(adaptedProperties, launcher, false, serverVersion);
    }
  }


  /**
   * Older SonarQube versions used to rely on some different properties, or even {@link System} properties.
   * For backward compatibility, we adapt the new properties to the old format.
   */
  Map<String, String> adaptDeprecatedPropertiesForInProcessBootstrapping(Map<String, String> properties, HttpConfig httpConfig) {
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
      setSystemPropertyIfNotAlreadySet(JAVAX_NET_SSL_KEY_STORE, keyStore.getPath().toString());
      setSystemPropertyIfNotAlreadySet(JAVAX_NET_SSL_KEY_STORE_PASSWORD, keyStore.getKeyStorePassword().orElse(CertificateStore.DEFAULT_PASSWORD));
    }
    var trustStore = httpConfig.getSslConfig().getTrustStore();
    if (trustStore != null) {
      setSystemPropertyIfNotAlreadySet(JAVAX_NET_SSL_TRUST_STORE, trustStore.getPath().toString());
      setSystemPropertyIfNotAlreadySet(JAVAX_NET_SSL_TRUST_STORE_PASSWORD, trustStore.getKeyStorePassword().orElse(CertificateStore.DEFAULT_PASSWORD));
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

  /**
   * New versions of SonarQube/SonarCloud will run on a separate VM. For people who used to rely on configuring SSL
   * by inserting the trusted certificate in the Scanner JVM truststore, or passing JVM SSL properties
   * we need to adapt the properties, at least temporarily, until we have helped most users to migrate.
   */
  static void adaptJvmSslPropertiesToScannerProperties(Map<String, String> bootstrapProperties, System2 system) {
    if (!bootstrapProperties.containsKey(SONAR_SCANNER_TRUSTSTORE_PATH)) {
      var jvmTrustStoreProp = system.getProperty(JAVAX_NET_SSL_TRUST_STORE);
      if (StringUtils.isBlank(jvmTrustStoreProp)) {
        var defaultJvmTrustStoreLocation = Paths.get(System.getProperty("java.home"), "lib", "security", "cacerts");
        if (Files.isRegularFile(defaultJvmTrustStoreLocation)) {
          LOG.debug("Mapping default scanner JVM truststore location '{}' to new properties", defaultJvmTrustStoreLocation);
          bootstrapProperties.put(SONAR_SCANNER_TRUSTSTORE_PATH, defaultJvmTrustStoreLocation.toString());
          bootstrapProperties.putIfAbsent(SONAR_SCANNER_TRUSTSTORE_PASSWORD, System.getProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD, "changeit"));
        }
      } else {
        bootstrapProperties.putIfAbsent(SONAR_SCANNER_TRUSTSTORE_PATH, jvmTrustStoreProp);
        ofNullable(system.getProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD))
          .ifPresent(password -> bootstrapProperties.putIfAbsent(SONAR_SCANNER_TRUSTSTORE_PASSWORD, password));
      }
    }
    if (!bootstrapProperties.containsKey(SONAR_SCANNER_KEYSTORE_PATH)) {
      var keystoreProp = system.getProperty(JAVAX_NET_SSL_KEY_STORE);
      if (!StringUtils.isBlank(keystoreProp)) {
        bootstrapProperties.put(SONAR_SCANNER_KEYSTORE_PATH, keystoreProp);
        ofNullable(system.getProperty(JAVAX_NET_SSL_KEY_STORE_PASSWORD))
          .ifPresent(password -> bootstrapProperties.putIfAbsent(SONAR_SCANNER_KEYSTORE_PASSWORD, password));
      }
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
