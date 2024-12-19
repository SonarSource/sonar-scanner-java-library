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
package org.sonarsource.scanner.lib;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junitpioneer.jupiter.RestoreSystemProperties;
import org.mockito.Mockito;
import org.sonarsource.scanner.lib.internal.InternalProperties;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.facade.forked.ScannerEngineLauncher;
import org.sonarsource.scanner.lib.internal.facade.forked.ScannerEngineLauncherFactory;
import org.sonarsource.scanner.lib.internal.facade.inprocess.IsolatedLauncherFactory;
import org.sonarsource.scanner.lib.internal.http.HttpConfig;
import org.sonarsource.scanner.lib.internal.http.ScannerHttpClient;
import org.sonarsource.scanner.lib.internal.http.ssl.CertificateStore;
import org.sonarsource.scanner.lib.internal.http.ssl.SslConfig;
import org.sonarsource.scanner.lib.internal.util.System2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.scanner.lib.ScannerEngineBootstrapper.SQ_VERSION_NEW_BOOTSTRAPPING;

class ScannerEngineBootstrapperTest {

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

    var launcher = mock(ScannerEngineLauncher.class);
    when(scannerEngineLauncherFactory.createLauncher(any(ScannerHttpClient.class), any(FileCache.class), anyMap()))
      .thenReturn(launcher);

