/*
 * SonarQube Scanner API - ITs
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
package com.sonar.scanner.api.it;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.util.NetworkUtils;
import com.sonar.scanner.api.it.tools.SimpleScanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.proxy.ConnectHandler;
import org.eclipse.jetty.proxy.ProxyServlet;
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
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SSLTest {

  private static final String CLIENT_KEYSTORE = "/SSLTest/clientkeystore.jks";
  private static final String CLIENT_KEYSTORE_PWD = "clientp12pwd";

  private static final String CLIENT_TRUSTSTORE = "/SSLTest/clienttruststore.jks";
  private static final String CLIENT_TRUSTSTORE_PWD = "clienttruststorepwd";

  static final String SERVER_TRUSTSTORE = "/SSLTest/servertruststore.jks";
  static final String SERVER_TRUSTSTORE_PWD = "servertruststorepwd";

  static final String SERVER_KEYSTORE = "/SSLTest/serverkeystore.jks";
  static final String SERVER_KEYSTORE_PWD = "serverkeystorepwd";

  private static final String PROXY_USER = "scott";
  private static final String PROXY_PASSWORD = "tiger";

  private static Server sslTransparentProxyserver;
  private static int httpsPort;
  private static Server proxyServer;
  private static int httpsProxyPort;

  private static ConcurrentLinkedDeque<String> seenByProxy = new ConcurrentLinkedDeque<>();

  @ClassRule
  public static final Orchestrator ORCHESTRATOR = ScannerApiTestSuite.ORCHESTRATOR;

  @Before
  public void deleteData() {
    ORCHESTRATOR.resetData();
    seenByProxy.clear();
  }

  @After
  public void stopProxy() throws Exception {
    if (sslTransparentProxyserver != null && sslTransparentProxyserver.isStarted()) {
      sslTransparentProxyserver.stop();
    }
    if (proxyServer != null && proxyServer.isStarted()) {
      proxyServer.stop();
    }
  }

  private static void startProxy(boolean basicAuth) throws Exception {
    httpsProxyPort = NetworkUtils.getNextAvailablePort();

    // Setup Threadpool
    QueuedThreadPool threadPool = new QueuedThreadPool();
    threadPool.setMaxThreads(500);

    proxyServer = new Server(threadPool);

    // HTTP(s) Configuration
    HttpConfiguration httpConfig = new HttpConfiguration();

    // Handler Structure
    HandlerCollection handlers = new HandlerCollection();
    handlers.setHandlers(new Handler[] {proxyHandler(basicAuth), new DefaultHandler()});
    proxyServer.setHandler(handlers);

    ServerConnector connector = new ServerConnector(proxyServer, new HttpConnectionFactory(httpConfig));
    connector.setPort(httpsProxyPort);
    proxyServer.addConnector(connector);

    proxyServer.start();
  }

  private static ConnectHandler proxyHandler(boolean basicAuth) {
    return new ConnectHandler() {
      @Override
      protected boolean handleAuthentication(HttpServletRequest request, HttpServletResponse response, String address) {
        if (!basicAuth) {
          return true;
        }
        String credentials = request.getHeader(HttpHeader.PROXY_AUTHORIZATION.asString());
        if (credentials != null) {
          int space = credentials.indexOf(' ');
          if (space > 0) {
            String method = credentials.substring(0, space);
            if ("basic".equalsIgnoreCase(method)) {
              credentials = credentials.substring(space + 1);
              credentials = B64Code.decode(credentials, StandardCharsets.ISO_8859_1);
              int i = credentials.indexOf(':');
              if (i > 0) {
                String username = credentials.substring(0, i);
                String password = credentials.substring(i + 1);

                return PROXY_USER.equals(username) && PROXY_PASSWORD.equals(password);
              }
            }
          }
        }
        return false;
      }

      @Override
      public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        seenByProxy.add(request.getRequestURI());
        super.handle(target, baseRequest, request, response);
      }
    };

  }

  private static void startSSLTransparentReverseProxy(boolean requireClientAuth) throws Exception {
    int httpPort = NetworkUtils.getNextAvailablePort();
    httpsPort = NetworkUtils.getNextAvailablePort();

    // Setup Threadpool
    QueuedThreadPool threadPool = new QueuedThreadPool();
    threadPool.setMaxThreads(500);

    sslTransparentProxyserver = new Server(threadPool);

    // HTTP Configuration
    HttpConfiguration httpConfig = new HttpConfiguration();
    httpConfig.setSecureScheme("https");
    httpConfig.setSecurePort(httpsPort);
    httpConfig.setSendServerVersion(true);
    httpConfig.setSendDateHeader(false);

    // Handler Structure
    HandlerCollection handlers = new HandlerCollection();
    handlers.setHandlers(new Handler[] {sslTransparentReverseProxyHandler(), new DefaultHandler()});
    sslTransparentProxyserver.setHandler(handlers);

    ServerConnector http = new ServerConnector(sslTransparentProxyserver, new HttpConnectionFactory(httpConfig));
    http.setPort(httpPort);
    sslTransparentProxyserver.addConnector(http);

    Path serverKeyStore = Paths.get(SSLTest.class.getResource(SERVER_KEYSTORE).toURI()).toAbsolutePath();
    String serverKeyPassword = "serverp12pwd";
    Path serverTrustStore = Paths.get(SSLTest.class.getResource(SERVER_TRUSTSTORE).toURI()).toAbsolutePath();
    assertThat(serverKeyStore).exists();
    assertThat(serverTrustStore).exists();

    // SSL Context Factory
    SslContextFactory sslContextFactory = new SslContextFactory();
    sslContextFactory.setKeyStorePath(serverKeyStore.toString());
    sslContextFactory.setKeyStorePassword(SERVER_KEYSTORE_PWD);
    sslContextFactory.setKeyManagerPassword(serverKeyPassword);
    sslContextFactory.setTrustStorePath(serverTrustStore.toString());
    sslContextFactory.setTrustStorePassword(SERVER_TRUSTSTORE_PWD);
    sslContextFactory.setNeedClientAuth(requireClientAuth);
    sslContextFactory.setExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA",
      "SSL_DHE_RSA_WITH_DES_CBC_SHA",
      "SSL_DHE_DSS_WITH_DES_CBC_SHA",
      "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
      "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
      "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
      "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");

    // SSL HTTP Configuration
    HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);

    // SSL Connector
    ServerConnector sslConnector = new ServerConnector(sslTransparentProxyserver,
      new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
      new HttpConnectionFactory(httpsConfig));
    sslConnector.setPort(httpsPort);
    sslTransparentProxyserver.addConnector(sslConnector);

    sslTransparentProxyserver.start();
  }

  private static ServletContextHandler sslTransparentReverseProxyHandler() {
    ServletContextHandler contextHandler = new ServletContextHandler();
    contextHandler.setServletHandler(newSslTransparentReverseProxyServletHandler());
    return contextHandler;
  }

  private static ServletHandler newSslTransparentReverseProxyServletHandler() {
    ServletHandler handler = new ServletHandler();
    ServletHolder holder = handler.addServletWithMapping(ProxyServlet.Transparent.class, "/*");
    holder.setInitParameter("proxyTo", ORCHESTRATOR.getServer().getUrl());
    return handler;
  }

  @Test
  public void simple_analysis_with_server_and_client_certificate() throws Exception {
    startSSLTransparentReverseProxy(true);
    SimpleScanner scanner = new SimpleScanner();
    BuildResult buildResult = scanner.executeSimpleProject(project("java-sample"), "https://localhost:" + httpsPort);

    assertThat(buildResult.getLastStatus()).isNotEqualTo(0);
    assertThat(buildResult.getLogs()).contains("javax.net.ssl.SSLHandshakeException");

    Path clientTruststore = Paths.get(SSLTest.class.getResource(CLIENT_TRUSTSTORE).toURI()).toAbsolutePath();
    assertThat(clientTruststore).exists();
    Path clientKeystore = Paths.get(SSLTest.class.getResource(CLIENT_KEYSTORE).toURI()).toAbsolutePath();
    assertThat(clientKeystore).exists();

    Map<String, String> params = new HashMap<>();
    params.put("javax.net.ssl.trustStore", clientTruststore.toString());
    params.put("javax.net.ssl.trustStorePassword", CLIENT_TRUSTSTORE_PWD);
    params.put("javax.net.ssl.keyStore", clientKeystore.toString());
    params.put("javax.net.ssl.keyStorePassword", CLIENT_KEYSTORE_PWD);

    buildResult = scanner.executeSimpleProject(project("java-sample"), "https://localhost:" + httpsPort, params);
    assertThat(buildResult.getLastStatus()).isEqualTo(0);
  }

  private static Path project(String projectName) {
    return Paths.get("..", "projects", projectName);
  }

  @Test
  public void simple_analysis_with_server_certificate() throws Exception {
    startSSLTransparentReverseProxy(false);
    SimpleScanner scanner = new SimpleScanner();

    BuildResult buildResult = scanner.executeSimpleProject(project("java-sample"), "https://localhost:" + httpsPort);
    assertThat(buildResult.getLastStatus()).isNotEqualTo(0);
    assertThat(buildResult.getLogs()).contains("javax.net.ssl.SSLHandshakeException");

    Path clientTruststore = Paths.get(SSLTest.class.getResource(CLIENT_TRUSTSTORE).toURI()).toAbsolutePath();
    String truststorePassword = CLIENT_TRUSTSTORE_PWD;
    assertThat(clientTruststore).exists();

    Map<String, String> params = new HashMap<>();
    params.put("javax.net.ssl.trustStore", clientTruststore.toString());
    params.put("javax.net.ssl.trustStorePassword", truststorePassword);

    buildResult = scanner.executeSimpleProject(project("java-sample"), "https://localhost:" + httpsPort, params);
    assertThat(buildResult.getLastStatus()).isEqualTo(0);
  }

  @Test
  public void simple_analysis_with_server_certificate_and_https_proxy() throws Exception {
    startSSLTransparentReverseProxy(false);
    startProxy(false);
    SimpleScanner scanner = new SimpleScanner();

    Map<String, String> params = new HashMap<>();
    // By default no request to localhost will use proxy
    params.put("http.nonProxyHosts", "");
    params.put("https.proxyHost", "localhost");
    params.put("https.proxyPort", "" + httpsProxyPort);
    Path clientTruststore = Paths.get(SSLTest.class.getResource(CLIENT_TRUSTSTORE).toURI()).toAbsolutePath();
    String truststorePassword = CLIENT_TRUSTSTORE_PWD;
    assertThat(clientTruststore).exists();
    params.put("javax.net.ssl.trustStore", clientTruststore.toString());
    params.put("javax.net.ssl.trustStorePassword", truststorePassword);

    BuildResult buildResult = scanner.executeSimpleProject(project("java-sample"), "https://localhost:" + httpsPort, params);
    assertThat(buildResult.getLastStatus()).isEqualTo(0);
    assertThat(seenByProxy).isNotEmpty();
  }

  @Test
  public void simple_analysis_with_server_certificate_and_https_proxy_with_basic_auth() throws Exception {
    startSSLTransparentReverseProxy(false);
    startProxy(true);
    SimpleScanner scanner = new SimpleScanner();

    Map<String, String> params = new HashMap<>();
    // By default no request to localhost will use proxy
    params.put("http.nonProxyHosts", "");
    params.put("https.proxyHost", "localhost");
    params.put("https.proxyPort", "" + httpsProxyPort);
    Path clientTruststore = Paths.get(SSLTest.class.getResource(CLIENT_TRUSTSTORE).toURI()).toAbsolutePath();
    String truststorePassword = CLIENT_TRUSTSTORE_PWD;
    assertThat(clientTruststore).exists();
    params.put("javax.net.ssl.trustStore", clientTruststore.toString());
    params.put("javax.net.ssl.trustStorePassword", truststorePassword);

    BuildResult buildResult = scanner.executeSimpleProject(project("java-sample"), "https://localhost:" + httpsPort, params);
    assertThat(buildResult.getLastStatus()).isNotEqualTo(0);
    assertThat(buildResult.getLogs()).contains("Failed to authenticate with proxy");

    params.put("http.proxyUser", PROXY_USER);
    params.put("http.proxyPassword", PROXY_PASSWORD);

    buildResult = scanner.executeSimpleProject(project("java-sample"), "https://localhost:" + httpsPort, params);
    assertThat(buildResult.getLastStatus()).isEqualTo(0);
    assertThat(seenByProxy).isNotEmpty();
  }

}
