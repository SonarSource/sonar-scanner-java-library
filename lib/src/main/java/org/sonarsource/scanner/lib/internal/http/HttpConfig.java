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
package org.sonarsource.scanner.lib.internal.http;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.scanner.lib.ScannerProperties;
import org.sonarsource.scanner.lib.internal.InternalProperties;
import org.sonarsource.scanner.lib.internal.http.ssl.CertificateStore;
import org.sonarsource.scanner.lib.internal.http.ssl.SslConfig;

import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_CONNECT_TIMEOUT;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_KEYSTORE_PASSWORD;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_KEYSTORE_PATH;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_PROXY_HOST;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_PROXY_PASSWORD;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_PROXY_PORT;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_PROXY_USER;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_RESPONSE_TIMEOUT;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_SOCKET_TIMEOUT;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_TRUSTSTORE_PASSWORD;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_TRUSTSTORE_PATH;

public class HttpConfig {

  private static final Logger LOG = LoggerFactory.getLogger(HttpConfig.class);

  static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
  static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ZERO;
  public static final String READ_TIMEOUT_SEC_PROPERTY = "sonar.ws.timeout";
  static final Duration DEFAULT_READ_TIMEOUT_SEC = Duration.ofSeconds(60);
  static final int DEFAULT_PROXY_PORT = 80;

  private final String webApiBaseUrl;
  private final String restApiBaseUrl;
  @Nullable
  private final String token;
  @Nullable
  private final String login;
  @Nullable
  private final String password;

  private final SslConfig sslConfig;
  private final Duration socketTimeout;
  private final Duration connectTimeout;
  private final Duration responseTimeout;
  @Nullable
  private final Proxy proxy;
  private final String proxyUser;
  private final String proxyPassword;
  private final String userAgent;

  public HttpConfig(Map<String, String> bootstrapProperties, Path sonarUserHome) {
    this.webApiBaseUrl = StringUtils.removeEnd(bootstrapProperties.get(ScannerProperties.HOST_URL), "/");
    this.restApiBaseUrl = StringUtils.removeEnd(bootstrapProperties.get(ScannerProperties.API_BASE_URL), "/");
    this.token = bootstrapProperties.get(ScannerProperties.SONAR_TOKEN);
    this.login = bootstrapProperties.get(ScannerProperties.SONAR_LOGIN);
    this.password = bootstrapProperties.get(ScannerProperties.SONAR_PASSWORD);
    this.userAgent = format("%s/%s", bootstrapProperties.get(InternalProperties.SCANNER_APP), bootstrapProperties.get(InternalProperties.SCANNER_APP_VERSION));
    this.socketTimeout = loadDuration(bootstrapProperties, SONAR_SCANNER_SOCKET_TIMEOUT, READ_TIMEOUT_SEC_PROPERTY, DEFAULT_READ_TIMEOUT_SEC);
    this.connectTimeout = loadDuration(bootstrapProperties, SONAR_SCANNER_CONNECT_TIMEOUT, null, DEFAULT_CONNECT_TIMEOUT);
    this.responseTimeout = loadDuration(bootstrapProperties, SONAR_SCANNER_RESPONSE_TIMEOUT, null, DEFAULT_RESPONSE_TIMEOUT);
    this.sslConfig = loadSslConfig(bootstrapProperties, sonarUserHome);
    this.proxy = loadProxy(bootstrapProperties);
    this.proxyUser = loadProxyUser(bootstrapProperties);
    this.proxyPassword = loadProxyPassword(bootstrapProperties);
  }

  private static String loadProxyPassword(Map<String, String> bootstrapProperties) {
    var scannerProxyPwd = bootstrapProperties.get(SONAR_SCANNER_PROXY_PASSWORD);
    return scannerProxyPwd != null ? scannerProxyPwd : System.getProperty("http.proxyPassword", "");
  }

  private static String loadProxyUser(Map<String, String> bootstrapProperties) {
    var scannerProxyUser = bootstrapProperties.get(SONAR_SCANNER_PROXY_USER);
    return scannerProxyUser != null ? scannerProxyUser : System.getProperty("http.proxyUser", "");
  }

  private static Duration loadDuration(Map<String, String> bootstrapProperties, String propKey, @Nullable String deprecatedPropKey, Duration defaultValue) {
    if (bootstrapProperties.containsKey(propKey)) {
      return parseDurationProperty(bootstrapProperties.get(propKey), propKey);
    } else if (deprecatedPropKey != null && bootstrapProperties.containsKey(deprecatedPropKey)) {
      LOG.warn("Property {} is deprecated and will be removed in a future version. Please use {} instead.", deprecatedPropKey, propKey);
      return parseDurationProperty(bootstrapProperties.get(deprecatedPropKey), deprecatedPropKey);
    } else {
      return defaultValue;
    }
  }

