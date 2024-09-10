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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLHandshakeException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.RestoreSystemProperties;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OkHttpClientFactoryTest {

  private static final String SONAR_WS_TIMEOUT = "sonar.ws.timeout";
  private static final String COOKIE = "BIGipServerpool_sonarqube.example.com_8443=123456789.12345.0000";

  private final Map<String, String> bootstrapProperties = new HashMap<>();

  @TempDir
  private Path sonarUserHomeDir;
  private Path sonarUserHome;

  @BeforeEach
  void prepareMocks() {
    this.sonarUserHome = sonarUserHomeDir;
    bootstrapProperties.clear();
  }

  @Test
  void support_custom_timeouts() {
    int readTimeoutSec = 2000;

    OkHttpClient underTest = OkHttpClientFactory.create(Map.of(SONAR_WS_TIMEOUT, String.valueOf(readTimeoutSec)), sonarUserHome);

    assertThat(underTest.readTimeoutMillis()).isEqualTo(readTimeoutSec * 1000);
  }

  @Test
  void support_custom_timeouts_throws_exception_on_non_number() {
    var props = Map.of(SONAR_WS_TIMEOUT, "fail");
    assertThatThrownBy(() -> OkHttpClientFactory.create(props, sonarUserHome))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("sonar.scanner.socketTimeout is not a valid integer: fail");
  }

  @Nested
  // Workaround until we move to Java 17+ and can make Wiremock extension static
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

    @RestoreSystemProperties
    @Test
    void test_with_cookie() throws Exception {
      try {
        String url = sonarqubeMock.baseUrl();

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

  }

  @Nested
  // Workaround until we move to Java 17+ and can make Wiremock extension static
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
        .isInstanceOf(IOException.class)
        .hasStackTraceContaining("timeout");
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
        .isInstanceOf(IOException.class)
        .hasStackTraceContaining("timeout");
    }

    @Test
    void it_should_throw_if_invalid_proxy_port() {
      bootstrapProperties.put("sonar.host.url", sonarqubeMock.baseUrl());
      bootstrapProperties.put("sonar.scanner.proxyHost", "localhost");
      bootstrapProperties.put("sonar.scanner.proxyPort", "not_a_number");

      assertThatThrownBy(() -> OkHttpClientFactory.create(bootstrapProperties, sonarUserHome))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("sonar.scanner.proxyPort is not a valid integer: not_a_number");
    }

    @Nested
    // Workaround until we move to Java 17+ and can make Wiremock extension static
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WithProxy {

      private static final String PROXY_AUTH_ENABLED = "proxy-auth";

      @RegisterExtension
      WireMockExtension proxyMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

      @BeforeEach
      void configureMocks(TestInfo info) {
        if (info.getTags().contains(PROXY_AUTH_ENABLED)) {
          proxyMock.stubFor(get(urlMatching("/batch/.*"))
            .inScenario("Proxy Auth")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse()
              .withStatus(407)
              .withHeader("Proxy-Authenticate", "Basic realm=\"Access to the proxy\""))
            .willSetStateTo("Challenge returned"));
          proxyMock.stubFor(get(urlMatching("/batch/.*"))
            .inScenario("Proxy Auth")
            .whenScenarioStateIs("Challenge returned")
            .willReturn(aResponse().proxiedFrom(sonarqubeMock.baseUrl())));
        } else {
          proxyMock.stubFor(get(urlMatching("/batch/.*")).willReturn(aResponse().proxiedFrom(sonarqubeMock.baseUrl())));
        }
      }

      @Test
      void it_should_honor_scanner_proxy_settings() throws IOException {
        bootstrapProperties.put("sonar.host.url", sonarqubeMock.baseUrl());
        bootstrapProperties.put("sonar.scanner.proxyHost", "localhost");
        bootstrapProperties.put("sonar.scanner.proxyPort", "" + proxyMock.getPort());

        sonarqubeMock.stubFor(get("/batch/index")
          .willReturn(aResponse().withStatus(200).withBody("Success")));

        Response response = call(sonarqubeMock.url("/batch/index"));
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).contains("Success");

        proxyMock.verify(getRequestedFor(urlEqualTo("/batch/index")));
      }

      @Test
      @Tag(PROXY_AUTH_ENABLED)
      void it_should_honor_scanner_proxy_settings_with_auth() throws IOException {
        var proxyLogin = "proxyLogin";
        var proxyPassword = "proxyPassword";
        bootstrapProperties.put("sonar.host.url", sonarqubeMock.baseUrl());
        bootstrapProperties.put("sonar.scanner.proxyHost", "localhost");
        bootstrapProperties.put("sonar.scanner.proxyPort", "" + proxyMock.getPort());
        bootstrapProperties.put("sonar.scanner.proxyUser", proxyLogin);
        bootstrapProperties.put("sonar.scanner.proxyPassword", proxyPassword);

        sonarqubeMock.stubFor(get("/batch/index")
          .willReturn(aResponse().withStatus(200).withBody("Success")));

        Response response = call(sonarqubeMock.url("/batch/index"));
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).contains("Success");

        proxyMock.verify(getRequestedFor(urlEqualTo("/batch/index"))
          .withHeader("Proxy-Authorization", equalTo("Basic " + Base64.getEncoder().encodeToString((proxyLogin + ":" + proxyPassword).getBytes(StandardCharsets.UTF_8)))));

      }

      @Test
      @Tag(PROXY_AUTH_ENABLED)
      @RestoreSystemProperties
      void it_should_honor_old_jvm_proxy_auth_properties() throws IOException {
        var proxyLogin = "proxyLogin";
        var proxyPassword = "proxyPassword";
        bootstrapProperties.put("sonar.host.url", sonarqubeMock.baseUrl());
        bootstrapProperties.put("sonar.scanner.proxyHost", "localhost");
        bootstrapProperties.put("sonar.scanner.proxyPort", "" + proxyMock.getPort());
        System.setProperty("http.proxyUser", proxyLogin);
        System.setProperty("http.proxyPassword", proxyPassword);

        sonarqubeMock.stubFor(get("/batch/index")
          .willReturn(aResponse().withStatus(200).withBody("Success")));

        Response response = call(sonarqubeMock.url("/batch/index"));
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).contains("Success");

        proxyMock.verify(getRequestedFor(urlEqualTo("/batch/index"))
          .withHeader("Proxy-Authorization", equalTo("Basic " + Base64.getEncoder().encodeToString((proxyLogin + ":" + proxyPassword).getBytes(StandardCharsets.UTF_8)))));

      }
    }

  }

  @Nested
  // Workaround until we move to Java 17+ and can make Wiremock extension static
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class WithMockHttpsSonarQube {

    public static final String KEYSTORE_PWD = "pwdServerP12";

    @RegisterExtension
    WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicHttpsPort().httpDisabled(true)
        .keystoreType("pkcs12")
        .keystorePath(toPath(requireNonNull(OkHttpClientFactoryTest.class.getResource("/ssl/server.p12"))).toString())
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
    void it_should_trust_server_self_signed_certificate_when_certificate_is_in_truststore() throws IOException {
      bootstrapProperties.put("sonar.host.url", sonarqubeMock.baseUrl());
      bootstrapProperties.put("sonar.scanner.truststorePath", toPath(requireNonNull(OkHttpClientFactoryTest.class.getResource("/ssl/client-truststore.p12"))).toString());
      bootstrapProperties.put("sonar.scanner.truststorePassword", "pwdClientWithServerCA");

      Response response = call(sonarqubeMock.url("/batch/index"));
      assertThat(response.code()).isEqualTo(200);
      assertThat(response.body().string()).contains("Success");
    }
  }

  @Nested
  // Workaround until we move to Java 17+ and can make Wiremock extension static
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class WithMockHttpsSonarQubeAndClientCertificates {

    public static final String KEYSTORE_PWD = "pwdServerP12";

    @RegisterExtension
    WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicHttpsPort().httpDisabled(true)
        .keystoreType("pkcs12")
        .keystorePath(toPath(requireNonNull(OkHttpClientFactoryTest.class.getResource("/ssl/server.p12"))).toString())
        .keystorePassword(KEYSTORE_PWD)
        .keyManagerPassword(KEYSTORE_PWD)
        .needClientAuth(true)
        .trustStoreType("pkcs12")
        .trustStorePath(toPath(requireNonNull(OkHttpClientFactoryTest.class.getResource("/ssl/server-with-client-ca.p12"))).toString())
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
      bootstrapProperties.put("sonar.scanner.truststorePath", toPath(requireNonNull(OkHttpClientFactoryTest.class.getResource("/ssl/client-truststore.p12"))).toString());
      bootstrapProperties.put("sonar.scanner.truststorePassword", "pwdClientWithServerCA");

      assertThatThrownBy(() -> call(sonarqubeMock.url("/batch/index")))
        .isInstanceOf(Exception.class)
        .satisfiesAnyOf(
          e -> assertThat(e).hasStackTraceContaining("SSLHandshakeException"),
          // Exception is flaky because of https://bugs.openjdk.org/browse/JDK-8172163
          e -> assertThat(e).hasStackTraceContaining("Broken pipe"));
    }

    @Test
    void it_should_authenticate_using_certificate_in_keystore() throws IOException {
      bootstrapProperties.put("sonar.host.url", sonarqubeMock.baseUrl());

      bootstrapProperties.put("sonar.scanner.truststorePath", toPath(requireNonNull(OkHttpClientFactoryTest.class.getResource("/ssl/client-truststore.p12"))).toString());
      bootstrapProperties.put("sonar.scanner.truststorePassword", "pwdClientWithServerCA");
      bootstrapProperties.put("sonar.scanner.keystorePath", toPath(requireNonNull(OkHttpClientFactoryTest.class.getResource("/ssl/client.p12"))).toString());
      bootstrapProperties.put("sonar.scanner.keystorePassword", "pwdClientCertP12");

      Response response = call(sonarqubeMock.url("/batch/index"));
      assertThat(response.code()).isEqualTo(200);
      assertThat(response.body().string()).contains("Success");
    }

    @RestoreSystemProperties
    @Test
    void it_should_support_jvm_system_properties() throws IOException {
      bootstrapProperties.put("sonar.host.url", sonarqubeMock.baseUrl());
      System.setProperty("javax.net.ssl.trustStore", toPath(requireNonNull(OkHttpClientFactoryTest.class.getResource("/ssl/client-truststore.p12"))).toString());
      System.setProperty("javax.net.ssl.trustStorePassword", "pwdClientWithServerCA");
      System.setProperty("javax.net.ssl.keyStore", toPath(requireNonNull(OkHttpClientFactoryTest.class.getResource("/ssl/client.p12"))).toString());
      System.setProperty("javax.net.ssl.keyStorePassword", "pwdClientCertP12");

      Response response = call(sonarqubeMock.url("/batch/index"));
      assertThat(response.code()).isEqualTo(200);
      assertThat(response.body().string()).contains("Success");
    }
  }

  private Response call(String url) throws IOException {
    return OkHttpClientFactory.create(bootstrapProperties, sonarUserHome).newCall(
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
