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
package org.sonarsource.scanner.impl;

import com.squareup.okhttp.ConnectionSpec;
import com.squareup.okhttp.OkHttpClient;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;

import static java.util.Arrays.asList;

public class OkHttpClientFactory {

  static final int CONNECT_TIMEOUT_MILLISECONDS = 5000;
  static final int READ_TIMEOUT_MILLISECONDS = 60000;

  private OkHttpClientFactory() {
    // only statics
  }

  public static OkHttpClient create() {
    return create(new JavaVersion());
  }

  static OkHttpClient create(JavaVersion javaVersion) {
    OkHttpClient httpClient = new OkHttpClient();
    httpClient.setConnectTimeout(CONNECT_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
    httpClient.setReadTimeout(READ_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
    ConnectionSpec tls = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .allEnabledTlsVersions()
      .allEnabledCipherSuites()
      .supportsTlsExtensions(true)
      .build();
    httpClient.setConnectionSpecs(asList(tls, ConnectionSpec.CLEARTEXT));
    httpClient.setSslSocketFactory(createSslSocketFactory(javaVersion));
    return httpClient;
  }

  private static SSLSocketFactory createSslSocketFactory(JavaVersion javaVersion) {
    try {
      SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
      return enableTls12InJava7(sslSocketFactory, javaVersion);
    } catch (Exception e) {
      throw new IllegalStateException("Fail to init TLS context", e);
    }
  }

  private static SSLSocketFactory enableTls12InJava7(SSLSocketFactory sslSocketFactory, JavaVersion javaVersion) {
    if (javaVersion.isJava7()) {
      // OkHttp executes SSLContext.getInstance("TLS") by default (see
      // https://github.com/square/okhttp/blob/c358656/okhttp/src/main/java/com/squareup/okhttp/OkHttpClient.java#L616)
      // As only TLS 1.0 is enabled by default in Java 7, the SSLContextFactory must be changed
      // in order to support all versions from 1.0 to 1.2.
      // Note that this is not overridden for Java 8 as TLS 1.2 is enabled by default.
      // Keeping getInstance("TLS") allows to support potential future versions of TLS on Java 8.
      return new Tls12Java7SocketFactory(sslSocketFactory);
    }
    return sslSocketFactory;
  }

  static class JavaVersion {
    boolean isJava7() {
      return System.getProperty("java.version").startsWith("1.7.");
    }
  }
}