  @Nullable
  private static Proxy loadProxy(Map<String, String> bootstrapProperties) {
    // OkHttp detects 'http.proxyHost' java property already, so just focus on sonar properties
    String proxyHost = defaultIfBlank(bootstrapProperties.get(SONAR_SCANNER_PROXY_HOST), null);
    if (proxyHost != null) {
      int proxyPort;
      if (bootstrapProperties.containsKey(SONAR_SCANNER_PROXY_PORT)) {
        proxyPort = parseIntProperty(bootstrapProperties.get(SONAR_SCANNER_PROXY_PORT), SONAR_SCANNER_PROXY_PORT);
      } else {
        proxyPort = DEFAULT_PROXY_PORT;
      }
      return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    } else {
      return null;
    }
  }

  /**
   * For testing, we can accept timeouts that are smaller than a second, expressed using ISO-8601 format for durations.
   * If we can't parse as ISO-8601, then fallback to the official format that is simply the number of seconds
   */
  private static Duration parseDurationProperty(String propValue, String propKey) {
    try {
      return Duration.parse(propValue);
    } catch (DateTimeParseException e) {
      return Duration.ofSeconds(parseIntProperty(propValue, propKey));
    }
  }

  private static int parseIntProperty(String propValue, String propKey) {
    try {
      return parseInt(propValue);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(propKey + " is not a valid integer: " + propValue, e);
    }
  }

  private static SslConfig loadSslConfig(Map<String, String> bootstrapProperties, Path sonarUserHome) {
    var keyStore = loadKeyStoreConfig(bootstrapProperties, sonarUserHome);
    var trustStore = loadTrustStoreConfig(bootstrapProperties, sonarUserHome);
    return new SslConfig(keyStore, trustStore);
  }

  @Nullable
  private static CertificateStore loadTrustStoreConfig(Map<String, String> bootstrapProperties, Path sonarUserHome) {
    var trustStorePath = parseFileProperty(bootstrapProperties, SONAR_SCANNER_TRUSTSTORE_PATH, "truststore", sonarUserHome.resolve("ssl/truststore.p12"));
    if (trustStorePath != null) {
      LOG.debug("Using truststore: {}", trustStorePath);
      var trustStorePassword = defaultIfBlank(bootstrapProperties.get(SONAR_SCANNER_TRUSTSTORE_PASSWORD), CertificateStore.DEFAULT_PASSWORD);
      return new CertificateStore(trustStorePath, trustStorePassword);
    }
    return null;
  }

  @Nullable
  private static CertificateStore loadKeyStoreConfig(Map<String, String> bootstrapProperties, Path sonarUserHome) {
    var keyStorePath = parseFileProperty(bootstrapProperties, SONAR_SCANNER_KEYSTORE_PATH, "keystore", sonarUserHome.resolve("ssl/keystore.p12"));
    if (keyStorePath != null) {
      LOG.debug("Using keystore: {}", keyStorePath);
      var keyStorePassword = defaultIfBlank(bootstrapProperties.get(SONAR_SCANNER_KEYSTORE_PASSWORD), CertificateStore.DEFAULT_PASSWORD);
      return new CertificateStore(keyStorePath, keyStorePassword);
    }
    return null;
  }

  @Nullable
  private static Path parseFileProperty(Map<String, String> bootstrapProperties, String propKey, String labelForLogs, Path defaultPath) {
    if (bootstrapProperties.containsKey(propKey)) {
      var keyStorePath = Paths.get(bootstrapProperties.get(propKey));
      if (!Files.exists(keyStorePath)) {
        throw new IllegalArgumentException("The " + labelForLogs + " file does not exist: " + keyStorePath);
      }
      return keyStorePath;
    } else if (Files.isRegularFile(defaultPath)) {
      return defaultPath;
    }
    return null;
  }

  public String getWebApiBaseUrl() {
    return webApiBaseUrl;
  }

  public String getRestApiBaseUrl() {
    return restApiBaseUrl;
  }

  @Nullable
  public String getToken() {
    return token;
  }

  @Nullable
  public String getLogin() {
    return login;
  }

  @Nullable
  public String getPassword() {
    return password;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public SslConfig getSslConfig() {
    return sslConfig;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public Duration getResponseTimeout() {
    return responseTimeout;
  }

  public Duration getSocketTimeout() {
    return socketTimeout;
  }

  @Nullable
  public Proxy getProxy() {
    return proxy;
  }

  public String getProxyUser() {
    return proxyUser;
  }

  public String getProxyPassword() {
    return proxyPassword;
  }
}
