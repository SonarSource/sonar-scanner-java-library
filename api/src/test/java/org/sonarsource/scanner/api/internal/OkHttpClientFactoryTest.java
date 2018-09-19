/*
 * SonarQube Scanner API
 * Copyright (C) 2011-2018 SonarSource SA
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
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.sonarsource.scanner.api.internal.cache.Logger;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;

@RunWith(Theories.class)
public class OkHttpClientFactoryTest {

  @DataPoint
  public static final String KEYSTORE_CLIENT_WITH_CA = "/client-with-ca.jks";
  @DataPoint
  public static final String KEYSTORE_CLIENT_WITH_CERTIFICATE = "/client-with-certificate.jks";

  private static final String KEYSTORE_PASSWORD = "abcdef";
  private static final String KEYSTORE_FILE = "/server.jks";
  private static final Logger logger = mock(Logger.class);

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void support_tls_versions_of_java8() {
    OkHttpClient underTest = OkHttpClientFactory.create(logger);

    assertTlsAndClearTextSpecifications(underTest);
    assertThat(underTest.sslSocketFactory()).isInstanceOf(SSLSocketFactory.getDefault().getClass());
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
      System.setProperty("javax.net.ssl.trustStorePassword", KEYSTORE_PASSWORD);

      expectedException.expect(SSLHandshakeException.class);
      call("https://www.google.com");
    } finally {
      // Ensure to not keeping this property for other tests
      System.clearProperty("javax.net.ssl.trustStore");
      System.clearProperty("javax.net.ssl.trustStorePassword");
    }
  }

  @Theory
  public void test_with_custom_https_server(String clientKeyStore) throws Exception {
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
      System.setProperty("javax.net.ssl.trustStorePassword", KEYSTORE_PASSWORD);

      Response response = call(url);
      assertThat(response.code()).isEqualTo(200);
      assertThat(response.body().string()).contains("OK");
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
        return new MockResponse().setResponseCode(200).setBody("OK");
      }
    });

    // JKS file storing the private key and TLS certificate
    Path serverCertificate = Paths.get(getClass().getResource(KEYSTORE_FILE).toURI()).toAbsolutePath();

    // Load the KeyStore
    KeyStore serverKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    FileInputStream stream = new FileInputStream(serverCertificate.toFile());
    serverKeyStore.load(stream, KEYSTORE_PASSWORD.toCharArray());

    // Load the KeyManager from the KeyStore
    String kmfAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(kmfAlgorithm);
    kmf.init(serverKeyStore, "".toCharArray());

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
