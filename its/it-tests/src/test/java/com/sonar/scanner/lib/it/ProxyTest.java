/*
 * SonarScanner Java Library - ITs
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
package com.sonar.scanner.lib.it;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.junit4.OrchestratorRule;
import com.sonar.scanner.lib.it.tools.SimpleScanner;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;

public class ProxyTest {

  private static final String PROXY_USER = "scott";
  private static final String PROXY_PASSWORD = "tiger";

  // SSL resources reused from SSLTest
  private static final String SERVER_KEYSTORE = "/SSLTest/server.p12";
  private static final String SERVER_KEYSTORE_PASSWORD = "pwdServerP12";
  private static final String KEYSTORE_CLIENT_WITH_CA = "/SSLTest/client-with-ca-keytool.p12";
  private static final String KEYSTORE_CLIENT_WITH_CA_PASSWORD = "pwdClientCAP12";

  @ClassRule
  public static final OrchestratorRule ORCHESTRATOR = ScannerJavaLibraryTestSuite.ORCHESTRATOR;

  /**
   * Starts a WireMock HTTPS server that transparently forwards all traffic to the Orchestrator
   * SonarQube instance. Used as the HTTPS target in proxy-CONNECT tests.
   */
  private static final WireMockServer httpsReverseProxy = new WireMockServer(WireMockConfiguration.wireMockConfig()
    .dynamicHttpsPort()
    .keystorePath(getResourcePath(SERVER_KEYSTORE).toString())
    .keystorePassword(SERVER_KEYSTORE_PASSWORD)
    .keyManagerPassword(SERVER_KEYSTORE_PASSWORD)
    .keystoreType("PKCS12"));

  private ProxyServer proxyServer;

  @BeforeClass
  public static void startHttpsReverseProxy() {
    httpsReverseProxy.start();
    httpsReverseProxy.stubFor(any(anyUrl())
      .willReturn(aResponse()
        .proxiedFrom(ScannerJavaLibraryTestSuite.ORCHESTRATOR.getServer().getUrl())));
  }

  @AfterClass
  public static void stopHttpsReverseProxy() {
    httpsReverseProxy.stop();
  }

  @Before
  public void resetData() {
    ScannerJavaLibraryTestSuite.resetData(ORCHESTRATOR);
  }

  @After
  public void stopProxy() throws Exception {
    if (proxyServer != null) {
      proxyServer.stop();
    }
  }

  @Test
  public void analysis_without_proxy_configured_should_not_hit_proxy() throws Exception {
    proxyServer = ProxyServer.start();
    SimpleScanner scanner = new SimpleScanner();

    BuildResult result = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl());
    assertThat(result.getLastStatus()).isZero();
    assertThat(proxyServer.getRequestsSeenByProxy()).isEmpty();
  }

  @Test
  public void analysis_with_proxy_not_requesting_authentication_should_succeed() throws Exception {
    proxyServer = ProxyServer.start();
    SimpleScanner scanner = new SimpleScanner();

    Map<String, String> params = new HashMap<>();
    // By default, no request to localhost will use proxy — clear that restriction
    params.put("http.nonProxyHosts", "");
    params.put("http.proxyHost", "localhost");
    params.put("http.proxyPort", "" + proxyServer.getPort());

    BuildResult result = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl(), params, Map.of());
    assertThat(result.getLastStatus()).isZero();
    assertThat(proxyServer.getRequestsSeenByProxy()).isNotEmpty();
  }

  @Test
  public void analysis_with_proxy_requesting_authentication_should_fail_if_no_credentials_provided() throws Exception {
    proxyServer = ProxyServer.start(PROXY_USER, PROXY_PASSWORD);
    SimpleScanner scanner = new SimpleScanner();

    BuildResult result = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl(), newProxyParams(false), Map.of());
    assertThat(result.getLastStatus()).isNotZero();
    assertThat(result.getLogs()).containsPattern(
      "Failed to query server version: GET http://(.*)/api/server/version failed with HTTP 407 Proxy Authentication Required.");
    assertThat(proxyServer.getRequestsSeenByProxy()).isEmpty();
  }

  @Test
  public void analysis_with_proxy_requesting_authentication_should_succeed_if_credentials_provided() throws Exception {
    proxyServer = ProxyServer.start(PROXY_USER, PROXY_PASSWORD);
    SimpleScanner scanner = new SimpleScanner();

    Map<String, String> params = newProxyParams(false);
    params.put("sonar.scanner.proxyUser", PROXY_USER);
    params.put("sonar.scanner.proxyPassword", PROXY_PASSWORD);

    BuildResult result = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl(), params, Map.of());
    assertThat(result.getLastStatus()).isZero();
    assertThat(proxyServer.getRequestsSeenByProxy()).isNotEmpty();
  }

  @Test
  public void analysis_with_proxy_requesting_authentication_and_https_server_should_fail_if_no_credentials_provided() throws Exception {
    proxyServer = ProxyServer.start(PROXY_USER, PROXY_PASSWORD);
    SimpleScanner scanner = new SimpleScanner();

    BuildResult result = scanner.executeSimpleProject(project("js-sample"), "https://localhost:" + httpsReverseProxy.httpsPort(),
      newProxyParams(true), Map.of());
    assertThat(result.getLastStatus()).isNotZero();
    assertThat(result.getLogs()).containsIgnoringCase("Failed to query server version");
    assertThat(proxyServer.getConnectRequestsSeenByProxy()).isEmpty();
  }

  /**
   * Reproduces the regression reported in SCANJLIB-306 (java-library 4.0):
   * HTTPS proxy authentication was broken — the {@code Proxy-Authorization} header was
   * not sent on the CONNECT tunnel, so the proxy kept returning 407.
   * <p>
   * This test uses a local HTTP forward proxy that enforces authentication on CONNECT
   * requests, plus a local HTTPS reverse-proxy that forwards to the running SonarQube
   * instance. This mirrors the real-world topology: scanner → HTTP proxy (CONNECT) →
   * HTTPS SonarQube.
   */
  @Test
  public void analysis_with_proxy_auth_and_https_server_should_succeed() throws Exception {
    proxyServer = ProxyServer.start(PROXY_USER, PROXY_PASSWORD);
    SimpleScanner scanner = new SimpleScanner();

    Map<String, String> params = newProxyParams(true);
    params.put("sonar.scanner.proxyUser", PROXY_USER);
    params.put("sonar.scanner.proxyPassword", PROXY_PASSWORD);

    BuildResult result = scanner.executeSimpleProject(project("js-sample"), "https://localhost:" + httpsReverseProxy.httpsPort(),
      params, Map.of());
    assertThat(result.getLastStatus()).isZero();
    assertThat(proxyServer.getConnectRequestsSeenByProxy()).isNotEmpty();
  }

  /**
   * Builds the base set of proxy params for authenticated proxy tests.
   * When {@code useHttps} is {@code true}, also adds the truststore to validate the HTTPS reverse proxy certificate,
   * and sets {@code jdk.http.auth.tunneling.disabledSchemes} so the JDK honours Basic auth on CONNECT tunnels.
   */
  private Map<String, String> newProxyParams(boolean useHttps) {
    Map<String, String> params = new HashMap<>();
    // By default, no request to localhost will use proxy — clear that restriction
    params.put("http.nonProxyHosts", "");
    params.put("sonar.scanner.proxyHost", "localhost");
    params.put("sonar.scanner.proxyPort", "" + proxyServer.getPort());
    if (useHttps) {
      Path clientTruststore = getResourcePath(KEYSTORE_CLIENT_WITH_CA);
      assertThat(clientTruststore).exists();
      // JDK-8210814: without this, the JDK does not perform Basic auth on CONNECT tunnels
      params.put("jdk.http.auth.tunneling.disabledSchemes", "");
      params.put("sonar.scanner.truststorePath", clientTruststore.toString());
      params.put("sonar.scanner.truststorePassword", KEYSTORE_CLIENT_WITH_CA_PASSWORD);
    }
    return params;
  }

  private static Path project(String projectName) {
    return Paths.get("..", "projects", projectName);
  }

  private static Path getResourcePath(String resourceName) {
    try {
      return Paths.get(ProxyTest.class.getResource(resourceName).toURI()).toAbsolutePath();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }
}
