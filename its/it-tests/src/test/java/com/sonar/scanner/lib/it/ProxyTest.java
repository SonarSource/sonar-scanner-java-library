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

import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.junit4.OrchestratorRule;
import com.sonar.orchestrator.util.NetworkUtils;
import com.sonar.scanner.lib.it.tools.ProxyAuthenticator;
import com.sonar.scanner.lib.it.tools.SimpleScanner;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.proxy.ConnectHandler;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.UserStore;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProxyTest {

  private static final String PROXY_USER = "scott";
  private static final String PROXY_PASSWORD = "tiger";

  // SSL resources reused from SSLTest
  private static final String SERVER_KEYSTORE = "/SSLTest/server.p12";
  private static final String SERVER_KEYSTORE_PASSWORD = "pwdServerP12";
  private static final String KEYSTORE_CLIENT_WITH_CA = "/SSLTest/client-with-ca-keytool.p12";
  private static final String KEYSTORE_CLIENT_WITH_CA_PASSWORD = "pwdClientCAP12";

  private static Server server;
  private static int httpProxyPort;
  // HTTPS reverse-proxy target, used for the HTTPS CONNECT tests
  private static Server httpsTargetServer;
  private static int httpsTargetPort;

  @ClassRule
  public static final OrchestratorRule ORCHESTRATOR = ScannerJavaLibraryTestSuite.ORCHESTRATOR;

  private static final ConcurrentLinkedDeque<String> seenByProxy = new ConcurrentLinkedDeque<>();
  private static final ConcurrentLinkedDeque<String> seenConnectByProxy = new ConcurrentLinkedDeque<>();

  @Before
  public void deleteData() {
    ScannerJavaLibraryTestSuite.resetData(ORCHESTRATOR);
    seenByProxy.clear();
    seenConnectByProxy.clear();
  }

  @After
  public void stopProxy() throws Exception {
    if (server != null && server.isStarted()) {
      server.stop();
    }
    if (httpsTargetServer != null && httpsTargetServer.isStarted()) {
      httpsTargetServer.stop();
    }
  }

  private static void startProxy(boolean needProxyAuth) throws Exception {
    httpProxyPort = NetworkUtils.getNextAvailablePort(InetAddress.getLocalHost());

    QueuedThreadPool threadPool = new QueuedThreadPool();
    threadPool.setMaxThreads(500);

    server = new Server(threadPool);

    HttpConfiguration httpConfig = new HttpConfiguration();
    httpConfig.setSecureScheme("https");
    httpConfig.setSendServerVersion(true);
    httpConfig.setSendDateHeader(false);

    // Wrap the ProxyServlet handler with a ConnectHandler so HTTPS CONNECT
    // tunnels are also handled (and authenticated) by the same proxy.
    TrackingConnectHandler connectHandler = new TrackingConnectHandler(needProxyAuth);
    connectHandler.setHandler(proxyHandler(needProxyAuth));

    HandlerCollection handlers = new HandlerCollection();
    handlers.setHandlers(new Handler[] {connectHandler, new DefaultHandler()});
    server.setHandler(handlers);

    ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
    http.setPort(httpProxyPort);
    server.addConnector(http);

    server.start();
  }

  /**
   * Starts a simple HTTPS reverse-proxy that forwards all traffic to the Orchestrator SonarQube
   * instance. Used as the HTTPS target in proxy-CONNECT tests.
   */
  private static void startHttpsTargetServer() throws Exception {
    httpsTargetPort = NetworkUtils.getNextAvailablePort(InetAddress.getLocalHost());

    QueuedThreadPool threadPool = new QueuedThreadPool();
    threadPool.setMaxThreads(500);

    httpsTargetServer = new Server(threadPool);

    HttpConfiguration httpConfig = new HttpConfiguration();
    httpConfig.setSecureScheme("https");
    httpConfig.setSecurePort(httpsTargetPort);
    httpConfig.setSendServerVersion(true);
    httpConfig.setSendDateHeader(false);

    Path serverKeyStore = Paths.get(ProxyTest.class.getResource(SERVER_KEYSTORE).toURI()).toAbsolutePath();
    assertThat(serverKeyStore).exists();

    ServerConnector sslConnector = buildServerConnector(serverKeyStore, httpConfig);
    httpsTargetServer.addConnector(sslConnector);

    // Transparently forward all requests to the Orchestrator instance
    ServletContextHandler context = new ServletContextHandler();
    ServletHandler servletHandler = new ServletHandler();
    ServletHolder holder = servletHandler.addServletWithMapping(ProxyServlet.Transparent.class, "/*");
    holder.setInitParameter("proxyTo", ORCHESTRATOR.getServer().getUrl());
    context.setServletHandler(servletHandler);
    httpsTargetServer.setHandler(context);

    httpsTargetServer.start();
  }

  private static ServerConnector buildServerConnector(Path serverKeyStore, HttpConfiguration httpConfig) {
    SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
    sslContextFactory.setKeyStorePath(serverKeyStore.toString());
    sslContextFactory.setKeyStorePassword(SERVER_KEYSTORE_PASSWORD);
    sslContextFactory.setKeyManagerPassword(SERVER_KEYSTORE_PASSWORD);

    HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
    ServerConnector sslConnector = new ServerConnector(httpsTargetServer,
      new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
      new HttpConnectionFactory(httpsConfig));
    sslConnector.setPort(httpsTargetPort);
    return sslConnector;
  }

  private static ServletContextHandler proxyHandler(boolean needProxyAuth) {
    ServletContextHandler contextHandler = new ServletContextHandler();
    if (needProxyAuth) {
      contextHandler.setSecurityHandler(basicAuth(PROXY_USER, PROXY_PASSWORD, "Private!"));
    }
    contextHandler.setServletHandler(newServletHandler());
    return contextHandler;
  }

  private static SecurityHandler basicAuth(String username, String password, String realm) {

    HashLoginService l = new HashLoginService(realm);

    UserStore userStore = new UserStore();
    userStore.addUser(username, Credential.getCredential(password), new String[] {"user"});

    l.setUserStore(userStore);

    Constraint constraint = new Constraint();
    constraint.setName(Constraint.__BASIC_AUTH);
    constraint.setRoles(new String[] {"user"});
    constraint.setAuthenticate(true);

    ConstraintMapping cm = new ConstraintMapping();
    cm.setConstraint(constraint);
    cm.setPathSpec("/*");

    ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
    csh.setAuthenticator(new ProxyAuthenticator());
    csh.setRealmName("myrealm");
    csh.addConstraintMapping(cm);
    csh.setLoginService(l);

    return csh;

  }

  private static ServletHandler newServletHandler() {
    ServletHandler handler = new ServletHandler();
    handler.addServletWithMapping(MyProxyServlet.class, "/*");
    return handler;
  }

  /**
   * ConnectHandler subclass that:
   * <ul>
   *   <li>Optionally requires {@code Proxy-Authorization} on CONNECT requests</li>
   *   <li>Records the host:port of every successfully-authenticated CONNECT</li>
   * </ul>
   * <p>
   * When authentication is required and credentials are missing, the handler sends a well-formed
   * {@code 407} response and lets Jetty close the connection naturally. This allows the JDK
   * {@link java.net.Authenticator} to read the challenge, supply credentials, and retry the CONNECT
   * on a new connection — exactly the flow that the {@code HttpClientFactory} fix enables.
   */
  private static class TrackingConnectHandler extends ConnectHandler {

    private final boolean requireAuth;

    TrackingConnectHandler(boolean requireAuth) {
      this.requireAuth = requireAuth;
    }

    @Override
    protected void handleConnect(org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request,
      HttpServletResponse response, String serverAddress) {
      if (requireAuth && !hasValidCredentials(request)) {
        response.setStatus(HttpServletResponse.SC_PROXY_AUTHENTICATION_REQUIRED);
        response.setHeader("Proxy-Authenticate", "Basic realm=\"proxy\"");
        response.setContentLength(0);
        baseRequest.setHandled(true);
        return;
      }
      seenConnectByProxy.add(serverAddress);
      super.handleConnect(baseRequest, request, response, serverAddress);
    }

    private static boolean hasValidCredentials(HttpServletRequest request) {
      String credentials = request.getHeader("Proxy-Authorization");
      if (credentials != null && credentials.startsWith("Basic ")) {
        String decoded = new String(Base64.getDecoder().decode(credentials.substring(6)), StandardCharsets.ISO_8859_1);
        int colon = decoded.indexOf(':');
        if (colon > 0) {
          String user = decoded.substring(0, colon);
          String pass = decoded.substring(colon + 1);
          return PROXY_USER.equals(user) && PROXY_PASSWORD.equals(pass);
        }
      }
      return false;
    }
  }

  public static class MyProxyServlet extends ProxyServlet {

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      seenByProxy.add(request.getRequestURI());
      super.service(request, response);
    }

    @Override
    protected void sendProxyRequest(HttpServletRequest clientRequest, HttpServletResponse proxyResponse, Request proxyRequest) {
      super.sendProxyRequest(clientRequest, proxyResponse, proxyRequest);

    }

  }

  private static Path project(String projectName) {
    return Paths.get("..", "projects", projectName);
  }

  @Test
  public void simple_analysis_with_proxy_no_auth() throws Exception {
    startProxy(false);
    SimpleScanner scanner = new SimpleScanner();

    // Don't use proxy
    BuildResult buildResult = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl());
    assertThat(buildResult.getLastStatus()).isZero();
    assertThat(seenByProxy).isEmpty();

    Map<String, String> params = new HashMap<>();
    // By default, no request to localhost will use proxy
    params.put("http.nonProxyHosts", "");
    params.put("http.proxyHost", "localhost");
    params.put("http.proxyPort", "" + httpProxyPort);

    buildResult = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl(), params, Map.of());
    assertThat(buildResult.getLastStatus()).isZero();
    assertThat(seenByProxy).isNotEmpty();
  }

  @Test
  public void simple_analysis_with_proxy_auth() throws Exception {
    startProxy(true);
    SimpleScanner scanner = new SimpleScanner();

    Map<String, String> params = new HashMap<>();
    // By default, no request to localhost will use proxy
    params.put("http.nonProxyHosts", "");
    params.put("sonar.scanner.proxyHost", "localhost");
    params.put("sonar.scanner.proxyPort", "" + httpProxyPort);

    BuildResult buildResult = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl(), params, Map.of());
    assertThat(buildResult.getLastStatus()).isNotZero();
    assertThat(buildResult.getLogs()).containsPattern("Failed to query server version: GET http://(.*)/api/server/version failed with HTTP 407 Proxy Authentication Required.");
    assertThat(seenByProxy).isEmpty();

    params.put("sonar.scanner.proxyUser", PROXY_USER);
    params.put("sonar.scanner.proxyPassword", PROXY_PASSWORD);
    buildResult = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl(), params, Map.of());
    assertThat(seenByProxy).isNotEmpty();
    System.out.println(buildResult.getLogs());
    assertThat(buildResult.getLastStatus()).isZero();
  }

  /**
   * Reproduces the regression reported for SonarScanner CLI 8.0 (java-library 4.0):
   * HTTPS proxy authentication was broken — the {@code Proxy-Authorization} header was
   * not sent on the CONNECT tunnel, so the proxy kept returning 407.
   * <p>
   * This test uses a local HTTP forward proxy that enforces authentication on CONNECT
   * requests, plus a local HTTPS reverse-proxy that forwards to the running SonarQube
   * instance. This mirrors the real-world topology: scanner → HTTP proxy (CONNECT) →
   * HTTPS SonarQube.
   */
  @Test
  public void simple_analysis_with_https_proxy_auth() throws Exception {
    startProxy(true);
    startHttpsTargetServer();
    SimpleScanner scanner = new SimpleScanner();

    Path clientTruststore = Paths.get(ProxyTest.class.getResource(KEYSTORE_CLIENT_WITH_CA).toURI()).toAbsolutePath();
    assertThat(clientTruststore).exists();

    Map<String, String> params = new HashMap<>();
    // By default, no request to localhost will use proxy
    params.put("http.nonProxyHosts", "");
    // JDK-8210814 without that, the JDK is not doing basic authentication on CONNECT tunnels
    params.put("jdk.http.auth.tunneling.disabledSchemes", "");
    params.put("sonar.scanner.proxyHost", "localhost");
    params.put("sonar.scanner.proxyPort", "" + httpProxyPort);
    // Trust the self-signed certificate used by the local HTTPS target
    params.put("sonar.scanner.truststorePath", clientTruststore.toString());
    params.put("sonar.scanner.truststorePassword", KEYSTORE_CLIENT_WITH_CA_PASSWORD);

    // Without proxy credentials the CONNECT tunnel should be rejected (407)
    BuildResult buildResult = scanner.executeSimpleProject(project("js-sample"), "https://localhost:" + httpsTargetPort, params, Map.of());
    assertThat(buildResult.getLastStatus()).isNotZero();
    assertThat(buildResult.getLogs()).containsIgnoringCase("Failed to query server version");
    assertThat(seenConnectByProxy).isEmpty();

    // With proxy credentials the CONNECT tunnel must succeed and the full analysis must pass
    params.put("sonar.scanner.proxyUser", PROXY_USER);
    params.put("sonar.scanner.proxyPassword", PROXY_PASSWORD);
    buildResult = scanner.executeSimpleProject(project("js-sample"), "https://localhost:" + httpsTargetPort, params, Map.of());
    assertThat(buildResult.getLastStatus()).isZero();
    assertThat(seenConnectByProxy).isNotEmpty();
  }

}
