/*
 * SonarScanner Java Library
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
package org.sonarsource.scanner.lib;

import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junitpioneer.jupiter.RestoreSystemProperties;
import org.mockito.Mockito;
import org.slf4j.event.Level;
import org.sonarsource.scanner.lib.internal.InternalProperties;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.facade.forked.ScannerEngineLauncher;
import org.sonarsource.scanner.lib.internal.facade.forked.ScannerEngineLauncherFactory;
import org.sonarsource.scanner.lib.internal.facade.inprocess.IsolatedLauncherFactory;
import org.sonarsource.scanner.lib.internal.http.HttpConfig;
import org.sonarsource.scanner.lib.internal.http.HttpException;
import org.sonarsource.scanner.lib.internal.http.ScannerHttpClient;
import org.sonarsource.scanner.lib.internal.http.ssl.CertificateStore;
import org.sonarsource.scanner.lib.internal.http.ssl.SslConfig;
import org.sonarsource.scanner.lib.internal.util.System2;
import testutils.LogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.scanner.lib.ScannerEngineBootstrapper.SQ_VERSION_NEW_BOOTSTRAPPING;
import static org.sonarsource.scanner.lib.ScannerEngineBootstrapper.SQ_VERSION_TOKEN_AUTHENTICATION;

class ScannerEngineBootstrapperTest {

  @RegisterExtension
  private final LogTester logTester = new LogTester();

  private final ScannerHttpClient scannerHttpClient = mock(ScannerHttpClient.class);
  private final ScannerEngineLauncherFactory scannerEngineLauncherFactory = mock(ScannerEngineLauncherFactory.class);
  private final System2 system = mock(System2.class);

  private ScannerEngineBootstrapper underTest;
  @TempDir
  private Path dumpToFolder;
  private Path dumpFile;

  @BeforeEach
  public void setUp() {
    this.dumpFile = dumpToFolder.resolve("dump.properties");

    when(system.getProperty("os.name")).thenReturn("linux_ubuntu");
    when(system.getProperty("os.arch")).thenReturn("x64");
    when(system.getProperty("java.home")).thenReturn(System.getProperty("java.home"));

    var launcher = mock(ScannerEngineLauncher.class);
    when(scannerEngineLauncherFactory.createLauncher(any(ScannerHttpClient.class), any(FileCache.class), anyMap()))
      .thenReturn(launcher);

    underTest = new ScannerEngineBootstrapper("Gradle", "3.1", system, scannerHttpClient,
      new IsolatedLauncherFactory(), scannerEngineLauncherFactory);
  }

  @Test
  void should_use_new_bootstrapping_with_default_url() throws Exception {
    try (var bootstrapResult = underTest.bootstrap()) {
      verify(scannerEngineLauncherFactory).createLauncher(eq(scannerHttpClient), any(FileCache.class), anyMap());
      assertThat(bootstrapResult.getEngineFacade().isSonarCloud()).isTrue();
      verifyCloudServerTypeLogged();
    }
  }

  @Test
  void should_use_new_bootstrapping_with_sonarcloud_url() throws Exception {
    try (var bootstrapResult = underTest.setBootstrapProperty(ScannerProperties.HOST_URL, "https://sonarcloud.io").bootstrap()) {
      verify(scannerEngineLauncherFactory).createLauncher(eq(scannerHttpClient), any(FileCache.class), anyMap());
      assertThat(bootstrapResult.isSuccessful()).isTrue();
      assertThat(bootstrapResult.getEngineFacade().isSonarCloud()).isTrue();
      verifyCloudServerTypeLogged();
    }
  }

  @Test
  void should_use_new_bootstrapping_with_sonarqube_10_6() throws Exception {
    when(scannerHttpClient.callRestApi("/analysis/version")).thenReturn(SQ_VERSION_NEW_BOOTSTRAPPING);
    try (var bootstrapResult = underTest.setBootstrapProperty(ScannerProperties.HOST_URL, "http://localhost").bootstrap()) {
      verify(scannerEngineLauncherFactory).createLauncher(eq(scannerHttpClient), any(FileCache.class), anyMap());
      assertThat(bootstrapResult.getEngineFacade().isSonarCloud()).isFalse();
      verifySonarQubeServerTypeLogged(SQ_VERSION_NEW_BOOTSTRAPPING);
      assertThat(bootstrapResult.getEngineFacade().getServerVersion()).isEqualTo(SQ_VERSION_NEW_BOOTSTRAPPING);
      assertThat(logTester.logs(Level.WARN)).isEmpty();
    }
  }

  @Test
  void should_issue_deprecation_warning_for_sonar_login_property_sonarqube_10_0() throws Exception {
    IsolatedLauncherFactory launcherFactory = mock(IsolatedLauncherFactory.class);
    when(launcherFactory.createLauncher(eq(scannerHttpClient), any(FileCache.class)))
      .thenReturn(mock(IsolatedLauncherFactory.IsolatedLauncherAndClassloader.class));

    ScannerEngineBootstrapper bootstrapper = new ScannerEngineBootstrapper("Gradle", "3.1", system, scannerHttpClient,
      launcherFactory, scannerEngineLauncherFactory);
    when(scannerHttpClient.callRestApi("/analysis/version")).thenThrow(new HttpException(URI.create("http://myserver").toURL(), 404, "Not Found", null));
    when(scannerHttpClient.callWebApi("/api/server/version")).thenReturn(SQ_VERSION_TOKEN_AUTHENTICATION);

    try (var bootstrapResult = bootstrapper.setBootstrapProperty(ScannerProperties.HOST_URL, "http://localhost").setBootstrapProperty(ScannerProperties.SONAR_LOGIN,
      "mockTokenValue").bootstrap()) {
      verify(launcherFactory).createLauncher(eq(scannerHttpClient), any(FileCache.class));
      assertThat(bootstrapResult.getEngineFacade().isSonarCloud()).isFalse();
      assertThat(logTester.logs(Level.WARN)).contains("Use of 'sonar.login' property has been deprecated in favor of 'sonar.token' (or the env variable alternative " +
        "'SONAR_TOKEN'). Please use the latter when passing a token.");
      verifySonarQubeServerTypeLogged(SQ_VERSION_TOKEN_AUTHENTICATION);
      assertThat(bootstrapResult.getEngineFacade().getServerVersion()).isEqualTo(SQ_VERSION_TOKEN_AUTHENTICATION);
    }
  }

  @Test
  void should_log_cb_server_type() throws Exception {
    String testCBVersion = "24.12.0.23452";
    when(scannerHttpClient.callRestApi("/analysis/version")).thenReturn(testCBVersion);
    try (var bootstrapResult = underTest.setBootstrapProperty(ScannerProperties.HOST_URL, "http://localhost").bootstrap()) {
      verify(scannerEngineLauncherFactory).createLauncher(eq(scannerHttpClient), any(FileCache.class), anyMap());
      assertThat(bootstrapResult.getEngineFacade().isSonarCloud()).isFalse();
      assertThat(logTester.logs(Level.INFO)).contains("Communicating with SonarQube Community Build ".concat(testCBVersion));
      assertThat(bootstrapResult.getEngineFacade().getServerVersion()).isEqualTo(testCBVersion);
    }
  }

  @Test
  void should_use_old_bootstrapping_with_sonarqube_10_5() throws Exception {
    IsolatedLauncherFactory launcherFactory = mock(IsolatedLauncherFactory.class);
    when(launcherFactory.createLauncher(eq(scannerHttpClient), any(FileCache.class)))
      .thenReturn(mock(IsolatedLauncherFactory.IsolatedLauncherAndClassloader.class));

    ScannerEngineBootstrapper bootstrapper = new ScannerEngineBootstrapper("Gradle", "3.1", system, scannerHttpClient,
      launcherFactory, scannerEngineLauncherFactory);
    when(scannerHttpClient.callRestApi("/analysis/version")).thenThrow(new HttpException(URI.create("http://myserver").toURL(), 404, "Not Found", null));
    when(scannerHttpClient.callWebApi("/api/server/version")).thenReturn("10.5");

    try (var bootstrapResult = bootstrapper.setBootstrapProperty(ScannerProperties.HOST_URL, "http://myserver").bootstrap()) {
      verify(launcherFactory).createLauncher(eq(scannerHttpClient), any(FileCache.class));
      assertThat(bootstrapResult.getEngineFacade().isSonarCloud()).isFalse();
      assertThat(bootstrapResult.getEngineFacade().getServerVersion()).isEqualTo("10.5");
      verifySonarQubeServerTypeLogged("10.5");
    }
  }

  @Test
  void should_show_help_on_proxy_auth_error() throws Exception {
    IsolatedLauncherFactory launcherFactory = mock(IsolatedLauncherFactory.class);
    when(launcherFactory.createLauncher(eq(scannerHttpClient), any(FileCache.class)))
      .thenReturn(mock(IsolatedLauncherFactory.IsolatedLauncherAndClassloader.class));

    ScannerEngineBootstrapper bootstrapper = new ScannerEngineBootstrapper("Gradle", "3.1", system, scannerHttpClient,
      launcherFactory, scannerEngineLauncherFactory);
    when(scannerHttpClient.callRestApi("/analysis/version")).thenThrow(new HttpException(URI.create("http://myserver").toURL(), 407, "Proxy Authentication Required", null));

    logTester.setLevel(Level.DEBUG);

    try (var result = bootstrapper.setBootstrapProperty(ScannerProperties.HOST_URL, "http://myserver").bootstrap()) {
      assertThat(result.isSuccessful()).isFalse();
    }

    assertThat(logTester.logs(Level.ERROR)).contains("Failed to query server version: Proxy Authentication Required. Please check the properties sonar.scanner.proxyUser and " +
      "sonar.scanner.proxyPassword.");
    assertThatNoServerTypeIsLogged();
  }

  @Test
  void should_preserve_both_exceptions_when_checking_version() throws Exception {
    IsolatedLauncherFactory launcherFactory = mock(IsolatedLauncherFactory.class);
    when(launcherFactory.createLauncher(eq(scannerHttpClient), any(FileCache.class)))
      .thenReturn(mock(IsolatedLauncherFactory.IsolatedLauncherAndClassloader.class));

    ScannerEngineBootstrapper bootstrapper = new ScannerEngineBootstrapper("Gradle", "3.1", system, scannerHttpClient,
      launcherFactory, scannerEngineLauncherFactory);
    when(scannerHttpClient.callRestApi("/analysis/version")).thenThrow(new HttpException(URI.create("http://myserver").toURL(), 404, "Not Found", null));
    when(scannerHttpClient.callWebApi("/api/server/version")).thenThrow(new HttpException(URI.create("http://myserver").toURL(), 400, "Server Error", null));

    logTester.setLevel(Level.DEBUG);

    try (var result = bootstrapper.setBootstrapProperty(ScannerProperties.HOST_URL, "http://myserver").bootstrap()) {
      assertThat(result.isSuccessful()).isFalse();
    }

    var loggedError = logTester.logEvents(Level.ERROR);
    assertThat(loggedError).hasSize(1);
    assertThat(loggedError.get(0).getFormattedMessage()).contains("Failed to query server version: Server Error");
    assertThat(ThrowableProxyUtil.asString(loggedError.get(0).getThrowableProxy()))
      .containsSubsequence(
        "Suppressed: org.sonarsource.scanner.lib.internal.http.HttpException: Not Found",
        "Caused by: org.sonarsource.scanner.lib.internal.http.HttpException: Server Error");
    assertThatNoServerTypeIsLogged();
  }


  @ParameterizedTest
  @ValueSource(ints = {401, 403})
  void should_log_user_friendly_message_when_auth_error(int code) throws Exception {
    IsolatedLauncherFactory launcherFactory = mock(IsolatedLauncherFactory.class);
    when(launcherFactory.createLauncher(eq(scannerHttpClient), any(FileCache.class)))
      .thenReturn(mock(IsolatedLauncherFactory.IsolatedLauncherAndClassloader.class));

    ScannerEngineBootstrapper bootstrapper = new ScannerEngineBootstrapper("Gradle", "3.1", system, scannerHttpClient,
      launcherFactory, scannerEngineLauncherFactory);
    when(scannerHttpClient.callRestApi(anyString())).thenThrow(new HttpException(URI.create("http://myserver").toURL(), code, "Unauthorized", null));
    when(scannerHttpClient.callWebApi(anyString())).thenThrow(new HttpException(URI.create("http://myserver").toURL(), code, "Unauthorized", null));

    try (var result = bootstrapper.setBootstrapProperty(ScannerProperties.HOST_URL, "http://localhost").bootstrap()) {
      assertThat(result.isSuccessful()).isFalse();
    }

    assertThat(logTester.logs(Level.ERROR)).contains("Failed to query server version: Unauthorized. Please check the property sonar.token or the environment variable SONAR_TOKEN" +
      ".");
    assertThatNoServerTypeIsLogged();
  }

  @Test
  void should_launch_in_simulation_mode() throws Exception {
    try (var bootstrapResult = underTest
      .setBootstrapProperty(InternalProperties.SCANNER_DUMP_TO_FILE, dumpFile.toString())
      .bootstrap()) {

      bootstrapResult.getEngineFacade().analyze(Map.of("sonar.projectKey", "foo"));

      assertThat(readDumpedProps().getProperty("sonar.projectKey")).isEqualTo("foo");
    }
  }

  private Properties readDumpedProps() throws IOException {
    Properties props = new Properties();
    props.load(Files.newInputStream(dumpFile));
    return props;
  }

  @Test
  void test_app() throws Exception {
    try (var bootstrapResult = underTest
      .setBootstrapProperty(InternalProperties.SCANNER_DUMP_TO_FILE, dumpFile.toString())
      .bootstrap()) {
      assertThat(bootstrapResult.getEngineFacade().getBootstrapProperties()).contains(
        entry("sonar.scanner.app", "Gradle"),
        entry("sonar.scanner.appVersion", "3.1"));
    }
  }

  @Test
  void should_set_sonarcloud_as_host_by_default() throws Exception {
    try (var bootstrapResult = underTest
      .setBootstrapProperty(InternalProperties.SCANNER_DUMP_TO_FILE, dumpFile.toString())
      .bootstrap()) {
      assertThat(bootstrapResult.getEngineFacade().getBootstrapProperties()).contains(
        entry("sonar.host.url", "https://sonarcloud.io"));

      assertThat(bootstrapResult.getEngineFacade().isSonarCloud()).isTrue();
      assertThrows(UnsupportedOperationException.class, bootstrapResult.getEngineFacade()::getServerVersion);
    }
  }

  @Test
  void should_use_sonarcloud_url_from_property() throws Exception {
    try (var bootstrapResult = underTest
      .setBootstrapProperty(InternalProperties.SCANNER_DUMP_TO_FILE, dumpFile.toString())
      .setBootstrapProperty("sonar.scanner.sonarcloudUrl", "https://preprod.sonarcloud.io")
      .bootstrap()) {
      assertThat(bootstrapResult.getEngineFacade().getBootstrapProperties()).contains(
        entry("sonar.host.url", "https://preprod.sonarcloud.io"));

      assertThat(bootstrapResult.getEngineFacade().isSonarCloud()).isTrue();
      assertThrows(UnsupportedOperationException.class, bootstrapResult.getEngineFacade()::getServerVersion);
    }
  }

  @Test
  void should_set_sonarqube_api_url_and_remove_trailing_slash() throws Exception {
    try (var bootstrapResult = underTest
      .setBootstrapProperty(InternalProperties.SCANNER_DUMP_TO_FILE, dumpFile.toString())
      .setBootstrapProperty(ScannerProperties.HOST_URL, "http://localhost/")
      .bootstrap()) {

      assertThat(bootstrapResult.getEngineFacade().getBootstrapProperties()).contains(
        entry(ScannerProperties.API_BASE_URL, "http://localhost/api/v2"));
      assertThat(bootstrapResult.getEngineFacade().isSonarCloud()).isFalse();
    }
  }

  @Test
  void should_set_properties() throws Exception {
    try (var scannerEngine = underTest
      .setBootstrapProperty(InternalProperties.SCANNER_DUMP_TO_FILE, dumpFile.toString())
      .setBootstrapProperty("sonar.projectKey", "foo")
      .setBootstrapProperty("sonar.host.url", "http://localhost")
      .addBootstrapProperties(new HashMap<String, String>() {
        {
          put("sonar.login", "admin");
          put("sonar.password", "gniark");
        }
      })
      .bootstrap()) {
      assertThat(scannerEngine.getEngineFacade().getBootstrapProperties()).contains(
        entry("sonar.projectKey", "foo"),
        entry("sonar.host.url", "http://localhost"),
        entry("sonar.login", "admin"),
        entry("sonar.password", "gniark"));
    }
  }

  @Test
  void should_set_os_and_arch() throws Exception {
    try (var scannerEngine = underTest
      .setBootstrapProperty(InternalProperties.SCANNER_DUMP_TO_FILE, dumpFile.toString())
      .bootstrap()) {

      assertThat(scannerEngine.getEngineFacade().getBootstrapProperties()).containsEntry("sonar.scanner.os", "linux");
      assertThat(scannerEngine.getEngineFacade().getBootstrapProperties()).containsKey("sonar.scanner.arch");
    }
  }

  @Test
  void should_not_override_os_and_arch_when_passed() throws Exception {
    Mockito.reset(system);
    when(system.getProperty("java.home")).thenReturn(System.getProperty("java.home"));

    try (var scannerEngine = underTest
      .setBootstrapProperty(InternalProperties.SCANNER_DUMP_TO_FILE, dumpFile.toString())
      .setBootstrapProperty(ScannerProperties.SCANNER_OS, "some-os")
      .setBootstrapProperty(ScannerProperties.SCANNER_ARCH, "some-arch")
      .bootstrap()) {

      assertThat(scannerEngine.getEngineFacade().getBootstrapProperties()).containsEntry("sonar.scanner.os", "some-os");
      assertThat(scannerEngine.getEngineFacade().getBootstrapProperties()).containsEntry("sonar.scanner.arch", "some-arch");
    }
  }

  @Test
  void should_set_deprecated_timeout_property() {
    var httpConfig = mock(HttpConfig.class);
    when(httpConfig.getSslConfig()).thenReturn(new SslConfig(null, null));

    when(httpConfig.getSocketTimeout()).thenReturn(Duration.ofSeconds(100));

    var adapted = underTest.adaptDeprecatedPropertiesForInProcessBootstrapping(Map.of(), httpConfig);
    assertThat(adapted).containsEntry("sonar.ws.timeout", "100");
  }

  @Test
  @RestoreSystemProperties
  void should_set_deprecated_proxy_properties() {
    var httpConfig = mock(HttpConfig.class);
    when(httpConfig.getSslConfig()).thenReturn(new SslConfig(null, null));
    when(httpConfig.getSocketTimeout()).thenReturn(Duration.ofSeconds(10));

    when(httpConfig.getProxy()).thenReturn(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("myproxy", 8080)));

    underTest.adaptDeprecatedPropertiesForInProcessBootstrapping(Map.of(), httpConfig);

    assertThat(System.getProperties()).contains(
      entry("http.proxyHost", "myproxy"),
      entry("https.proxyHost", "myproxy"),
      entry("http.proxyPort", "8080"),
      entry("https.proxyPort", "8080"));
  }

  @Test
  @RestoreSystemProperties
  void should_set_deprecated_ssl_properties() {
    var httpConfig = mock(HttpConfig.class);
    when(httpConfig.getSocketTimeout()).thenReturn(Duration.ofSeconds(10));

    when(httpConfig.getSslConfig())
      .thenReturn(new SslConfig(
        new CertificateStore(Paths.get("some", "keystore.p12"), "keystorePass", true),
        new CertificateStore(Paths.get("some", "truststore.p12"), "truststorePass", true)));

    underTest.adaptDeprecatedPropertiesForInProcessBootstrapping(Map.of(), httpConfig);

    assertThat(System.getProperties()).contains(
      entry("javax.net.ssl.keyStore", Paths.get("some", "keystore.p12").toString()),
      entry("javax.net.ssl.keyStorePassword", "keystorePass"),
      entry("javax.net.ssl.trustStore", Paths.get("some", "truststore.p12").toString()),
      entry("javax.net.ssl.trustStorePassword", "truststorePass"));
  }

  @Test
  void should_not_set_ssl_properties_if_no_ssl_config() {
    var httpConfig = mock(HttpConfig.class);

    when(httpConfig.getSslConfig())
      .thenReturn(new SslConfig(null, null));

    var adapted = ScannerEngineBootstrapper.adaptSslPropertiesToScannerProperties(Map.of(), httpConfig);

    assertThat(adapted).isEmpty();
  }

  @Test
  void should_not_set_ssl_properties_if_ssl_config_not_from_jvm() {
    var httpConfig = mock(HttpConfig.class);

    when(httpConfig.getSslConfig())
      .thenReturn(new SslConfig(
        new CertificateStore(Paths.get("some", "keystore.p12"), "keystorePass", false),
        new CertificateStore(Paths.get("some", "truststore.p12"), "truststorePass", false)));

    var adapted = ScannerEngineBootstrapper.adaptSslPropertiesToScannerProperties(Map.of(), httpConfig);

    assertThat(adapted).isEmpty();
  }

  @Test
  void should_set_ssl_properties_if_ssl_config_from_jvm() {
    var httpConfig = mock(HttpConfig.class);
    when(httpConfig.getSocketTimeout()).thenReturn(Duration.ofSeconds(10));

    when(httpConfig.getSslConfig())
      .thenReturn(new SslConfig(
        new CertificateStore(Paths.get("some", "keystore.p12"), "keystorePass", true),
        new CertificateStore(Paths.get("some", "truststore.p12"), "truststorePass", true)));

    var adapted = ScannerEngineBootstrapper.adaptSslPropertiesToScannerProperties(Map.of(), httpConfig);

    assertThat(adapted).contains(
      entry("sonar.scanner.keystorePath", Paths.get("some", "keystore.p12").toString()),
      entry("sonar.scanner.keystorePassword", "keystorePass"),
      entry("sonar.scanner.truststorePath", Paths.get("some", "truststore.p12").toString()),
      entry("sonar.scanner.truststorePassword", "truststorePass"));
  }

  private void verifyCloudServerTypeLogged() {
    assertThat(logTester.logs(Level.INFO)).contains("Communicating with SonarQube Cloud");
  }

  private void verifySonarQubeServerTypeLogged(String version) {
    assertThat(logTester.logs(Level.INFO)).contains("Communicating with SonarQube Server ".concat(version));
  }

  private void assertThatNoServerTypeIsLogged() {
    assertThat(logTester.logs(Level.INFO).stream().filter(logMessage -> logMessage.startsWith("Communicating with")).collect(Collectors.toList())).isEmpty();
  }

}
