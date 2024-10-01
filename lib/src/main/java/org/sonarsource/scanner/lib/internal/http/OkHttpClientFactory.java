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

import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.exception.GenericKeyStoreException;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.scanner.lib.internal.http.ssl.SslConfig;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class OkHttpClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(OkHttpClientFactory.class);

  static final CookieManager COOKIE_MANAGER;
  private static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
  // use the same cookie jar for all instances
  private static final JavaNetCookieJar COOKIE_JAR;

  private OkHttpClientFactory() {
    // only statics
  }

  static {
    COOKIE_MANAGER = new CookieManager();
    COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
    COOKIE_JAR = new JavaNetCookieJar(COOKIE_MANAGER);
  }

  static OkHttpClient create(HttpConfig httpConfig) {

    var sslContext = configureSsl(httpConfig.getSslConfig());

    OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
      .connectTimeout(httpConfig.getConnectTimeout().toMillis(), TimeUnit.MILLISECONDS)
      .readTimeout(httpConfig.getSocketTimeout().toMillis(), TimeUnit.MILLISECONDS)
      .callTimeout(httpConfig.getResponseTimeout().toMillis(), TimeUnit.MILLISECONDS)
      .cookieJar(COOKIE_JAR)
      .sslSocketFactory(sslContext.getSslSocketFactory(), sslContext.getTrustManager().orElseThrow());

    ConnectionSpec tls = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .allEnabledTlsVersions()
      .allEnabledCipherSuites()
      .build();
    okHttpClientBuilder.connectionSpecs(asList(tls, ConnectionSpec.CLEARTEXT));

    if (httpConfig.getProxy() != null) {
      okHttpClientBuilder.proxy(httpConfig.getProxy());
    }

    if (isNotBlank(httpConfig.getProxyUser())) {
      okHttpClientBuilder.proxyAuthenticator((route, response) -> {
        if (response.request().header(PROXY_AUTHORIZATION) != null) {
          // Give up, we've already attempted to authenticate.
          return null;
        }
        if (HttpURLConnection.HTTP_PROXY_AUTH == response.code()) {
          String credential = Credentials.basic(httpConfig.getProxyUser(), httpConfig.getProxyPassword(), UTF_8);
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
      KeyStore trustStore = loadKeyStoreWithBouncyCastle(
        trustStoreConfig.getPath(),
        trustStoreConfig.getKeyStorePassword().toCharArray(),
        trustStoreConfig.getKeyStoreType());
      sslFactoryBuilder.withTrustMaterial(trustStore);
    }
    return sslFactoryBuilder.build();
  }

  public static KeyStore loadKeyStoreWithBouncyCastle(Path keystorePath, char[] keystorePassword, String keystoreType) {
    try (InputStream keystoreInputStream = Files.newInputStream(keystorePath, StandardOpenOption.READ)) {
      KeyStore keystore = KeyStore.getInstance(keystoreType, new BouncyCastleProvider());
      keystore.load(keystoreInputStream, keystorePassword);
      return keystore;
    } catch (Exception e) {
      throw new GenericKeyStoreException(e);
    }
  }

}
