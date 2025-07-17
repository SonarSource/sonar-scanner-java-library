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

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.scanner.lib.internal.FailedBootstrap;
import org.sonarsource.scanner.lib.internal.InternalProperties;
import org.sonarsource.scanner.lib.internal.MessageException;
import org.sonarsource.scanner.lib.internal.SuccessfulBootstrap;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.endpoint.ScannerEndpoint;
import org.sonarsource.scanner.lib.internal.endpoint.ScannerEndpointResolver;
import org.sonarsource.scanner.lib.internal.facade.forked.NewScannerEngineFacade;
import org.sonarsource.scanner.lib.internal.facade.forked.ScannerEngineLauncher;
import org.sonarsource.scanner.lib.internal.facade.forked.ScannerEngineLauncherFactory;
import org.sonarsource.scanner.lib.internal.facade.inprocess.InProcessScannerEngineFacade;
import org.sonarsource.scanner.lib.internal.facade.inprocess.IsolatedLauncherFactory;
import org.sonarsource.scanner.lib.internal.facade.simulation.SimulationScannerEngineFacade;
import org.sonarsource.scanner.lib.internal.http.HttpConfig;
import org.sonarsource.scanner.lib.internal.http.HttpException;
import org.sonarsource.scanner.lib.internal.http.ScannerHttpClient;
import org.sonarsource.scanner.lib.internal.http.ssl.CertificateStore;
import org.sonarsource.scanner.lib.internal.util.ArchResolver;
import org.sonarsource.scanner.lib.internal.util.OsResolver;
import org.sonarsource.scanner.lib.internal.util.Paths2;
import org.sonarsource.scanner.lib.internal.util.System2;
import org.sonarsource.scanner.lib.internal.util.VersionUtils;

import static org.sonarsource.scanner.lib.EnvironmentConfig.TOKEN_ENV_VARIABLE;
import static org.sonarsource.scanner.lib.ScannerProperties.SCANNER_ARCH;
import static org.sonarsource.scanner.lib.ScannerProperties.SCANNER_OS;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_LOGIN;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_KEYSTORE_PASSWORD;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_KEYSTORE_PATH;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_TRUSTSTORE_PASSWORD;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_TRUSTSTORE_PATH;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_TOKEN;
import static org.sonarsource.scanner.lib.internal.JvmProperties.HTTPS_PROXY_HOST;
import static org.sonarsource.scanner.lib.internal.JvmProperties.HTTPS_PROXY_PORT;
import static org.sonarsource.scanner.lib.internal.JvmProperties.HTTP_PROXY_HOST;
import static org.sonarsource.scanner.lib.internal.JvmProperties.HTTP_PROXY_PORT;
import static org.sonarsource.scanner.lib.internal.JvmProperties.JAVAX_NET_SSL_KEY_STORE;
import static org.sonarsource.scanner.lib.internal.JvmProperties.JAVAX_NET_SSL_KEY_STORE_PASSWORD;
import static org.sonarsource.scanner.lib.internal.JvmProperties.JAVAX_NET_SSL_TRUST_STORE;
import static org.sonarsource.scanner.lib.internal.JvmProperties.JAVAX_NET_SSL_TRUST_STORE_PASSWORD;

/**
 * Entry point to run a Sonar analysis programmatically.
 */
public class ScannerEngineBootstrapper {

  private static final Logger LOG = LoggerFactory.getLogger(ScannerEngineBootstrapper.class);

  static final String SQ_VERSION_NEW_BOOTSTRAPPING = "10.6";
  static final String SQ_VERSION_TOKEN_AUTHENTICATION = "10.0";

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

