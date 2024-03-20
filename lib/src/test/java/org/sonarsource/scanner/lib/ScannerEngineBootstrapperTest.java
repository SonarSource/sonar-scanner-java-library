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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sonarsource.scanner.lib.internal.ClassloadRules;
import org.sonarsource.scanner.lib.internal.InternalProperties;
import org.sonarsource.scanner.lib.internal.IsolatedLauncherFactory;
import org.sonarsource.scanner.lib.internal.ScannerEngineLauncherFactory;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.cache.Logger;
import org.sonarsource.scanner.lib.internal.http.ServerConnection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.scanner.lib.ScannerEngineBootstrapper.SQ_VERSION_NEW_BOOTSTRAPPING;

@ExtendWith(MockitoExtension.class)
class ScannerEngineBootstrapperTest {

  @Mock
  private LogOutput logOutput;
  @Mock
  private ServerConnection serverConnection;
  @Mock
  private ScannerEngineLauncherFactory scannerEngineLauncherFactory;
  @Mock
  private System2 system;

  private ScannerEngineBootstrapper underTest;
  @TempDir
  private Path dumpToFolder;
  private Path dumpFile;

  @BeforeEach
  public void setUp() {
    this.dumpFile = dumpToFolder.resolve("dump.properties");

    when(system.getProperty("os.name")).thenReturn("linux_ubuntu");
    when(system.getProperty("os.arch")).thenReturn("x64");

    underTest = new ScannerEngineBootstrapper("Gradle", "3.1", logOutput, system, serverConnection,
      new IsolatedLauncherFactory(mock(Logger.class)), scannerEngineLauncherFactory);
  }

  @Test
  void should_use_new_bootstrapping_with_default_url() throws Exception {
    try (var scannerEngineFacade = underTest.bootstrap()) {
      verify(scannerEngineLauncherFactory).createLauncher(eq(serverConnection), any(FileCache.class), anyMap());
      assertThat(scannerEngineFacade.isSonarCloud()).isTrue();
    }
  }

  @Test
  void should_use_new_bootstrapping_with_sonarcloud_url() throws Exception {
    try (var scannerEngineFacade = underTest.setBootstrapProperty(ScannerProperties.HOST_URL, "https://sonarcloud.io").bootstrap()) {
      verify(scannerEngineLauncherFactory).createLauncher(eq(serverConnection), any(FileCache.class), anyMap());
      assertThat(scannerEngineFacade.isSonarCloud()).isTrue();
    }
  }

  @Test
  void should_use_new_bootstrapping_with_sonarqube_10_6() throws Exception {
    when(serverConnection.callRestApi("/analysis/version")).thenReturn(SQ_VERSION_NEW_BOOTSTRAPPING);
    try (var scannerEngineFacade = underTest.setBootstrapProperty(ScannerProperties.HOST_URL, "http://localhost").bootstrap()) {
      verify(scannerEngineLauncherFactory).createLauncher(eq(serverConnection), any(FileCache.class), anyMap());
      assertThat(scannerEngineFacade.isSonarCloud()).isFalse();
      assertThat(scannerEngineFacade.getServerVersion()).isEqualTo(SQ_VERSION_NEW_BOOTSTRAPPING);
    }
  }

  @Test
  void should_use_old_bootstrapping_with_sonarqube_10_5() throws Exception {
    IsolatedLauncherFactory launcherFactory = mock(IsolatedLauncherFactory.class);
    when(launcherFactory.createLauncher(any(ClassloadRules.class), eq(serverConnection), any(FileCache.class)))
      .thenReturn(mock(IsolatedLauncherFactory.IsolatedLauncherAndClassloader.class));

    ScannerEngineBootstrapper bootstrapper = new ScannerEngineBootstrapper("Gradle", "3.1", logOutput, system, serverConnection,
      launcherFactory, scannerEngineLauncherFactory);
    when(serverConnection.callRestApi("/analysis/version")).thenReturn("10.5");

    try (var scannerEngineFacade = bootstrapper.setBootstrapProperty(ScannerProperties.HOST_URL, "http://localhost").bootstrap()) {
      verify(launcherFactory).createLauncher(any(ClassloadRules.class), eq(serverConnection), any(FileCache.class));
      assertThat(scannerEngineFacade.isSonarCloud()).isFalse();
      assertThat(scannerEngineFacade.getServerVersion()).isEqualTo("10.5");
    }
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
  void should_set_default_platform_encoding() throws Exception {
    try (var scannerEngine = underTest
      .setBootstrapProperty(InternalProperties.SCANNER_DUMP_TO_FILE, dumpFile.toString())
      .bootstrap()) {

      scannerEngine.analyze(Map.of());

      assertThat(readDumpedProps().getProperty("sonar.sourceEncoding")).isEqualTo(Charset.defaultCharset().name());
    }
    verify(logOutput).log("Default locale: \"" + Locale.getDefault() + "\", source code encoding: \"" + Charset.defaultCharset().name() + "\" (analysis is platform dependent)", LogOutput.Level.INFO);
  }

  @Test
  void should_set_default_platform_encoding_when_empty() throws Exception {
    try (var scannerEngine = underTest
      .setBootstrapProperty(InternalProperties.SCANNER_DUMP_TO_FILE, dumpFile.toString())
      .bootstrap()) {

      scannerEngine.analyze(Map.of("sonar.sourceEncoding", ""));

      assertThat(readDumpedProps().getProperty("sonar.sourceEncoding")).isEqualTo(Charset.defaultCharset().name());
    }
    verify(logOutput).log("Default locale: \"" + Locale.getDefault() + "\", source code encoding: \"" + Charset.defaultCharset().name() + "\" (analysis is platform dependent)", LogOutput.Level.INFO);
  }

  @Test
  void should_use_parameterized_encoding() throws Exception {
    try (var scannerEngine = underTest
      .setBootstrapProperty(InternalProperties.SCANNER_DUMP_TO_FILE, dumpFile.toString())
      .bootstrap()) {

      scannerEngine.analyze(Map.of("sonar.sourceEncoding", "THE_ISO_1234"));

      assertThat(readDumpedProps().getProperty("sonar.sourceEncoding")).isEqualTo("THE_ISO_1234");
    }
  }

  @Test
  void should_set_os_and_arch() throws Exception {
    try (var scannerEngine = underTest
      .setBootstrapProperty(InternalProperties.SCANNER_DUMP_TO_FILE, dumpFile.toString())
      .bootstrap()) {

      assertThat(scannerEngine.getBootstrapProperties()).containsEntry("sonar.scanner.os", "linux");
      assertThat(scannerEngine.getBootstrapProperties()).containsEntry("sonar.scanner.arch", "x64");
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
}
