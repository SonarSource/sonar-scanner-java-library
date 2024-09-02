/*
 * SonarScanner Java Library - ITs
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
package com.sonar.scanner.lib.it;

import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.junit4.OrchestratorRule;
import com.sonar.orchestrator.util.NetworkUtils;
import com.sonar.scanner.lib.it.tools.SimpleScanner;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jetty.http.HttpVersion;
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
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(DataProviderRunner.class)
public class SSLTest {

  // This keystore contains only the CA used to sign the server certificate
  private static final String KEYSTORE_CLIENT_WITH_CA_KEYTOOL = "/SSLTest/client-with-ca-keytool.p12";
  private static final String KEYSTORE_CLIENT_WITH_CA_OPENSSL = "/SSLTest/client-with-ca-openssl.p12";
  private static final String CLIENT_WITH_CA_KEYSTORE_PASSWORD = "pwdClientCAP12";

  // This keystore contains only the server certificate
  private static final String KEYSTORE_CLIENT_WITH_CERTIFICATE_KEYTOOL = "/SSLTest/client-with-certificate-keytool.p12";
  private static final String KEYSTORE_CLIENT_WITH_CERTIFICATE_OPENSSL = "/SSLTest/client-with-certificate-openssl.p12";
  private static final String KEYSTORE_CLIENT_WITH_CERTIFICATE_OPENSSL_JDKTRUST = "/SSLTest/client-with-certificate-openssl-jdktrust.p12";
  private static final String CLIENT_WITH_CERTIFICATE_KEYSTORE_PASSWORD = "pwdClientP12";

  private static final String SERVER_KEYSTORE = "/SSLTest/server.p12";
  private static final String SERVER_KEYSTORE_PASSWORD = "pwdServerP12";

  private static final String SERVER_TRUSTSTORE_WITH_CLIENT_CA = "/SSLTest/server-with-client-ca.p12";
  private static final String SERVER_TRUSTSTORE_WITH_CLIENT_CA_PASSWORD = "pwdServerWithClientCA";

  private static final String CLIENT_KEYSTORE = "/SSLTest/client.p12";
  private static final String CLIENT_KEYSTORE_PASSWORD = "pwdClientCertP12";

  private static Server server;
  private static int httpsPort;

  @ClassRule
  public static final OrchestratorRule ORCHESTRATOR = ScannerJavaLibraryTestSuite.ORCHESTRATOR;

  @Before
  public void deleteData() {
    ScannerJavaLibraryTestSuite.resetData(ORCHESTRATOR);
  }

  @After
  public void stopProxy() throws Exception {
    if (server != null && server.isStarted()) {
      server.stop();
    }
  }

  private static void startSSLTransparentReverseProxy(boolean requireClientAuth) throws Exception {
    int httpPort = NetworkUtils.getNextAvailablePort(InetAddress.getLocalHost());
    httpsPort = NetworkUtils.getNextAvailablePort(InetAddress.getLocalHost());

    // Setup Threadpool
    QueuedThreadPool threadPool = new QueuedThreadPool();
    threadPool.setMaxThreads(500);

    server = new Server(threadPool);

    // HTTP Configuration
    HttpConfiguration httpConfig = new HttpConfiguration();
    httpConfig.setSecureScheme("https");
    httpConfig.setSecurePort(httpsPort);
    httpConfig.setSendServerVersion(true);
    httpConfig.setSendDateHeader(false);

    // Handler Structure
    HandlerCollection handlers = new HandlerCollection();
    handlers.setHandlers(new Handler[]{proxyHandler(), new DefaultHandler()});
    server.setHandler(handlers);

    ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
    http.setPort(httpPort);
    server.addConnector(http);

    Path serverKeyStore = Paths.get(SSLTest.class.getResource(SERVER_KEYSTORE).toURI()).toAbsolutePath();
    assertThat(serverKeyStore).exists();

    // SSL Context Factory
    SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
    sslContextFactory.setKeyStorePath(serverKeyStore.toString());
    sslContextFactory.setKeyStorePassword(SERVER_KEYSTORE_PASSWORD);
    sslContextFactory.setKeyManagerPassword(SERVER_KEYSTORE_PASSWORD);
    if (requireClientAuth) {
      Path serverTrustStore = Paths.get(SSLTest.class.getResource(SERVER_TRUSTSTORE_WITH_CLIENT_CA).toURI()).toAbsolutePath();
      sslContextFactory.setTrustStorePath(serverTrustStore.toString());
      assertThat(serverTrustStore).exists();
      sslContextFactory.setTrustStorePassword(SERVER_TRUSTSTORE_WITH_CLIENT_CA_PASSWORD);
    }
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
    ServerConnector sslConnector = new ServerConnector(server,
      new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
      new HttpConnectionFactory(httpsConfig));
    sslConnector.setPort(httpsPort);
    server.addConnector(sslConnector);

    server.start();
  }

  private static ServletContextHandler proxyHandler() {
    ServletContextHandler contextHandler = new ServletContextHandler();
    contextHandler.setServletHandler(newServletHandler());
    return contextHandler;
  }

  private static ServletHandler newServletHandler() {
    ServletHandler handler = new ServletHandler();
    ServletHolder holder = handler.addServletWithMapping(ProxyServlet.Transparent.class, "/*");
    holder.setInitParameter("proxyTo", ORCHESTRATOR.getServer().getUrl());
    return handler;
  }

  @Test
  public void simple_analysis_with_server_and_client_certificate() throws Exception {
    startSSLTransparentReverseProxy(true);
    SimpleScanner scanner = new SimpleScanner();
    BuildResult buildResult = scanner.executeSimpleProject(project("js-sample"), "https://localhost:" + httpsPort);

    assertThat(buildResult.getLastStatus()).isNotZero();
    assertThat(buildResult.getLogs()).contains("javax.net.ssl.SSLHandshakeException");

    Path clientTruststore = Paths.get(SSLTest.class.getResource(KEYSTORE_CLIENT_WITH_CA_KEYTOOL).toURI()).toAbsolutePath();
    assertThat(clientTruststore).exists();
    Path clientKeystore = Paths.get(SSLTest.class.getResource(CLIENT_KEYSTORE).toURI()).toAbsolutePath();
    assertThat(clientKeystore).exists();

    Map<String, String> params = new HashMap<>();
    // In the truststore we have the CA allowing to connect to local TLS server
    params.put("javax.net.ssl.trustStore", clientTruststore.toString());
    params.put("javax.net.ssl.trustStorePassword", CLIENT_WITH_CA_KEYSTORE_PASSWORD);
    // The KeyStore is storing the certificate to identify the user
    params.put("javax.net.ssl.keyStore", clientKeystore.toString());
    params.put("javax.net.ssl.keyStorePassword", CLIENT_KEYSTORE_PASSWORD);

    buildResult = scanner.executeSimpleProject(project("js-sample"), "https://localhost:" + httpsPort, params, Map.of());
    assertThat(buildResult.getLastStatus()).isZero();
  }

  @Test
  public void simple_analysis_with_server_and_without_client_certificate_is_failing() throws Exception {
    startSSLTransparentReverseProxy(true);
    SimpleScanner scanner = new SimpleScanner();
    BuildResult buildResult = scanner.executeSimpleProject(project("js-sample"), "https://localhost:" + httpsPort);

    assertThat(buildResult.getLastStatus()).isNotZero();
    assertThat(buildResult.getLogs()).contains("javax.net.ssl.SSLHandshakeException");

    Path clientTruststore = Paths.get(SSLTest.class.getResource(KEYSTORE_CLIENT_WITH_CA_KEYTOOL).toURI()).toAbsolutePath();
    assertThat(clientTruststore).exists();
    Path clientKeystore = Paths.get(SSLTest.class.getResource(CLIENT_KEYSTORE).toURI()).toAbsolutePath();
    assertThat(clientKeystore).exists();

    Map<String, String> params = new HashMap<>();
    // In the truststore we have the CA allowing to connect to local TLS server
    params.put("javax.net.ssl.trustStore", clientTruststore.toString());
    params.put("javax.net.ssl.trustStorePassword", CLIENT_WITH_CA_KEYSTORE_PASSWORD);
    // Voluntary missing client keystore

    buildResult = scanner.executeSimpleProject(project("js-sample"), "https://localhost:" + httpsPort, params, Map.of());
    assertThat(buildResult.getLastStatus()).isEqualTo(1);

    // different exception is thrown depending on the JDK version. See: https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8172163
    String failedAnalysis = "(?s).*java\\.lang\\.IllegalStateException: Failed to get server version.*";
    assertThat(buildResult.getLogs())
      .matches(p -> p.matches(failedAnalysis + "Caused by: javax\\.net\\.ssl\\.SSLException: Broken pipe \\(Write failed\\).*") ||
        p.matches(failedAnalysis + "Caused by: javax\\.net\\.ssl\\.SSLProtocolException: Broken pipe \\(Write failed\\).*") ||
        p.matches(failedAnalysis + "Caused by: javax\\.net\\.ssl\\.SSLHandshakeException: Received fatal alert: bad_certificate.*") ||
        p.matches(failedAnalysis + "Caused by: java\\.net\\.SocketException: Broken pipe.*") ||
        p.matches(failedAnalysis + "Caused by: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target.*")
      );
  }

  private static Path project(String projectName) {
    return Paths.get("..", "projects", projectName);
  }

  @Test
  @UseDataProvider("variousClientTrustStores")
  public void simple_analysis_with_server_certificate(String clientTrustStore, String keyStorePassword, boolean useJavaSslProperties) throws Exception {
    startSSLTransparentReverseProxy(false);
    SimpleScanner scanner = new SimpleScanner();

    BuildResult buildResult = scanner.executeSimpleProject(project("js-sample"), "https://localhost:" + httpsPort);
    assertThat(buildResult.getLastStatus()).isNotZero();
    assertThat(buildResult.getLogs()).contains("javax.net.ssl.SSLHandshakeException");

    Path clientTruststore = Paths.get(SSLTest.class.getResource(clientTrustStore).toURI()).toAbsolutePath();
    assertThat(clientTruststore).exists();

    Map<String, String> params = new HashMap<>();
    if (useJavaSslProperties) {
      params.put("javax.net.ssl.trustStore", clientTruststore.toString());
      params.put("javax.net.ssl.trustStorePassword", keyStorePassword);
    } else {
      params.put("sonar.scanner.truststorePath", clientTruststore.toString());
      params.put("sonar.scanner.truststorePassword", keyStorePassword);
    }

    buildResult = scanner.executeSimpleProject(project("js-sample"), "https://localhost:" + httpsPort, params, Map.of());
    System.out.println(buildResult.getLogs());
    assertThat(buildResult.getLastStatus()).isZero();
  }

  @DataProvider()
  public static Object[][] variousClientTrustStores() {
    return new Object[][] {
      {KEYSTORE_CLIENT_WITH_CA_KEYTOOL, CLIENT_WITH_CA_KEYSTORE_PASSWORD, true},
      {KEYSTORE_CLIENT_WITH_CA_OPENSSL, CLIENT_WITH_CA_KEYSTORE_PASSWORD, false},
      {KEYSTORE_CLIENT_WITH_CERTIFICATE_KEYTOOL, CLIENT_WITH_CERTIFICATE_KEYSTORE_PASSWORD, true},
      {KEYSTORE_CLIENT_WITH_CERTIFICATE_OPENSSL, CLIENT_WITH_CERTIFICATE_KEYSTORE_PASSWORD, false},
      {KEYSTORE_CLIENT_WITH_CERTIFICATE_OPENSSL_JDKTRUST, CLIENT_WITH_CERTIFICATE_KEYSTORE_PASSWORD, false},
      {KEYSTORE_CLIENT_WITH_CERTIFICATE_OPENSSL_JDKTRUST, CLIENT_WITH_CERTIFICATE_KEYSTORE_PASSWORD, true}
    };
  }
}
