/*
 * SonarScanner Java Library
 * Copyright (C) 2011-2025 SonarSource Sàrl
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

import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Optional;
import javax.annotation.Nullable;
import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.exception.GenericKeyStoreException;
import nl.altindag.ssl.util.KeyStoreUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.scanner.lib.internal.http.ssl.CertificateStore;
import org.sonarsource.scanner.lib.internal.http.ssl.SslConfig;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sonarsource.scanner.lib.ScannerProperties.SONAR_SCANNER_SKIP_SYSTEM_TRUSTSTORE;

public class HttpClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(HttpClientFactory.class);

  static final CookieManager COOKIE_MANAGER;
  public static final String BC_IGNORE_USELESS_PASSWD = "org.bouncycastle.pkcs12.ignore_useless_passwd";

  private HttpClientFactory() {
  }

  static {
    COOKIE_MANAGER = new CookieManager();
    COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
  }

  static HttpClient create(HttpConfig httpConfig) {
    var sslContext = configureSsl(httpConfig.getSslConfig(), httpConfig.skipSystemTruststore());

    var httpClientBuilder = HttpClient.newBuilder()
      .connectTimeout(httpConfig.getConnectTimeout())
      .cookieHandler(COOKIE_MANAGER)
      .sslContext(sslContext.getSslContext())
      .sslParameters(sslContext.getSslParameters())
      .followRedirects(HttpClient.Redirect.NEVER);

    if (httpConfig.getProxy() != null) {
      var proxyAddress = httpConfig.getProxy().address();
      if (proxyAddress instanceof InetSocketAddress) {
        httpClientBuilder.proxy(ProxySelector.of((InetSocketAddress) proxyAddress));
      }
    }

    return httpClientBuilder.build();
  }

  private static SSLFactory configureSsl(SslConfig sslConfig, boolean skipSystemTrustMaterial) {
    var sslFactoryBuilder = SSLFactory.builder()
      .withDefaultTrustMaterial();
    if (!skipSystemTrustMaterial) {
      LOG.debug("Loading OS trusted SSL certificates...");
      LOG.debug("This operation might be slow or even get stuck. You can skip it by passing the scanner property '{}=true'", SONAR_SCANNER_SKIP_SYSTEM_TRUSTSTORE);
      sslFactoryBuilder.withSystemTrustMaterial();
    }
    var keyStoreConfig = sslConfig.getKeyStore();
    if (keyStoreConfig != null) {
      keyStoreConfig.getKeyStorePassword()
        .ifPresentOrElse(
          password -> sslFactoryBuilder.withIdentityMaterial(keyStoreConfig.getPath(), password.toCharArray(), keyStoreConfig.getKeyStoreType()),
          () -> loadIdentityMaterialWithDefaultPassword(sslFactoryBuilder, keyStoreConfig.getPath()));
    }
    var trustStoreConfig = sslConfig.getTrustStore();
    if (trustStoreConfig != null) {
      KeyStore trustStore;
      try {
        trustStore = loadTrustStoreWithBouncyCastle(
          trustStoreConfig.getPath(),
          trustStoreConfig.getKeyStorePassword().orElse(null),
          trustStoreConfig.getKeyStoreType(),
          trustStoreConfig.isFromJvm());
        LOG.debug("Loaded truststore from '{}' containing {} certificates", trustStoreConfig.getPath(), trustStore.size());
      } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException e) {
        throw new GenericKeyStoreException("Unable to read truststore from '" + trustStoreConfig.getPath() + "'", e);
      }
      sslFactoryBuilder.withTrustMaterial(trustStore);
    }
    return sslFactoryBuilder.build();
  }

  private static void loadIdentityMaterialWithDefaultPassword(SSLFactory.Builder sslFactoryBuilder, Path path) {
    try {
      var keystore = KeyStoreUtils.loadKeyStore(path, CertificateStore.DEFAULT_PASSWORD.toCharArray(), CertificateStore.DEFAULT_STORE_TYPE);
      sslFactoryBuilder.withIdentityMaterial(keystore, CertificateStore.DEFAULT_PASSWORD.toCharArray());
    } catch (GenericKeyStoreException e) {
      var keystore = KeyStoreUtils.loadKeyStore(path, CertificateStore.OLD_DEFAULT_PASSWORD.toCharArray(), CertificateStore.DEFAULT_STORE_TYPE);
      LOG.warn("Using deprecated default password for keystore '{}'.", path);
      sslFactoryBuilder.withIdentityMaterial(keystore, CertificateStore.OLD_DEFAULT_PASSWORD.toCharArray());
    }
  }

  static KeyStore loadTrustStoreWithBouncyCastle(Path keystorePath, @Nullable String keystorePassword, String keystoreType, boolean fromJvm) throws IOException,
    KeyStoreException, CertificateException, NoSuchAlgorithmException {
    Properties.setThreadOverride(BC_IGNORE_USELESS_PASSWD, true);
    KeyStore keystore = KeyStore.getInstance(keystoreType, new BouncyCastleProvider());
    if (keystorePassword != null) {
      loadKeyStoreWithPassword(keystorePath, keystore, keystorePassword);
    } else {
      try {
        loadKeyStoreWithPassword(keystorePath, keystore, CertificateStore.DEFAULT_PASSWORD);
      } catch (Exception e) {
        if (!fromJvm) {
          loadKeyStoreWithPassword(keystorePath, keystore, CertificateStore.OLD_DEFAULT_PASSWORD);
          LOG.warn("Using deprecated default password for truststore '{}'.", keystorePath);
        }
      }
    }
    return keystore;
  }

  private static void loadKeyStoreWithPassword(Path keystorePath, KeyStore keystore, String oldDefaultPassword) throws IOException, NoSuchAlgorithmException, CertificateException {
    try (InputStream keystoreInputStream = Files.newInputStream(keystorePath, StandardOpenOption.READ)) {
      keystore.load(keystoreInputStream, oldDefaultPassword.toCharArray());
    }
  }

}
