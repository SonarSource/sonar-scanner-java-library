/*
 * SonarScanner Java Library
 * Copyright (C) SonarSource Sàrl
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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.net.ssl.SSLHandshakeException;
import nl.altindag.ssl.exception.GenericKeyStoreException;
import nl.altindag.ssl.exception.GenericSecurityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.event.Level;
import org.sonarsource.scanner.lib.internal.http.ssl.DerBuilder;
import org.sonarsource.scanner.lib.internal.http.ssl.Pkcs12CertificateReader;
import org.sonarsource.scanner.lib.internal.util.System2;
import testutils.LogTester;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpClientFactoryTest {

  private static final String COOKIE = "BIGipServerpool_sonarqube.example.com_8443=123456789.12345.0000";

  private final Map<String, String> bootstrapProperties = new HashMap<>();

  @RegisterExtension
  private final LogTester logTester = new LogTester();

  @TempDir
  private Path sonarUserHomeDir;
  private Path sonarUserHome;
  private final System2 system2 = mock();

  @BeforeEach
  void prepareMocks() {
    this.sonarUserHome = sonarUserHomeDir;
    bootstrapProperties.clear();
    when(system2.getProperty("java.home")).thenReturn(System.getProperty("java.home"));
  }

  @ParameterizedTest
  @CsvSource({
    "keystore_changeit.p12,   wrong,        false",
    "keystore_changeit.p12,   changeit,     true",
    "keystore_changeit.p12,,                true",
    "keystore_sonar.p12,      wrong,        false",
    "keystore_sonar.p12,      sonar,        true",
    "keystore_sonar.p12,,                   true",
    "keystore_anotherpwd.p12, wrong,        false",
    "keystore_anotherpwd.p12, anotherpwd,   true",
    "keystore_anotherpwd.p12,,              false",
    "keystore_emptypwd.p12,   wrong,        true",
    "keystore_emptypwd.p12,,                true"})
  void it_should_fail_if_invalid_truststore_password(String keystore, @Nullable String password, boolean shouldSucceed) {
    bootstrapProperties.put("sonar.scanner.truststorePath", toPath(requireNonNull(HttpClientFactoryTest.class.getResource("/ssl/" + keystore))).toString());
    if (password != null) {
      bootstrapProperties.put("sonar.scanner.truststorePassword", password);
    }

    var httpConfig = new HttpConfig(bootstrapProperties, sonarUserHome, system2);
    if (shouldSucceed) {
      assertThatNoException().isThrownBy(() -> HttpClientFactory.create(httpConfig));
    } else {
      assertThatThrownBy(() -> HttpClientFactory.create(httpConfig))
        .isInstanceOf(GenericKeyStoreException.class)
        .hasMessageContaining("Unable to read truststore from")
        .hasStackTraceContaining("password");
    }
  }

  @ParameterizedTest
  @CsvSource({
    "keystore_changeit.p12,   wrong,        false",
    "keystore_changeit.p12,   changeit,     true",
    "keystore_changeit.p12,,                true",
    "keystore_sonar.p12,      wrong,        false",
    "keystore_sonar.p12,      sonar,        true",
    "keystore_sonar.p12,,                   true",
    "keystore_anotherpwd.p12, wrong,        false",
    "keystore_anotherpwd.p12, anotherpwd,   true",
    "keystore_anotherpwd.p12,,              false",
    "keystore_emptypwd.p12,   wrong,        true",
    "keystore_emptypwd.p12,,                true"})
  void it_should_fail_if_invalid_keystore_password(String keystore, @Nullable String password, boolean shouldSucceed) {
    bootstrapProperties.put("sonar.scanner.keystorePath", toPath(requireNonNull(HttpClientFactoryTest.class.getResource("/ssl/" + keystore))).toString());
    if (password != null) {
      bootstrapProperties.put("sonar.scanner.keystorePassword", password);
    }

    var httpConfig = new HttpConfig(bootstrapProperties, sonarUserHome, system2);
    if (shouldSucceed) {
      assertThatNoException().isThrownBy(() -> HttpClientFactory.create(httpConfig));
    } else {
      assertThatThrownBy(() -> HttpClientFactory.create(httpConfig))
        .isInstanceOf(GenericSecurityException.class)
        .hasMessageContaining("keystore password was incorrect");
    }
  }

  @Test
  void should_load_os_certificates_by_default() {
    logTester.setLevel(Level.DEBUG);

    HttpClientFactory.create(new HttpConfig(bootstrapProperties, sonarUserHome, system2));

    assertThat(logTester.logs(Level.DEBUG)).contains("Loading OS trusted SSL certificates...");
  }

  @Test
  void should_skip_load_of_os_certificates_if_props_set() {
    logTester.setLevel(Level.DEBUG);
    bootstrapProperties.put("sonar.scanner.skipSystemTruststore", "true");

    HttpClientFactory.create(new HttpConfig(bootstrapProperties, sonarUserHome, system2));

    assertThat(logTester.logs(Level.DEBUG)).doesNotContain("Loading OS trusted SSL certificates...");
  }

  @Test
  void it_should_not_retry_deprecated_default_password_when_truststore_is_from_jvm() {
    when(system2.getProperty("javax.net.ssl.trustStore"))
      .thenReturn(toPath(requireNonNull(HttpClientFactoryTest.class.getResource("/ssl/keystore_anotherpwd.p12"))).toString());

    var httpConfig = new HttpConfig(bootstrapProperties, sonarUserHome, system2);

    assertThatThrownBy(() -> HttpClientFactory.create(httpConfig))
      .isInstanceOf(GenericKeyStoreException.class);
  }

  @Test
  void it_should_keep_empty_result_when_fallback_reader_also_fails_to_parse_truststore() throws Exception {
    logTester.setLevel(Level.DEBUG);
    var password = "test-password";
    var path = sonarUserHomeDir.resolve("non-x509-cert-type.p12");
    Files.write(path, buildTruststoreWithNonX509CertType());

    var keyStore = HttpClientFactory.loadTrustStore(path, password, "PKCS12", false);

    assertThat(keyStore.size()).isZero();
    assertThat(logTester.logs(Level.DEBUG)).anyMatch(log -> log.contains("Manual PKCS12 parsing failed"));
  }

  @Test
  void it_should_keep_empty_result_when_fallback_reader_also_finds_no_certificates() throws Exception {
    var path = sonarUserHomeDir.resolve("no-certificates.p12");
    var emptySafeContents = DerBuilder.sequence();
    var dataContentInfo = DerBuilder.sequence(DerBuilder.oid("1.2.840.113549.1.7.1"), DerBuilder.explicit(0, DerBuilder.octetString(emptySafeContents)));
    var authenticatedSafe = DerBuilder.sequence(dataContentInfo);
    var authSafeContentInfo = DerBuilder.sequence(DerBuilder.oid("1.2.840.113549.1.7.1"), DerBuilder.explicit(0, DerBuilder.octetString(authenticatedSafe)));
    Files.write(path, DerBuilder.sequence(DerBuilder.integer(3), authSafeContentInfo));

    var keyStore = HttpClientFactory.loadTrustStore(path, "test-password", "PKCS12", false);

    assertThat(keyStore.size()).isZero();
  }

  /**
   * Native JDK load succeeds but returns no entries here too, since a non-X.509 certType is not a
   * certificate-only bag it recognizes as trusted. {@link Pkcs12CertificateReader} (backed by BC's
   * own PKCS12KeyStoreSpi) is stricter on this specific point and rejects the whole file outright,
   * so the fallback reader also fails, exercising HttpClientFactory's catch-and-swallow path.
   */
  private static byte[] buildTruststoreWithNonX509CertType() throws Exception {
    var cert = (X509Certificate) CertificateFactory.getInstance("X.509")
      .generateCertificate(Files.newInputStream(toPath(requireNonNull(HttpClientFactoryTest.class.getResource("/ssl/ca.crt")))));
    var certBag = DerBuilder.sequence(DerBuilder.oid("1.2.840.113549.1.9.22.2"), DerBuilder.explicit(0, DerBuilder.octetString(cert.getEncoded())));
    var safeBag = DerBuilder.sequence(DerBuilder.oid("1.2.840.113549.1.12.10.1.3"), DerBuilder.explicit(0, certBag));
    var safeContents = DerBuilder.sequence(safeBag);

    var dataContentInfo = DerBuilder.sequence(DerBuilder.oid("1.2.840.113549.1.7.1"), DerBuilder.explicit(0, DerBuilder.octetString(safeContents)));
    var authenticatedSafe = DerBuilder.sequence(dataContentInfo);
    var authSafeContentInfo = DerBuilder.sequence(DerBuilder.oid("1.2.840.113549.1.7.1"), DerBuilder.explicit(0, DerBuilder.octetString(authenticatedSafe)));
    return DerBuilder.sequence(DerBuilder.integer(3), authSafeContentInfo);
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class WithMockHttpSonarQubeForCookies {

    @RegisterExtension
    WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
      .options(wireMockConfig().globalTemplating(true))
      .build();

    @BeforeEach
    void mockServerResponses() {
      sonarqubeMock.stubFor(get(anyUrl()).withHeader("Cookie", matching(".*")).atPriority(1)
        .willReturn(ok("OK\n{{request.headers.Cookie}}")));
      sonarqubeMock.stubFor(get(anyUrl()).atPriority(2)
        .willReturn(ok("OK").withHeader("Set-Cookie", COOKIE)));
    }

    @Test
    void test_with_cookie() throws Exception {
      try {
        String url = sonarqubeMock.baseUrl();

        HttpClientFactory.COOKIE_MANAGER.getCookieStore().removeAll();

        HttpResponse<String> response = call(url);
        String setCookieHeader = response.headers().firstValue("Set-Cookie").orElse(null);
        assertThat(setCookieHeader).isEqualTo(COOKIE);
        assertThat(response.body()).doesNotContain(COOKIE);

        response = call(url);
        assertThat(response.body()).contains(COOKIE);

        HttpClientFactory.COOKIE_MANAGER.getCookieStore().removeAll();

        response = call(url);
        assertThat(response.body()).doesNotContain(COOKIE);
      } finally {
        System.clearProperty("javax.net.ssl.trustStore");
        System.clearProperty("javax.net.ssl.trustStorePassword");
      }
    }

  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class WithMockHttpSonarQube {

    @RegisterExtension
    WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort())
      .build();

    @Test
    void it_should_timeout_on_long_response() {
      bootstrapProperties.put("sonar.host.url", sonarqubeMock.baseUrl());
      bootstrapProperties.put("sonar.scanner.responseTimeout", "PT0.2S");

      sonarqubeMock.stubFor(get("/batch/index")
        .willReturn(aResponse().withStatus(200)
          .withFixedDelay(2000)
          .withBody("Success")));

      assertThatThrownBy(() -> call(sonarqubeMock.url("/batch/index")))
        .satisfiesAnyOf(
          e -> assertThat(e).isInstanceOf(java.net.http.HttpTimeoutException.class),
          e -> assertThat(e).hasStackTraceContaining("timeout"));
    }

    @Test
    void it_should_timeout_on_slow_response() {
      bootstrapProperties.put("sonar.host.url", sonarqubeMock.baseUrl());
      bootstrapProperties.put("sonar.scanner.socketTimeout", "PT0.2S");

      sonarqubeMock.stubFor(get("/batch/index")
        .willReturn(aResponse().withStatus(200)
          .withChunkedDribbleDelay(2, 2000)
          .withBody("Success")));

      assertThatThrownBy(() -> call(sonarqubeMock.url("/batch/index")))
        .satisfiesAnyOf(
          e -> assertThat(e).isInstanceOf(java.net.http.HttpTimeoutException.class),
          e -> assertThat(e).hasStackTraceContaining("timeout"));
    }

  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class WithMockHttpsSonarQube {

    public static final String KEYSTORE_PWD = "pwdServerP12";

    @RegisterExtension
    WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicHttpsPort().httpDisabled(true)
        .keystoreType("pkcs12")
        .keystorePath(toPath(requireNonNull(HttpClientFactoryTest.class.getResource("/ssl/server.p12"))).toString())
        .keystorePassword(KEYSTORE_PWD)
        .keyManagerPassword(KEYSTORE_PWD))
      .build();

    @BeforeEach
    void mockResponse() {
      sonarqubeMock.stubFor(get("/batch/index")
        .willReturn(aResponse().withStatus(200).withBody("Success")));
    }

    @Test
    void it_should_not_trust_server_self_signed_certificate_by_default() {
      bootstrapProperties.put("sonar.host.url", sonarqubeMock.baseUrl());

      assertThatThrownBy(() -> call(sonarqubeMock.url("/batch/index")))
        .isInstanceOf(SSLHandshakeException.class)
        .hasStackTraceContaining("CertificateException");
    }

    @Test
    void it_should_trust_server_self_signed_certificate_when_certificate_is_in_truststore() throws IOException, InterruptedException {
      bootstrapProperties.put("sonar.host.url", sonarqubeMock.baseUrl());
      bootstrapProperties.put("sonar.scanner.truststorePath", toPath(requireNonNull(HttpClientFactoryTest.class.getResource("/ssl/client-truststore.p12"))).toString());
      bootstrapProperties.put("sonar.scanner.truststorePassword", "pwdClientWithServerCA");

      HttpResponse<String> response = call(sonarqubeMock.url("/batch/index"));
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("Success");
    }

    @Test
    void it_should_trust_server_self_signed_certificate_when_truststore_is_openssl_generated_cert_only() throws IOException, InterruptedException {
      logTester.setLevel(Level.DEBUG);
      bootstrapProperties.put("sonar.host.url", sonarqubeMock.baseUrl());
      bootstrapProperties.put("sonar.scanner.truststorePath", toPath(requireNonNull(HttpClientFactoryTest.class.getResource("/ssl/truststore-openssl-cert-only.p12"))).toString());
      bootstrapProperties.put("sonar.scanner.truststorePassword", "pwdOpenssl12");

      HttpResponse<String> response = call(sonarqubeMock.url("/batch/index"));

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("Success");
      assertThat(logTester.logs(Level.DEBUG)).anyMatch(log -> log.contains("falling back to manual PKCS12 parsing"));
    }

    @Test
    void it_should_not_use_the_fallback_reader_for_a_keytool_generated_truststore() throws IOException, InterruptedException {
      logTester.setLevel(Level.DEBUG);
      bootstrapProperties.put("sonar.host.url", sonarqubeMock.baseUrl());
      bootstrapProperties.put("sonar.scanner.truststorePath", toPath(requireNonNull(HttpClientFactoryTest.class.getResource("/ssl/client-truststore.p12"))).toString());
      bootstrapProperties.put("sonar.scanner.truststorePassword", "pwdClientWithServerCA");

      call(sonarqubeMock.url("/batch/index"));

      assertThat(logTester.logs(Level.DEBUG)).noneMatch(log -> log.contains("falling back to manual PKCS12 parsing"));
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class WithMockHttpsSonarQubeAndClientCertificates {

    public static final String KEYSTORE_PWD = "pwdServerP12";

    @RegisterExtension
    WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicHttpsPort().httpDisabled(true)
        .keystoreType("pkcs12")
        .keystorePath(toPath(requireNonNull(HttpClientFactoryTest.class.getResource("/ssl/server.p12"))).toString())
        .keystorePassword(KEYSTORE_PWD)
        .keyManagerPassword(KEYSTORE_PWD)
        .needClientAuth(true)
        .trustStoreType("pkcs12")
        .trustStorePath(toPath(requireNonNull(HttpClientFactoryTest.class.getResource("/ssl/server-with-client-ca.p12"))).toString())
        .trustStorePassword("pwdServerWithClientCA"))
      .build();

    @BeforeEach
    void mockResponse() {
      sonarqubeMock.stubFor(get("/batch/index")
        .willReturn(aResponse().withStatus(200).withBody("Success")));
    }

    @Test
    void it_should_fail_if_client_certificate_not_provided() {
      bootstrapProperties.put("sonar.host.url", sonarqubeMock.baseUrl());
      bootstrapProperties.put("sonar.scanner.truststorePath", toPath(requireNonNull(HttpClientFactoryTest.class.getResource("/ssl/client-truststore.p12"))).toString());
      bootstrapProperties.put("sonar.scanner.truststorePassword", "pwdClientWithServerCA");

      assertThatThrownBy(() -> call(sonarqubeMock.url("/batch/index")))
        .isInstanceOf(Exception.class)
        .satisfiesAnyOf(
          e -> assertThat(e).hasStackTraceContaining("SSLHandshakeException"),
          e -> assertThat(e).hasStackTraceContaining("Broken pipe"));
    }

    @Test
    void it_should_authenticate_using_certificate_in_keystore() throws IOException, InterruptedException {
      bootstrapProperties.put("sonar.host.url", sonarqubeMock.baseUrl());

      bootstrapProperties.put("sonar.scanner.truststorePath", toPath(requireNonNull(HttpClientFactoryTest.class.getResource("/ssl/client-truststore.p12"))).toString());
      bootstrapProperties.put("sonar.scanner.truststorePassword", "pwdClientWithServerCA");
      bootstrapProperties.put("sonar.scanner.keystorePath", toPath(requireNonNull(HttpClientFactoryTest.class.getResource("/ssl/client.p12"))).toString());
      bootstrapProperties.put("sonar.scanner.keystorePassword", "pwdClientCertP12");

      HttpResponse<String> response = call(sonarqubeMock.url("/batch/index"));
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("Success");
    }

  }

  private HttpResponse<String> call(String url) throws IOException, InterruptedException {
    HttpConfig config = new HttpConfig(bootstrapProperties, sonarUserHome, system2);
    HttpClient client = HttpClientFactory.create(config);
    var timeout = config.getResponseTimeout().isZero() ? config.getSocketTimeout() : config.getResponseTimeout();
    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .GET()
      .timeout(timeout)
      .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static Path toPath(URL url) {
    try {
      return Paths.get(url.toURI());
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

}