  public ScannerEngineBootstrapResult bootstrap() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Scanner max available memory: {}", FileUtils.byteCountToDisplaySize(Runtime.getRuntime().maxMemory()));
    }
    var endpoint = ScannerEndpointResolver.resolveEndpoint(bootstrapProperties);
    initBootstrapDefaultValues(endpoint);
    var immutableProperties = Map.copyOf(bootstrapProperties);
    var sonarUserHome = resolveSonarUserHome(immutableProperties);
    var httpConfig = new HttpConfig(immutableProperties, sonarUserHome, system);
    var isSonarQubeCloud = endpoint.isSonarQubeCloud();
    var isSimulation = immutableProperties.containsKey(InternalProperties.SCANNER_DUMP_TO_FILE);
    var fileCache = FileCache.create(sonarUserHome);

    if (isSimulation) {
      var serverVersion = immutableProperties.getOrDefault(InternalProperties.SCANNER_VERSION_SIMULATION, "9.9");
      return new SuccessfulBootstrap(new SimulationScannerEngineFacade(immutableProperties, isSonarQubeCloud, serverVersion));
    }

    // No HTTP call should be made before this point
    try {
      scannerHttpClient.init(httpConfig);
      if (isSonarQubeCloud) {
        return bootstrapCloud(fileCache, immutableProperties, httpConfig, endpoint);
      }
      return bootstrapServer(fileCache, immutableProperties, httpConfig);
    } catch (MessageException e) {
      return handleException(e);
    }
  }

  private ScannerEngineBootstrapResult bootstrapCloud(FileCache fileCache, Map<String, String> immutableProperties, HttpConfig httpConfig, ScannerEndpoint endpoint) {
    endpoint.getRegionLabel().ifPresentOrElse(
      region -> LOG.info("Communicating with SonarQube Cloud ({} region)", region),
      () -> LOG.info("Communicating with SonarQube Cloud"));
    var scannerFacade = buildNewFacade(fileCache, immutableProperties, httpConfig,
      (launcher, adaptedProperties) -> NewScannerEngineFacade.forSonarQubeCloud(adaptedProperties, launcher));
    return new SuccessfulBootstrap(scannerFacade);
  }

  private ScannerEngineBootstrapResult bootstrapServer(FileCache fileCache, Map<String, String> immutableProperties, HttpConfig httpConfig) {
    var serverVersion = getServerVersion(scannerHttpClient);
    var serverLabel = guessServerLabelFromVersion(serverVersion);
    LOG.info("Communicating with {} {}", serverLabel, serverVersion);
    if (VersionUtils.isAtLeastIgnoringQualifier(serverVersion, SQ_VERSION_TOKEN_AUTHENTICATION) && Objects.nonNull(httpConfig.getLogin())) {
      LOG.warn("Use of '{}' property has been deprecated in favor of '{}' (or the env variable alternative '{}'). Please use the latter when passing a token.", SONAR_LOGIN,
        SONAR_TOKEN, TOKEN_ENV_VARIABLE);
    }
    ScannerEngineFacade scannerFacade;
    if (VersionUtils.isAtLeastIgnoringQualifier(serverVersion, SQ_VERSION_NEW_BOOTSTRAPPING)) {
      scannerFacade = buildNewFacade(fileCache, immutableProperties, httpConfig,
        (launcher, adaptedProperties) -> NewScannerEngineFacade.forSonarQubeServer(adaptedProperties, launcher, serverVersion));
    } else {
      var launcher = launcherFactory.createLauncher(scannerHttpClient, fileCache);
      var adaptedProperties = adaptDeprecatedPropertiesForInProcessBootstrapping(immutableProperties, httpConfig);
      scannerFacade = new InProcessScannerEngineFacade(adaptedProperties, launcher, false, serverVersion);
    }
    return new SuccessfulBootstrap(scannerFacade);
  }

  static String guessServerLabelFromVersion(String serverVersion) {
    if (VersionUtils.compareMajor(serverVersion, 10) <= 0 || VersionUtils.compareMajor(serverVersion, 2025) >= 0) {
      return "SonarQube Server";
    } else {
      return "SonarQube Community Build";
    }
  }

  private ScannerEngineFacade buildNewFacade(FileCache fileCache, Map<String, String> immutableProperties, HttpConfig httpConfig,
    BiFunction<ScannerEngineLauncher, Map<String, String>, ScannerEngineFacade> facadeFactory) {
    var launcher = scannerEngineLauncherFactory.createLauncher(scannerHttpClient, fileCache, immutableProperties);
    var adaptedProperties = adaptSslPropertiesToScannerProperties(immutableProperties, httpConfig);
    return facadeFactory.apply(launcher, adaptedProperties);
  }

  private static ScannerEngineBootstrapResult handleException(MessageException e) {
    var message = new StringBuilder(e.getMessage());
    if (e.getCause() instanceof HttpException) {
      var httpEx = (HttpException) e.getCause();
      var code = httpEx.getCode();
      if (code == 401 || code == 403) {
        var helpMessage = "Please check the property " + ScannerProperties.SONAR_TOKEN +
          " or the environment variable " + TOKEN_ENV_VARIABLE + ".";
        message.append(". ").append(helpMessage);
      }
      if (code == 407) {
        var helpMessage = "Please check the properties " + ScannerProperties.SONAR_SCANNER_PROXY_USER +
          " and " + ScannerProperties.SONAR_SCANNER_PROXY_PASSWORD + ".";
        message.append(". ").append(helpMessage);
      }
    }
    logWithStacktraceOnlyIfDebug(message.toString(), e);
    return new FailedBootstrap();
  }

  /**
   * For functional errors, the stacktrace is not necessary. It is only useful for debugging.
   */
  private static void logWithStacktraceOnlyIfDebug(String message, Throwable t) {
    if (LOG.isDebugEnabled()) {
      LOG.error(message, t);
    } else {
      LOG.error(message);
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
      setSystemPropertyIfNotAlreadySet(HTTP_PROXY_HOST, ((InetSocketAddress) proxy.address()).getHostString());
      setSystemPropertyIfNotAlreadySet(HTTPS_PROXY_HOST, ((InetSocketAddress) proxy.address()).getHostString());
      setSystemPropertyIfNotAlreadySet(HTTP_PROXY_PORT, String.valueOf(((InetSocketAddress) proxy.address()).getPort()));
      setSystemPropertyIfNotAlreadySet(HTTPS_PROXY_PORT, String.valueOf(((InetSocketAddress) proxy.address()).getPort()));
    }
    // Those are not standard JVM properties, but they are supported by the Scanner Engine.
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

  private void setSystemPropertyIfNotAlreadySet(String key, @Nullable String value) {
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

  private static String getServerVersion(ScannerHttpClient scannerHttpClient) {
    try {
      return scannerHttpClient.callRestApi("/analysis/version");
    } catch (HttpException httpException) {
      // Fallback to the old endpoint
      try {
        var serverVersion = scannerHttpClient.callWebApi("/api/server/version");
        if (VersionUtils.isAtLeastIgnoringQualifier(serverVersion, SQ_VERSION_NEW_BOOTSTRAPPING)) {
          // If version is greater than 10.6, we would have expected the first call to succeed, so it is better to throw the original exception than moving on
          // and having the scanner failing later (usually because of authentication issues)
          throw httpException;
        }
        return serverVersion;
      } catch (Exception e2) {
        var ex = new MessageException("Failed to query server version: " + e2.getMessage(), e2);
        if (!e2.equals(httpException)) {
          ex.addSuppressed(httpException);
        }
        throw ex;
      }
    } catch (Exception e) {
      throw new MessageException("Failed to query server version: " + e.getMessage(), e);
    }
  }

  private void initBootstrapDefaultValues(ScannerEndpoint endpoint) {
    setBootstrapProperty(ScannerProperties.HOST_URL, endpoint.getWebEndpoint());
    setBootstrapProperty(ScannerProperties.API_BASE_URL, endpoint.getApiEndpoint());
    if (endpoint.isSonarQubeCloud()) {
      setBootstrapProperty(ScannerProperties.SONARQUBE_CLOUD_URL, endpoint.getWebEndpoint());
    }
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
  static Map<String, String> adaptSslPropertiesToScannerProperties(Map<String, String> bootstrapProperties, HttpConfig httpConfig) {
    var result = new HashMap<>(bootstrapProperties);
    var keyStore = httpConfig.getSslConfig().getKeyStore();
    if (keyStore != null && keyStore.isFromJvm()) {
      result.put(SONAR_SCANNER_KEYSTORE_PATH, keyStore.getPath().toString());
      keyStore.getKeyStorePassword().ifPresent(password -> result.put(SONAR_SCANNER_KEYSTORE_PASSWORD, password));
    }

    var trustStore = httpConfig.getSslConfig().getTrustStore();
    if (trustStore != null && trustStore.isFromJvm()) {
      result.put(SONAR_SCANNER_TRUSTSTORE_PATH, trustStore.getPath().toString());
      trustStore.getKeyStorePassword().ifPresent(password -> result.put(SONAR_SCANNER_TRUSTSTORE_PASSWORD, password));
    }
    return Map.copyOf(result);
  }

}
