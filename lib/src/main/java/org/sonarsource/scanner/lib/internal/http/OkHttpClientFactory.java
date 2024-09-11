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

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.Security;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.util.KeyStoreUtils;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.scanner.lib.ScannerProperties;
import org.sonarsource.scanner.lib.internal.http.ssl.CertificateStore;
import org.sonarsource.scanner.lib.internal.http.ssl.SslConfig;

import static java.lang.Integer.parseInt;
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
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

public class OkHttpClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(OkHttpClientFactory.class);

  static final CookieManager COOKIE_MANAGER;
  private static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
  // use the same cookie jar for all instances
  private static final JavaNetCookieJar COOKIE_JAR;

  static final int DEFAULT_CONNECT_TIMEOUT = 5;
  static final int DEFAULT_RESPONSE_TIMEOUT = 0;
  static final String READ_TIMEOUT_SEC_PROPERTY = "sonar.ws.timeout";
  static final int DEFAULT_READ_TIMEOUT_SEC = 60;

  private OkHttpClientFactory() {
    // only statics
  }

  static {
    COOKIE_MANAGER = new CookieManager();
    COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
    COOKIE_JAR = new JavaNetCookieJar(COOKIE_MANAGER);
  }

  static OkHttpClient create(Map<String, String> bootstrapProperties, Path sonarUserHome) {

    String oldSocketTimeout = defaultIfBlank(bootstrapProperties.get(READ_TIMEOUT_SEC_PROPERTY), valueOf(DEFAULT_READ_TIMEOUT_SEC));
    String socketTimeout = defaultIfBlank(bootstrapProperties.get(SONAR_SCANNER_SOCKET_TIMEOUT), oldSocketTimeout);
    String connectTimeout = defaultIfBlank(bootstrapProperties.get(SONAR_SCANNER_CONNECT_TIMEOUT), valueOf(DEFAULT_CONNECT_TIMEOUT));
    String responseTimeout = defaultIfBlank(bootstrapProperties.get(SONAR_SCANNER_RESPONSE_TIMEOUT), valueOf(DEFAULT_RESPONSE_TIMEOUT));
    var sslContext = configureSsl(parseSslConfig(bootstrapProperties, sonarUserHome));

    OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
      .connectTimeout(parseDurationProperty(connectTimeout, SONAR_SCANNER_CONNECT_TIMEOUT), TimeUnit.MILLISECONDS)
      .readTimeout(parseDurationProperty(socketTimeout, SONAR_SCANNER_SOCKET_TIMEOUT), TimeUnit.MILLISECONDS)
      .callTimeout(parseDurationProperty(responseTimeout, SONAR_SCANNER_RESPONSE_TIMEOUT), TimeUnit.MILLISECONDS)
      .cookieJar(COOKIE_JAR)
      .sslSocketFactory(sslContext.getSslSocketFactory(), sslContext.getTrustManager().orElseThrow());

    ConnectionSpec tls = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .allEnabledTlsVersions()
      .allEnabledCipherSuites()
      .build();
    okHttpClientBuilder.connectionSpecs(asList(tls, ConnectionSpec.CLEARTEXT));

    // OkHttp detects 'http.proxyHost' java property already, so just focus on sonar properties
    String proxyHost = defaultIfBlank(bootstrapProperties.get(SONAR_SCANNER_PROXY_HOST), null);
    if (proxyHost != null) {
      var defaultProxyPort = bootstrapProperties.get(ScannerProperties.HOST_URL).startsWith("https") ? "443" : "80";
      String proxyPortStr = defaultIfBlank(bootstrapProperties.get(SONAR_SCANNER_PROXY_PORT), defaultProxyPort);
      var proxyPort = parseIntProperty(proxyPortStr, SONAR_SCANNER_PROXY_PORT);
      okHttpClientBuilder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
    }

    var scannerProxyUser = bootstrapProperties.get(SONAR_SCANNER_PROXY_USER);
    String proxyUser = scannerProxyUser != null ? scannerProxyUser : System.getProperty("http.proxyUser", "");
    if (isNotBlank(proxyUser)) {
      var scannerProxyPwd = bootstrapProperties.get(SONAR_SCANNER_PROXY_PASSWORD);
      String proxyPassword = scannerProxyPwd != null ? scannerProxyPwd : System.getProperty("http.proxyPassword", "");
      okHttpClientBuilder.proxyAuthenticator((route, response) -> {
        if (response.request().header(PROXY_AUTHORIZATION) != null) {
          // Give up, we've already attempted to authenticate.
          return null;
        }
        if (HttpURLConnection.HTTP_PROXY_AUTH == response.code()) {
          String credential = Credentials.basic(proxyUser, proxyPassword, UTF_8);
          return response.request().newBuilder().header(PROXY_AUTHORIZATION, credential).build();
        }
        return null;
      });
    }

    var logging = new HttpLoggingInterceptor(LOG::debug);
    logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
    okHttpClientBuilder.addInterceptor(logging);

    return okHttpClientBuilder.build();
  }

  /**
   * For testing, we can accept timeouts that are smaller than a second, expressed using ISO-8601 format for durations.
   * If we can't parse as ISO-8601, then fallback to the official format that is simply the number of seconds
   */
  private static int parseDurationProperty(String propValue, String propKey) {
    try {
      return (int) Duration.parse(propValue).toMillis();
    } catch (DateTimeParseException e) {
      return parseIntProperty(propValue, propKey) * 1_000;
    }
  }

  private static int parseIntProperty(String propValue, String propKey) {
    try {
      return parseInt(propValue);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(propKey + " is not a valid integer: " + propValue, e);
    }
  }

  private static SslConfig parseSslConfig(Map<String, String> bootstrapProperties, Path sonarUserHome) {
    var keyStorePath = defaultIfBlank(bootstrapProperties.get(SONAR_SCANNER_KEYSTORE_PATH), sonarUserHome.resolve("ssl/keystore.p12").toString());
    var keyStorePassword = defaultIfBlank(bootstrapProperties.get(SONAR_SCANNER_KEYSTORE_PASSWORD), CertificateStore.DEFAULT_PASSWORD);
    var trustStorePath = defaultIfBlank(bootstrapProperties.get(SONAR_SCANNER_TRUSTSTORE_PATH), sonarUserHome.resolve("ssl/truststore.p12").toString());
    var trustStorePassword = defaultIfBlank(bootstrapProperties.get(SONAR_SCANNER_TRUSTSTORE_PASSWORD), CertificateStore.DEFAULT_PASSWORD);
    var keyStore = new CertificateStore(Path.of(keyStorePath), keyStorePassword);
    var trustStore = new CertificateStore(Path.of(trustStorePath), trustStorePassword);
    return new SslConfig(keyStore, trustStore);
  }

  private static SSLFactory configureSsl(SslConfig sslConfig) {
    var sslFactoryBuilder = SSLFactory.builder()
      .withDefaultTrustMaterial()
      .withSystemTrustMaterial();
    if (System.getProperties().containsKey("javax.net.ssl.keyStore")) {
      sslFactoryBuilder.withSystemPropertyDerivedIdentityMaterial();
    }
    var keyStoreConfig = sslConfig.getKeyStore();
    if (keyStoreConfig != null && Files.exists(keyStoreConfig.getPath())) {
      sslFactoryBuilder.withIdentityMaterial(keyStoreConfig.getPath(), keyStoreConfig.getKeyStorePassword().toCharArray(), keyStoreConfig.getKeyStoreType());
    }
    var trustStoreConfig = sslConfig.getTrustStore();
    if (trustStoreConfig != null && Files.exists(trustStoreConfig.getPath())) {
      Security.addProvider(new BouncyCastleProvider());
      KeyStore trustStore = KeyStoreUtils.loadKeyStore(
        trustStoreConfig.getPath(),
        trustStoreConfig.getKeyStorePassword().toCharArray(),
        trustStoreConfig.getKeyStoreType(),
        BouncyCastleProvider.PROVIDER_NAME);
      sslFactoryBuilder.withTrustMaterial(trustStore);
    }
    return sslFactoryBuilder.build();
  }

}
