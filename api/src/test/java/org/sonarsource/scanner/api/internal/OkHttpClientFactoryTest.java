/*
 * SonarQube Scanner API
 * Copyright (C) 2011-2017 SonarSource SA
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
package org.sonarsource.scanner.api.internal;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import java.util.List;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Test;
import org.sonarsource.scanner.api.internal.cache.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class OkHttpClientFactoryTest {

  @Test
  public void support_tls_versions_of_java8() {
    Logger logger = mock(Logger.class);
    OkHttpClient underTest = OkHttpClientFactory.create(logger);

    assertTlsAndClearTextSpecifications(underTest);
    assertThat(underTest.sslSocketFactory()).isInstanceOf(SSLSocketFactory.getDefault().getClass());
  }

  @Test
  public void support_custom_timeouts() {
    int connectTimeout = 1000;
    int readTimeout = 2000;
    System.setProperty("http.connection.timeout", String.valueOf(connectTimeout));
    System.setProperty("http.socket.timeout", String.valueOf(readTimeout));

    Logger logger = mock(Logger.class);
    OkHttpClient underTest = OkHttpClientFactory.create(logger);

    assertThat(underTest.connectTimeoutMillis()).isEqualTo(connectTimeout);
    assertThat(underTest.readTimeoutMillis()).isEqualTo(readTimeout);
  }

  private void assertTlsAndClearTextSpecifications(OkHttpClient client) {
    List<ConnectionSpec> connectionSpecs = client.connectionSpecs();
    assertThat(connectionSpecs).hasSize(2);

    // TLS. tlsVersions()==null means all TLS versions
    assertThat(connectionSpecs.get(0).tlsVersions()).isNull();
    assertThat(connectionSpecs.get(0).isTls()).isTrue();

    // HTTP
    assertThat(connectionSpecs.get(1).tlsVersions()).isNull();
    assertThat(connectionSpecs.get(1).isTls()).isFalse();
  }
}
