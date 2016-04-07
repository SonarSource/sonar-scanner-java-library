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

import com.squareup.okhttp.ConnectionSpec;
import com.squareup.okhttp.OkHttpClient;
import java.util.List;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OkHttpClientFactoryTest {

  OkHttpClientFactory.JavaVersion javaVersion = mock(OkHttpClientFactory.JavaVersion.class);

  @Test
  public void support_tls_1_2_on_java7() {
    when(javaVersion.isJava7()).thenReturn(true);
    OkHttpClient underTest = OkHttpClientFactory.create(javaVersion);

    assertTlsAndClearTextSpecifications(underTest);
    // enable TLS 1.0, 1.1 and 1.2
    assertThat(underTest.getSslSocketFactory()).isNotNull();
  }

  @Test
  public void support_tls_versions_of_java8() {
    when(javaVersion.isJava7()).thenReturn(false);
    OkHttpClient underTest = OkHttpClientFactory.create(javaVersion);

    assertTlsAndClearTextSpecifications(underTest);
    assertThat(underTest.getSslSocketFactory()).isInstanceOf(SSLSocketFactory.getDefault().getClass());
  }

  private void assertTlsAndClearTextSpecifications(OkHttpClient client) {
    List<ConnectionSpec> connectionSpecs = client.getConnectionSpecs();
    assertThat(connectionSpecs).hasSize(2);

    // TLS. tlsVersions()==null means all TLS versions
    assertThat(connectionSpecs.get(0).tlsVersions()).isNull();
    assertThat(connectionSpecs.get(0).isTls()).isTrue();

    // HTTP
    assertThat(connectionSpecs.get(1).tlsVersions()).isNull();
    assertThat(connectionSpecs.get(1).isTls()).isFalse();
  }
}
