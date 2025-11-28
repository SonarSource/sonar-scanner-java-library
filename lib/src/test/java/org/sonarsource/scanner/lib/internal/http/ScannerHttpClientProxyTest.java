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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.RestoreSystemProperties;
import org.sonarsource.scanner.lib.ScannerProperties;
import org.sonarsource.scanner.lib.internal.InternalProperties;
import org.sonarsource.scanner.lib.internal.util.System2;
import testutils.LogTester;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

class ScannerHttpClientProxyTest {

  // Used to set log level to INFO
  @RegisterExtension
  private final LogTester logTester = new LogTester();

  @TempDir
  private Path sonarUserHome;

  private static final String HELLO_WORLD = "hello, world!";

  @RegisterExtension
  static WireMockExtension sonarqube = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  private static final String PROXY_AUTH_ENABLED = "proxy-auth";

  @RegisterExtension
  static WireMockExtension proxyMock = WireMockExtension.newInstance()
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
        .willReturn(aResponse().proxiedFrom(sonarqube.baseUrl())));
    } else {
      proxyMock.stubFor(get(urlMatching("/batch/.*")).willReturn(aResponse().proxiedFrom(sonarqube.baseUrl())));
    }
  }

  @Test
  void should_honor_scanner_proxy_settings() {
    sonarqube.stubFor(get("/batch/index.txt")
      .willReturn(aResponse().withBody(HELLO_WORLD)));

    Map<String, String> props = new HashMap<>();
    props.put(ScannerProperties.SONAR_SCANNER_PROXY_HOST, "localhost");
    props.put(ScannerProperties.SONAR_SCANNER_PROXY_PORT, String.valueOf(proxyMock.getPort()));

    ScannerHttpClient underTest = create(sonarqube.baseUrl(), props);
    String response = underTest.callWebApi("/batch/index.txt");

    assertThat(response).isEqualTo(HELLO_WORLD);
    proxyMock.verify(getRequestedFor(urlMatching("/batch/.*")));
  }

  @Test
  @Tag(PROXY_AUTH_ENABLED)
  void should_honor_scanner_proxy_settings_with_auth() {
    sonarqube.stubFor(get("/batch/index.txt")
      .willReturn(aResponse().withBody(HELLO_WORLD)));

    Map<String, String> props = new HashMap<>();
    props.put(ScannerProperties.SONAR_SCANNER_PROXY_HOST, "localhost");
    props.put(ScannerProperties.SONAR_SCANNER_PROXY_PORT, String.valueOf(proxyMock.getPort()));
    props.put(ScannerProperties.SONAR_SCANNER_PROXY_USER, "proxyUser");
    props.put(ScannerProperties.SONAR_SCANNER_PROXY_PASSWORD, "proxyPassword");

    ScannerHttpClient underTest = create(sonarqube.baseUrl(), props);
    String response = underTest.callWebApi("/batch/index.txt");

    assertThat(response).isEqualTo(HELLO_WORLD);
    proxyMock.verify(getRequestedFor(urlMatching("/batch/.*"))
      .withHeader("Proxy-Authorization", equalTo("Basic " + Base64.getEncoder().encodeToString("proxyUser:proxyPassword".getBytes(StandardCharsets.UTF_8)))));
  }

  @Test
  @Tag(PROXY_AUTH_ENABLED)
  @RestoreSystemProperties
  void should_honor_old_jvm_proxy_auth_properties() {
    System.setProperty("http.proxyUser", "proxyUser");
    System.setProperty("http.proxyPassword", "proxyPassword");

    sonarqube.stubFor(get("/batch/index.txt")
      .willReturn(aResponse().withBody(HELLO_WORLD)));

    Map<String, String> props = new HashMap<>();
    props.put(ScannerProperties.SONAR_SCANNER_PROXY_HOST, "localhost");
    props.put(ScannerProperties.SONAR_SCANNER_PROXY_PORT, String.valueOf(proxyMock.getPort()));

    ScannerHttpClient underTest = create(sonarqube.baseUrl(), props);
    String response = underTest.callWebApi("/batch/index.txt");

    assertThat(response).isEqualTo(HELLO_WORLD);
    proxyMock.verify(getRequestedFor(urlMatching("/batch/.*"))
      .withHeader("Proxy-Authorization", equalTo("Basic " + Base64.getEncoder().encodeToString("proxyUser:proxyPassword".getBytes(StandardCharsets.UTF_8)))));
  }

  @Test
  @Tag(PROXY_AUTH_ENABLED)
  void should_preserve_token_authentication_with_proxy_auth() {
    sonarqube.stubFor(get("/batch/index.txt")
      .willReturn(aResponse().withBody(HELLO_WORLD)));

    Map<String, String> props = new HashMap<>();
    props.put(ScannerProperties.SONAR_SCANNER_PROXY_HOST, "localhost");
    props.put(ScannerProperties.SONAR_SCANNER_PROXY_PORT, String.valueOf(proxyMock.getPort()));
    props.put(ScannerProperties.SONAR_SCANNER_PROXY_USER, "proxyUser");
    props.put(ScannerProperties.SONAR_SCANNER_PROXY_PASSWORD, "proxyPassword");
    props.put("sonar.token", "some_token");

    ScannerHttpClient underTest = create(sonarqube.baseUrl(), props);
    String response = underTest.callWebApi("/batch/index.txt");

    assertThat(response).isEqualTo(HELLO_WORLD);
    proxyMock.verify(getRequestedFor(urlMatching("/batch/.*"))
      .withHeader("Proxy-Authorization", equalTo("Basic " + Base64.getEncoder().encodeToString("proxyUser:proxyPassword".getBytes(StandardCharsets.UTF_8)))));
    sonarqube.verify(getRequestedFor(urlMatching("/batch/.*"))
      .withHeader("Authorization", equalTo("Bearer some_token")));
  }

  @Test
  @Tag(PROXY_AUTH_ENABLED)
  void should_preserve_username_password_authentication_with_proxy_auth() {
    sonarqube.stubFor(get("/batch/index.txt")
      .willReturn(aResponse().withBody(HELLO_WORLD)));

    Map<String, String> props = new HashMap<>();
    props.put(ScannerProperties.SONAR_SCANNER_PROXY_HOST, "localhost");
    props.put(ScannerProperties.SONAR_SCANNER_PROXY_PORT, String.valueOf(proxyMock.getPort()));
    props.put(ScannerProperties.SONAR_SCANNER_PROXY_USER, "proxyUser");
    props.put(ScannerProperties.SONAR_SCANNER_PROXY_PASSWORD, "proxyPassword");
    props.put("sonar.login", "some_username");
    props.put("sonar.password", "some_password");

    ScannerHttpClient underTest = create(sonarqube.baseUrl(), props);
    String response = underTest.callWebApi("/batch/index.txt");

    assertThat(response).isEqualTo(HELLO_WORLD);
    proxyMock.verify(getRequestedFor(urlMatching("/batch/.*"))
      .withHeader("Proxy-Authorization", equalTo("Basic " + Base64.getEncoder().encodeToString("proxyUser:proxyPassword".getBytes(StandardCharsets.UTF_8)))));
    sonarqube.verify(getRequestedFor(urlMatching("/batch/.*"))
      .withHeader("Authorization", equalTo("Basic " + Base64.getEncoder().encodeToString("some_username:some_password".getBytes(StandardCharsets.UTF_8)))));
  }

  @Test
  void should_preserve_token_authentication_with_proxy_without_auth() {
    sonarqube.stubFor(get("/batch/index.txt")
      .willReturn(aResponse().withBody(HELLO_WORLD)));

    Map<String, String> props = new HashMap<>();
    props.put(ScannerProperties.SONAR_SCANNER_PROXY_HOST, "localhost");
    props.put(ScannerProperties.SONAR_SCANNER_PROXY_PORT, String.valueOf(proxyMock.getPort()));
    props.put("sonar.token", "some_token");

    ScannerHttpClient underTest = create(sonarqube.baseUrl(), props);
    String response = underTest.callWebApi("/batch/index.txt");

    assertThat(response).isEqualTo(HELLO_WORLD);
    proxyMock.verify(getRequestedFor(urlMatching("/batch/.*")));
    sonarqube.verify(getRequestedFor(urlMatching("/batch/.*"))
      .withHeader("Authorization", equalTo("Bearer some_token")));
  }

  private ScannerHttpClient create(String url, Map<String, String> additionalProps) {
    Map<String, String> props = new HashMap<>();
    props.put(ScannerProperties.HOST_URL, url);
    props.put(ScannerProperties.API_BASE_URL, url);
    props.put(InternalProperties.SCANNER_APP, "user");
    props.put(InternalProperties.SCANNER_APP_VERSION, "agent");
    props.putAll(additionalProps);

    ScannerHttpClient connection = new ScannerHttpClient();
    connection.init(new HttpConfig(props, sonarUserHome, new System2()));
    return connection;
  }

}
