/*
 * SonarQube Scanner API
 * Copyright (C) 2011-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.scanner.api.internal;

import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.sonarsource.scanner.api.internal.cache.Logger;

import static java.util.Arrays.asList;

public class OkHttpClientFactory {

  static final int CONNECT_TIMEOUT_MILLISECONDS = 5000;
  static final int READ_TIMEOUT_MILLISECONDS = 60000;
  static final String NONE = "NONE";
  static final String P11KEYSTORE = "PKCS11";

  private OkHttpClientFactory() {
    // only statics
  }

  static OkHttpClient create(Logger logger) {
    OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder();

    okHttpClientBuilder.connectTimeout(CONNECT_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
    okHttpClientBuilder.readTimeout(READ_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);

    ConnectionSpec tls = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .allEnabledTlsVersions()
      .allEnabledCipherSuites()
      .supportsTlsExtensions(true)
      .build();
    okHttpClientBuilder.connectionSpecs(asList(tls, ConnectionSpec.CLEARTEXT));
    X509TrustManager systemDefaultTrustManager = systemDefaultTrustManager();
    okHttpClientBuilder.sslSocketFactory(systemDefaultSslSocketFactory(systemDefaultTrustManager, logger), systemDefaultTrustManager);

    // OkHttp detect 'http.proxyHost' java property, but credentials should be filled
    final String proxyUser = System.getProperty("http.proxyUser", "");
    if (!System.getProperty("http.proxyHost", "").isEmpty() && !proxyUser.isEmpty()) {
      okHttpClientBuilder.proxyAuthenticator((route, response) -> {
        if (HttpURLConnection.HTTP_PROXY_AUTH == response.code()) {
          String credential = Credentials.basic(proxyUser, System.getProperty("http.proxyPassword"));
          return response.request().newBuilder().header("Proxy-Authorization", credential).build();
        }
        return null;
      });
    }
    return okHttpClientBuilder.build();
  }

  private static X509TrustManager systemDefaultTrustManager() {
    try {
      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init((KeyStore) null);
      TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
      if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
        throw new IllegalStateException("Unexpected default trust managers:" + Arrays.toString(trustManagers));
      }
      return (X509TrustManager) trustManagers[0];
    } catch (GeneralSecurityException e) {
      // The system has no TLS. Just give up.
      throw new AssertionError(e);
    }
  }

  private static SSLSocketFactory systemDefaultSslSocketFactory(X509TrustManager trustManager, Logger logger) {
    KeyManager[] defaultKeyManager;
    try {
      defaultKeyManager = getDefaultKeyManager(logger);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to get default key manager", e);
    }
    try {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(defaultKeyManager, new TrustManager[] {trustManager}, null);
      return sslContext.getSocketFactory();
    } catch (GeneralSecurityException e) {
      // The system has no TLS. Just give up.
      throw new AssertionError(e);
    }
  }

  /**
   * Inspired from sun.security.ssl.SSLContextImpl#getDefaultKeyManager()
   */
  private static synchronized KeyManager[] getDefaultKeyManager(Logger logger) throws Exception {

    final String defaultKeyStore = System.getProperty("javax.net.ssl.keyStore", "");
    String defaultKeyStoreType = System.getProperty("javax.net.ssl.keyStoreType", KeyStore.getDefaultType());
    String defaultKeyStoreProvider = System.getProperty("javax.net.ssl.keyStoreProvider", "");

    logger.debug("keyStore is : " + defaultKeyStore);
    logger.debug("keyStore type is : " + defaultKeyStoreType);
    logger.debug("keyStore provider is : " + defaultKeyStoreProvider);

    if (P11KEYSTORE.equals(defaultKeyStoreType) && !NONE.equals(defaultKeyStore)) {
      throw new IllegalArgumentException("if keyStoreType is " + P11KEYSTORE + ", then keyStore must be " + NONE);
    }

    KeyStore ks = null;
    String defaultKeyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword", "");
    char[] passwd = defaultKeyStorePassword.isEmpty() ? null : defaultKeyStorePassword.toCharArray();

    /*
     * Try to initialize key store.
     */
    if (!defaultKeyStoreType.isEmpty()) {
      logger.debug("init keystore");
      if (defaultKeyStoreProvider.isEmpty()) {
        ks = KeyStore.getInstance(defaultKeyStoreType);
      } else {
        ks = KeyStore.getInstance(defaultKeyStoreType, defaultKeyStoreProvider);
      }
      if (!defaultKeyStore.isEmpty() && !NONE.equals(defaultKeyStore)) {
        try (FileInputStream fs = new FileInputStream(defaultKeyStore)) {
          ks.load(fs, passwd);
        }
      } else {
        ks.load(null, passwd);
      }
    }

    /*
     * Try to initialize key manager.
     */
    logger.debug("init keymanager of type " + KeyManagerFactory.getDefaultAlgorithm());
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

    if (P11KEYSTORE.equals(defaultKeyStoreType)) {
      // do not pass key passwd if using token
      kmf.init(ks, null);
    } else {
      kmf.init(ks, passwd);
    }

    return kmf.getKeyManagers();
  }
}
