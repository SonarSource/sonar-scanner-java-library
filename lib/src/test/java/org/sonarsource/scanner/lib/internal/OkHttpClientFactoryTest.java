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
package org.sonarsource.scanner.lib.internal;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.net.ssl.SSLHandshakeException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.scanner.lib.internal.cache.Logger;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

class OkHttpClientFactoryTest {

  private static final String KEYSTORE_CLIENT_WITH_CA = "/client-with-ca.p12";
  private static final String CLIENT_WITH_CA_KEYSTORE_PASSWORD = "pwdClientCAP12";

  private static final String KEYSTORE_CLIENT_WITH_CERTIFICATE = "/client-with-certificate.p12";
  private static final String CLIENT_WITH_CERTIFICATE_KEYSTORE_PASSWORD = "pwdClientP12";

  private static final String SERVER_KEYSTORE_PASSWORD = "pwdServerP12";
  private static final String SERVER_KEYSTORE_FILE = "/server.p12";
  private static final Logger logger = mock(Logger.class);
  private static final String SONAR_WS_TIMEOUT = "sonar.ws.timeout";
  private static final String COOKIE = "BIGipServerpool_sonarqube.example.com_8443=123456789.12345.0000";

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicHttpsPort().httpDisabled(true).globalTemplating(true)
      .keystoreType("pkcs12")
      .keystorePath(toPath(requireNonNull(OkHttpClientFactoryTest.class.getResource(SERVER_KEYSTORE_FILE))).toString())
      .keystorePassword(SERVER_KEYSTORE_PASSWORD)
      .keyManagerPassword(SERVER_KEYSTORE_PASSWORD))
    .build();

  @BeforeEach
  void mockServerResponses() {
    sonarqubeMock.stubFor(get(anyUrl()).withHeader("Cookie", matching(".*")).atPriority(1)
      .willReturn(ok("OK\n{{request.headers.Cookie}}")));
    sonarqubeMock.stubFor(get(anyUrl()).atPriority(2)
      .willReturn(ok("OK").withHeader("Set-Cookie", COOKIE)));
  }

  @AfterEach
  public void cleanSystemProperty() {
    System.clearProperty(SONAR_WS_TIMEOUT);
  }

  @Test
  void support_custom_timeouts() {
    int readTimeoutSec = 2000;
    System.setProperty(SONAR_WS_TIMEOUT, String.valueOf(readTimeoutSec));

    OkHttpClient underTest = OkHttpClientFactory.create(logger);

    assertThat(underTest.readTimeoutMillis()).isEqualTo(readTimeoutSec * 1000);
  }

  @Test
  void support_custom_timeouts_throws_exception_on_non_number() {
    System.setProperty(SONAR_WS_TIMEOUT, "fail");

    assertThatThrownBy(() -> OkHttpClientFactory.create(logger)).isInstanceOf(NumberFormatException.class);
  }

  @Test
  void test_with_external_http_server() throws IOException {
    Response response = call("http://www.google.com");
    assertThat(response.code()).isEqualTo(200);
    assertThat(response.body().string()).contains("doctype html");
  }

  @Test
  void test_with_external_https_server_with_correct_certificate() throws IOException {
    Response response = call("https://www.google.com");
    assertThat(response.code()).isEqualTo(200);
    assertThat(response.body().string()).contains("doctype html");
  }

  @Test
  void when_overriding_truststore_using_ca_known_websites_are_failing() throws IOException, URISyntaxException {
    when_overriding_truststore_known_websites_are_failing(KEYSTORE_CLIENT_WITH_CA, CLIENT_WITH_CA_KEYSTORE_PASSWORD);
  }

  @Test
  void when_overriding_truststore_using_server_certificate_known_websites_are_failing() throws IOException, URISyntaxException {
    when_overriding_truststore_known_websites_are_failing(KEYSTORE_CLIENT_WITH_CERTIFICATE, CLIENT_WITH_CERTIFICATE_KEYSTORE_PASSWORD);
  }

  public void when_overriding_truststore_known_websites_are_failing(String clientKeyStore, String keyStorePassword) throws URISyntaxException {
    try {
      Path clientTruststore = Paths.get(getClass().getResource(clientKeyStore).toURI()).toAbsolutePath();
      System.setProperty("javax.net.ssl.trustStore", clientTruststore.toString());
      System.setProperty("javax.net.ssl.trustStorePassword", keyStorePassword);

      assertThrows(SSLHandshakeException.class, () -> call("https://www.google.com"));
    } finally {
      // Ensure to not keeping this property for other tests
      System.clearProperty("javax.net.ssl.trustStore");
      System.clearProperty("javax.net.ssl.trustStorePassword");
    }
  }

  @Test
  void test_with_custom_https_server_using_ca_in_truststore() throws Exception {
    test_with_custom_https_server(KEYSTORE_CLIENT_WITH_CA, CLIENT_WITH_CA_KEYSTORE_PASSWORD);
  }

  @Test
  void test_with_custom_https_server_using_server_certificate_in_truststore() throws Exception {
    test_with_custom_https_server(KEYSTORE_CLIENT_WITH_CERTIFICATE, CLIENT_WITH_CERTIFICATE_KEYSTORE_PASSWORD);
  }

  private void test_with_custom_https_server(String clientKeyStore, String keyStorePassword) throws Exception {
    System.setProperty("javax.net.debug", "ssl,handshake,record");
    try {
      String url = sonarqubeMock.baseUrl();

      // First test without any truststore is expecting to fail
      try {
        call(url);
        fail("Must have failed with an IOException");
      } catch (IOException e) {
        assertThat(e).hasMessageContaining("unable to find valid certification path to requested target");
      }

      // Add the truststore
      Path clientTruststore = Paths.get(getClass().getResource(clientKeyStore).toURI()).toAbsolutePath();
      System.setProperty("javax.net.ssl.trustStore", clientTruststore.toString());
      System.setProperty("javax.net.ssl.trustStorePassword", keyStorePassword);

      Response response = call(url);
      assertThat(response.code()).isEqualTo(200);
      assertThat(response.body().string()).contains("OK");
    } finally {
      // Ensure to not keeping this property for other tests
      System.clearProperty("javax.net.ssl.trustStore");
      System.clearProperty("javax.net.ssl.trustStorePassword");
    }
  }

  @Test
  void test_with_cookie_using_ca_in_truststore() throws Exception {
    test_with_cookie(KEYSTORE_CLIENT_WITH_CA, CLIENT_WITH_CA_KEYSTORE_PASSWORD);
  }

  @Test
  void test_with_cookie_using_server_certificate_in_truststore() throws Exception {
    test_with_cookie(KEYSTORE_CLIENT_WITH_CERTIFICATE, CLIENT_WITH_CERTIFICATE_KEYSTORE_PASSWORD);
  }

  private void test_with_cookie(String clientKeyStore, String keyStorePassword) throws Exception {
    try {
      String url = sonarqubeMock.baseUrl();

      // Add the truststore
      Path clientTruststore = Paths.get(getClass().getResource(clientKeyStore).toURI()).toAbsolutePath();
      System.setProperty("javax.net.ssl.trustStore", clientTruststore.toString());
      System.setProperty("javax.net.ssl.trustStorePassword", keyStorePassword);

      OkHttpClientFactory.COOKIE_MANAGER.getCookieStore().removeAll(); // Clear any existing cookies

      Response response = call(url);
      assertThat(response.header("Set-Cookie")).isEqualTo(COOKIE); // The server should have asked us to set a cookie
      assertThat(response.body().string()).doesNotContain(COOKIE);

      response = call(url);
      assertThat(response.body().string()).contains(COOKIE);

    } finally {
      // Ensure to not keeping this property for other tests
      System.clearProperty("javax.net.ssl.trustStore");
      System.clearProperty("javax.net.ssl.trustStorePassword");
    }
  }

  private static Response call(String url) throws IOException {
    return OkHttpClientFactory.create(logger).newCall(
      new Request.Builder()
        .url(url)
        .get()
        .build())
      .execute();
  }

  private static Path toPath(URL url) {
    try {
      return Paths.get(url.toURI());
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

}
