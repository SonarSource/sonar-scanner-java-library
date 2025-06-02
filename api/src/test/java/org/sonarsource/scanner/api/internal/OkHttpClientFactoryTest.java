/*
 * SonarQube Scanner API
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
package org.sonarsource.scanner.api.internal;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.theories.Theory;
import org.junit.rules.ExpectedException;
import org.sonarsource.scanner.api.internal.cache.Logger;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class OkHttpClientFactoryTest {

  private static final String KEYSTORE_CLIENT_WITH_CA = "/client-with-ca.p12";
  private static final String CLIENT_WITH_CA_KEYSTORE_PASSWORD = "pwdClientCAP12";

  private static final String KEYSTORE_CLIENT_WITH_CERTIFICATE = "/client-with-certificate.p12";
  private static final String CLIENT_WITH_CERTIFICATE_KEYSTORE_PASSWORD = "pwdClientP12";

  private static final String SERVER_KEYSTORE_PASSWORD = "pwdServerP12";
  private static final String SERVER_KEYSTORE_FILE = "/server.p12";
  private static final Logger logger = mock(Logger.class);
  private static final String SONAR_WS_TIMEOUT = "sonar.ws.timeout";
  private static final String COOKIE = "BIGipServerpool_sonarqube.example.com_8443=123456789.12345.0000";

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @After
  public void cleanSystemProperty() {
    System.clearProperty(SONAR_WS_TIMEOUT);
  }

  @Test
  public void support_custom_timeouts() {
    int readTimeoutSec = 2000;
    System.setProperty(SONAR_WS_TIMEOUT, String.valueOf(readTimeoutSec));

    Logger logger = mock(Logger.class);
    OkHttpClient underTest = OkHttpClientFactory.create(logger);

    assertThat(underTest.readTimeoutMillis()).isEqualTo(readTimeoutSec * 1000);
  }

  @Test
  public void support_custom_timeouts_throws_exception_on_non_number() {
    System.setProperty(SONAR_WS_TIMEOUT, "fail");

    Logger logger = mock(Logger.class);
    assertThatThrownBy(() -> OkHttpClientFactory.create(logger)).isInstanceOf(NumberFormatException.class);
  }

  @Test
  public void test_with_external_http_server() throws IOException {
    Response response = call("http://www.google.com");
    assertThat(response.code()).isEqualTo(200);
    assertThat(response.body().string()).contains("doctype html");
  }

  @Test
  public void test_with_external_https_server_with_correct_certificate() throws IOException {
    Response response = call("https://www.google.com");
    assertThat(response.code()).isEqualTo(200);
    assertThat(response.body().string()).contains("doctype html");
  }

  @Theory
  public void when_overriding_truststore_known_websites_are_failing(String clientKeyStore) throws IOException, URISyntaxException {
    try {
      Path clientTruststore = Paths.get(getClass().getResource(clientKeyStore).toURI()).toAbsolutePath();
      System.setProperty("javax.net.ssl.trustStore", clientTruststore.toString());
      System.setProperty("javax.net.ssl.trustStorePassword", SERVER_KEYSTORE_PASSWORD);

      expectedException.expect(SSLHandshakeException.class);
      call("https://www.google.com");
    } finally {
      // Ensure to not keeping this property for other tests
      System.clearProperty("javax.net.ssl.trustStore");
      System.clearProperty("javax.net.ssl.trustStorePassword");
    }
  }

  @Test
  public void test_with_custom_https_server_using_ca_in_truststore() throws Exception {
    test_with_custom_https_server(KEYSTORE_CLIENT_WITH_CA, CLIENT_WITH_CA_KEYSTORE_PASSWORD);
  }

  @Test
  public void test_with_custom_https_server_using_server_certificate_in_truststore() throws Exception {
    test_with_custom_https_server(KEYSTORE_CLIENT_WITH_CERTIFICATE, CLIENT_WITH_CERTIFICATE_KEYSTORE_PASSWORD);
  }

  private void test_with_custom_https_server(String clientKeyStore, String keyStorePassword) throws Exception {
    System.setProperty("javax.net.debug", "ssl,handshake,record");
    try (MockWebServer server = buildTLSServer()) {
      String url = format("https://localhost:%d/", server.getPort());

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
  public void test_with_cookie_using_ca_in_truststore() throws Exception {
    test_with_cookie(KEYSTORE_CLIENT_WITH_CA, CLIENT_WITH_CA_KEYSTORE_PASSWORD);
  }

  @Test
  public void test_with_cookie_using_server_certificate_in_truststore() throws Exception {
    test_with_cookie(KEYSTORE_CLIENT_WITH_CERTIFICATE, CLIENT_WITH_CERTIFICATE_KEYSTORE_PASSWORD);
  }

  private void test_with_cookie(String clientKeyStore, String keyStorePassword) throws Exception {
    try (MockWebServer server = buildTLSServer()) {
      String url = format("https://localhost:%d/", server.getPort());

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

  /**
   * Build a MockWebServer with a dispatcher always sending 200 response with OK
   * This webserver will use respond only to https protocol
   */
  private MockWebServer buildTLSServer() throws Exception {
    MockWebServer server = new MockWebServer();
    server.setDispatcher(new Dispatcher() {
      @Override
      public MockResponse dispatch(RecordedRequest request) {
        String responseBody = "OK";
        MockResponse response = new MockResponse().setResponseCode(200);
        String cookie = request.getHeader("Cookie");
        if (cookie == null || cookie.isEmpty()) {
          // Only set the cookie if it is not already set
          response.addHeader("Set-Cookie", COOKIE);
        } else {
          // dump the cookie into the response body to aid in test inspection
          responseBody += "\nCookie: " + cookie;
        }
        response.setBody(responseBody);
        return response;
      }
    });

    // JKS file storing the private key and TLS certificate
    Path serverCertificate = Paths.get(getClass().getResource(SERVER_KEYSTORE_FILE).toURI()).toAbsolutePath();

    // Load the KeyStore
    KeyStore serverKeyStore = KeyStore.getInstance("pkcs12");
    FileInputStream stream = new FileInputStream(serverCertificate.toFile());
    serverKeyStore.load(stream, SERVER_KEYSTORE_PASSWORD.toCharArray());

    // Load the KeyManager from the KeyStore
    String kmfAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(kmfAlgorithm);
    kmf.init(serverKeyStore, SERVER_KEYSTORE_PASSWORD.toCharArray());

    // Add the "Keys" (ie. private key and TLS certificate to the TrustManager
    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(kmfAlgorithm);
    trustManagerFactory.init(serverKeyStore);

    // Create the SocketFactory using the TrustManager so the private key and certificate
    SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
    sslContext.init(kmf.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());

    // Let's use it for the WebServer
    server.useHttps(sslContext.getSocketFactory(), false);

    return server;
  }
}
