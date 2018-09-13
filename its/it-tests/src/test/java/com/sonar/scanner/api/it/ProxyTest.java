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
import com.sonar.scanner.api.it.tools.ProxyAuthenticator;
import com.sonar.scanner.api.it.tools.SimpleScanner;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProxyTest {

  private static final String PROXY_USER = "scott";
  private static final String PROXY_PASSWORD = "tiger";
  private static Server server;
  private static int httpProxyPort;

  @ClassRule
  public static final Orchestrator ORCHESTRATOR = ScannerApiTestSuite.ORCHESTRATOR;

  private static ConcurrentLinkedDeque<String> seenByProxy = new ConcurrentLinkedDeque<>();

  @Before
  public void deleteData() {
    ORCHESTRATOR.resetData();
    seenByProxy.clear();
  }

  @After
  public void stopProxy() throws Exception {
    if (server != null && server.isStarted()) {
      server.stop();
    }
  }

  private static void startProxy(boolean needProxyAuth) throws Exception {
    httpProxyPort = NetworkUtils.getNextAvailablePort(InetAddress.getLocalHost());

    // Setup Threadpool
    QueuedThreadPool threadPool = new QueuedThreadPool();
    threadPool.setMaxThreads(500);

    server = new Server(threadPool);

    // HTTP Configuration
    HttpConfiguration httpConfig = new HttpConfiguration();
    httpConfig.setSecureScheme("https");
    httpConfig.setSendServerVersion(true);
    httpConfig.setSendDateHeader(false);

    // Handler Structure
    HandlerCollection handlers = new HandlerCollection();
    handlers.setHandlers(new Handler[] {proxyHandler(needProxyAuth), new DefaultHandler()});
    server.setHandler(handlers);

    ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
    http.setPort(httpProxyPort);
    server.addConnector(http);

    server.start();
  }

  private static ServletContextHandler proxyHandler(boolean needProxyAuth) {
    ServletContextHandler contextHandler = new ServletContextHandler();
    if (needProxyAuth) {
      contextHandler.setSecurityHandler(basicAuth(PROXY_USER, PROXY_PASSWORD, "Private!"));
    }
    contextHandler.setServletHandler(newServletHandler());
    return contextHandler;
  }

  private static final SecurityHandler basicAuth(String username, String password, String realm) {

    HashLoginService l = new HashLoginService();
    l.putUser(username, Credential.getCredential(password), new String[] {"user"});
    l.setName(realm);

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
    assertThat(buildResult.getLastStatus()).isEqualTo(0);
    assertThat(seenByProxy).isEmpty();

    Map<String, String> params = new HashMap<>();
    // By default no request to localhost will use proxy
    params.put("http.nonProxyHosts", "");
    params.put("http.proxyHost", "localhost");
    params.put("http.proxyPort", "" + httpProxyPort);

    buildResult = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl(), params);
    assertThat(buildResult.getLastStatus()).isEqualTo(0);
    assertThat(seenByProxy).isNotEmpty();
  }

  @Test
  public void simple_analysis_with_proxy_auth() throws Exception {
    startProxy(true);
    SimpleScanner scanner = new SimpleScanner();

    Map<String, String> params = new HashMap<>();
    // By default no request to localhost will use proxy
    params.put("http.nonProxyHosts", "");
    params.put("http.proxyHost", "localhost");
    params.put("http.proxyPort", "" + httpProxyPort);

    BuildResult buildResult = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl(), params);
    assertThat(buildResult.getLastStatus()).isEqualTo(1);
    assertThat(buildResult.getLogs()).contains("Status returned by url", "is not valid: [407]");
    assertThat(seenByProxy).isEmpty();

    params.put("http.proxyUser", PROXY_USER);
    params.put("http.proxyPassword", PROXY_PASSWORD);
    buildResult = scanner.executeSimpleProject(project("js-sample"), ORCHESTRATOR.getServer().getUrl(), params);
    assertThat(seenByProxy).isNotEmpty();
    if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(6, 1)) {
      assertThat(buildResult.getLastStatus()).isEqualTo(0);
    }
  }

}