    underTest = new ScannerEngineBootstrapper("Gradle", "3.1", system, scannerHttpClient,
      new IsolatedLauncherFactory(), scannerEngineLauncherFactory);
  }

  @Test
  void should_use_new_bootstrapping_with_default_url() throws Exception {
    try (var scannerEngineFacade = underTest.bootstrap()) {
      verify(scannerEngineLauncherFactory).createLauncher(eq(scannerHttpClient), any(FileCache.class), anyMap());
      assertThat(scannerEngineFacade.isSonarCloud()).isTrue();
    }
  }

  @Test
  void should_use_new_bootstrapping_with_sonarcloud_url() throws Exception {
    try (var scannerEngineFacade = underTest.setBootstrapProperty(ScannerProperties.HOST_URL, "https://sonarcloud.io").bootstrap()) {
      verify(scannerEngineLauncherFactory).createLauncher(eq(scannerHttpClient), any(FileCache.class), anyMap());
      assertThat(scannerEngineFacade.isSonarCloud()).isTrue();
    }
  }

  @Test
  void should_use_new_bootstrapping_with_sonarqube_10_6() throws Exception {
    when(scannerHttpClient.callRestApi("/analysis/version")).thenReturn(SQ_VERSION_NEW_BOOTSTRAPPING);
    try (var scannerEngineFacade = underTest.setBootstrapProperty(ScannerProperties.HOST_URL, "http://localhost").bootstrap()) {
      verify(scannerEngineLauncherFactory).createLauncher(eq(scannerHttpClient), any(FileCache.class), anyMap());
      assertThat(scannerEngineFacade.isSonarCloud()).isFalse();
      assertThat(scannerEngineFacade.getServerVersion()).isEqualTo(SQ_VERSION_NEW_BOOTSTRAPPING);
    }
  }

  @Test
  void should_use_old_bootstrapping_with_sonarqube_10_5() throws Exception {
    IsolatedLauncherFactory launcherFactory = mock(IsolatedLauncherFactory.class);
    when(launcherFactory.createLauncher(eq(scannerHttpClient), any(FileCache.class)))
      .thenReturn(mock(IsolatedLauncherFactory.IsolatedLauncherAndClassloader.class));

    ScannerEngineBootstrapper bootstrapper = new ScannerEngineBootstrapper("Gradle", "3.1", system, scannerHttpClient,
      launcherFactory, scannerEngineLauncherFactory);
    when(scannerHttpClient.callRestApi("/analysis/version")).thenThrow(new IOException("404 Not found"));
    when(scannerHttpClient.callWebApi("/api/server/version")).thenReturn("10.5");

    try (var scannerEngineFacade = bootstrapper.setBootstrapProperty(ScannerProperties.HOST_URL, "http://localhost").bootstrap()) {
      verify(launcherFactory).createLauncher(eq(scannerHttpClient), any(FileCache.class));
      assertThat(scannerEngineFacade.isSonarCloud()).isFalse();
      assertThat(scannerEngineFacade.getServerVersion()).isEqualTo("10.5");
    }
  }

  @Test
  void should_preserve_both_exceptions_when_checking_version() throws Exception {
    IsolatedLauncherFactory launcherFactory = mock(IsolatedLauncherFactory.class);
    when(launcherFactory.createLauncher(eq(scannerHttpClient), any(FileCache.class)))
      .thenReturn(mock(IsolatedLauncherFactory.IsolatedLauncherAndClassloader.class));

    ScannerEngineBootstrapper bootstrapper = new ScannerEngineBootstrapper("Gradle", "3.1", system, scannerHttpClient,
      launcherFactory, scannerEngineLauncherFactory);
    when(scannerHttpClient.callRestApi("/analysis/version")).thenThrow(new IOException("404 Not found"));
    when(scannerHttpClient.callWebApi("/api/server/version")).thenThrow(new IOException("400 Server Error"));

    assertThatThrownBy(() -> {
      try (var ignored = bootstrapper.setBootstrapProperty(ScannerProperties.HOST_URL, "http://localhost").bootstrap()) {
        // Should throw
      }
    })
      .hasMessage("Failed to get server version")
      .hasStackTraceContaining("400 Server Error", "404 Not found");
  }

  @Test
  void should_launch_in_simulation_mode() throws Exception {
    try (var scannerEngine = underTest
      .setBootstrapProperty(InternalProperties.SCANNER_DUMP_TO_FILE, dumpFile.toString())
      .bootstrap()) {

      scannerEngine.analyze(Map.of("sonar.projectKey", "foo"));

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
    try (var scannerEngine = underTest
      .setBootstrapProperty(InternalProperties.SCANNER_DUMP_TO_FILE, dumpFile.toString())
      .bootstrap()) {
      assertThat(scannerEngine.getBootstrapProperties()).contains(
        entry("sonar.scanner.app", "Gradle"),
        entry("sonar.scanner.appVersion", "3.1"));
    }
  }

  @Test
  void should_set_sonarcloud_as_host_by_default() throws Exception {
    try (var scannerEngine = underTest
      .setBootstrapProperty(InternalProperties.SCANNER_DUMP_TO_FILE, dumpFile.toString())
      .bootstrap()) {
      assertThat(scannerEngine.getBootstrapProperties()).contains(
        entry("sonar.host.url", "https://sonarcloud.io"));

      assertThat(scannerEngine.isSonarCloud()).isTrue();
      assertThrows(UnsupportedOperationException.class, scannerEngine::getServerVersion);
    }
  }

  @Test
  void should_use_sonarcloud_url_from_property() throws Exception {
    try (var scannerEngine = underTest
      .setBootstrapProperty(InternalProperties.SCANNER_DUMP_TO_FILE, dumpFile.toString())
      .setBootstrapProperty("sonar.scanner.sonarcloudUrl", "https://preprod.sonarcloud.io")
      .bootstrap()) {
      assertThat(scannerEngine.getBootstrapProperties()).contains(
        entry("sonar.host.url", "https://preprod.sonarcloud.io"));

      assertThat(scannerEngine.isSonarCloud()).isTrue();
      assertThrows(UnsupportedOperationException.class, scannerEngine::getServerVersion);
    }
  }

  @Test
  void should_set_sonarqube_api_url_and_remove_trailing_slash() throws Exception {
    try (var scannerEngine = underTest
      .setBootstrapProperty(InternalProperties.SCANNER_DUMP_TO_FILE, dumpFile.toString())
      .setBootstrapProperty(ScannerProperties.HOST_URL, "http://localhost/")
      .bootstrap()) {

      assertThat(scannerEngine.getBootstrapProperties()).contains(
        entry(ScannerProperties.API_BASE_URL, "http://localhost/api/v2"));
      assertThat(scannerEngine.isSonarCloud()).isFalse();
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
      assertThat(scannerEngine.getBootstrapProperties()).contains(
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

      assertThat(scannerEngine.getBootstrapProperties()).containsEntry("sonar.scanner.os", "linux");
      assertThat(scannerEngine.getBootstrapProperties()).containsKey("sonar.scanner.arch");
    }
  }

  @Test
  void should_not_override_os_and_arch_when_passed() throws Exception {
    Mockito.reset(system);

    try (var scannerEngine = underTest
      .setBootstrapProperty(InternalProperties.SCANNER_DUMP_TO_FILE, dumpFile.toString())
      .setBootstrapProperty(ScannerProperties.SCANNER_OS, "some-os")
      .setBootstrapProperty(ScannerProperties.SCANNER_ARCH, "some-arch")
      .bootstrap()) {

      assertThat(scannerEngine.getBootstrapProperties()).containsEntry("sonar.scanner.os", "some-os");
      assertThat(scannerEngine.getBootstrapProperties()).containsEntry("sonar.scanner.arch", "some-arch");
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
        new CertificateStore(Paths.get("some/keystore.p12"), "keystorePass"),
        new CertificateStore(Paths.get("some/truststore.p12"), "truststorePass")));

    underTest.adaptDeprecatedPropertiesForInProcessBootstrapping(Map.of(), httpConfig);

    assertThat(System.getProperties()).contains(
      entry("javax.net.ssl.keyStore", "some/keystore.p12"),
      entry("javax.net.ssl.keyStorePassword", "keystorePass"),
      entry("javax.net.ssl.trustStore", "some/truststore.p12"),
      entry("javax.net.ssl.trustStorePassword", "truststorePass"));
  }

  @Test
  void should_set_ssl_properties_from_default_jvm_location() {
    Map<String, String> properties = new HashMap<>();

    ScannerEngineBootstrapper.adaptJvmSslPropertiesToScannerProperties(properties, system);

    assertThat(properties).contains(
      entry("sonar.scanner.truststorePath", Paths.get(System.getProperty("java.home"), "lib", "security", "cacerts").toString()),
      entry("sonar.scanner.truststorePassword", "changeit"));
  }

  @Test
  void should_set_ssl_properties_from_jvm_system_properties(@TempDir Path tempDir) throws IOException {
    var jvmTruststore = tempDir.resolve("jvmTrust.p12");
    Files.createFile(jvmTruststore);
    var jvmKeyStore = tempDir.resolve("jvmKey.p12");
    Files.createFile(jvmKeyStore);
    when(system.getProperty("javax.net.ssl.trustStore")).thenReturn(jvmTruststore.toString());
    when(system.getProperty("javax.net.ssl.trustStorePassword")).thenReturn("jvmTrustPassword");
    when(system.getProperty("javax.net.ssl.keyStore")).thenReturn(jvmKeyStore.toString());
    when(system.getProperty("javax.net.ssl.keyStorePassword")).thenReturn("jvmKeyPassword");

    Map<String, String> properties = new HashMap<>();

    ScannerEngineBootstrapper.adaptJvmSslPropertiesToScannerProperties(properties, system);

    assertThat(properties).containsOnly(
      entry("sonar.scanner.truststorePath", jvmTruststore.toString()),
      entry("sonar.scanner.truststorePassword", "jvmTrustPassword"),
      entry("sonar.scanner.keystorePath", jvmKeyStore.toString()),
      entry("sonar.scanner.keystorePassword", "jvmKeyPassword"));
  }

  @Test
  void should_not_change_ssl_properties_if_already_set_as_scanner_props(@TempDir Path tempDir) throws IOException {
    var jvmTruststore = tempDir.resolve("jvmTrust.p12");
    Files.createFile(jvmTruststore);
    var jvmKeyStore = tempDir.resolve("jvmKey.p12");
    Files.createFile(jvmKeyStore);
    when(system.getProperty("javax.net.ssl.trustStore")).thenReturn(jvmTruststore.toString());
    when(system.getProperty("javax.net.ssl.trustStorePassword")).thenReturn("jvmTrustPassword");
    when(system.getProperty("javax.net.ssl.keyStore")).thenReturn(jvmKeyStore.toString());
    when(system.getProperty("javax.net.ssl.keyStorePassword")).thenReturn("jvmKeyPassword");

    var scannerTruststore = tempDir.resolve("truststore.p12");
    Files.createFile(scannerTruststore);
    var scannerKeystore = tempDir.resolve("keystore.p12");
    Files.createFile(scannerKeystore);

    var properties = Map.of("sonar.scanner.truststorePath", scannerTruststore.toString(),
      "sonar.scanner.truststorePassword", "scannerTrustPassword",
      "sonar.scanner.keystorePath", scannerTruststore.toString(),
      "sonar.scanner.keystorePassword", "scannerKeyPassword");

    var mutableProps = new HashMap<>(properties);

    ScannerEngineBootstrapper.adaptJvmSslPropertiesToScannerProperties(mutableProps, system);

    assertThat(mutableProps).containsExactlyInAnyOrderEntriesOf(properties);
  }

}
