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
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScannerEngineBootstrapperTest {

  private ScannerEngineBootstrapper underTest;
  private LogOutput logger;
  private System2 system;
  @TempDir
  private Path dumpToFolder;
  private Path dumpFile;

  @BeforeEach
  public void setUp() {
    this.dumpFile = dumpToFolder.resolve("dump.properties");
    logger = mock(LogOutput.class);
    system = mock(System2.class);

    underTest = new ScannerEngineBootstrapper("Gradle", "3.1", logger, system);
  }

  @Test
  void should_launch_in_simulation_mode() throws Exception {
    try (var scannerEngine = underTest
      .setBootstrapProperty("sonar.scanner.dumpToFile", dumpFile.toString())
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
      .setBootstrapProperty("sonar.scanner.dumpToFile", dumpFile.toString())
      .bootstrap()) {
      assertThat(scannerEngine.getBootstrapProperties()).contains(
        entry("sonar.scanner.app", "Gradle"),
        entry("sonar.scanner.appVersion", "3.1"));
    }
  }

  @Test
  void should_set_sonarcloud_as_host_by_default() throws Exception {
    try (var scannerEngine = underTest
      .setBootstrapProperty("sonar.scanner.dumpToFile", dumpFile.toString())
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
      .setBootstrapProperty("sonar.scanner.dumpToFile", dumpFile.toString())
      .setBootstrapProperty("sonar.scanner.sonarcloudUrl", "https://preprod.sonarcloud.io")
      .bootstrap()) {
      assertThat(scannerEngine.getBootstrapProperties()).contains(
        entry("sonar.host.url", "https://preprod.sonarcloud.io"));

      assertThat(scannerEngine.isSonarCloud()).isTrue();
      assertThrows(UnsupportedOperationException.class, scannerEngine::getServerVersion);
    }
  }

  @Test
  void should_set_url_from_env_as_host_if_host_env_var_provided() throws Exception {
    when(system.getEnvironmentVariable("SONAR_HOST_URL")).thenReturn("http://from-env.org:9000");

    try (var scannerEngine = underTest
      .setBootstrapProperty("sonar.scanner.dumpToFile", dumpFile.toString())
      .bootstrap()) {
      assertThat(scannerEngine.getBootstrapProperties()).contains(
        entry("sonar.host.url", "http://from-env.org:9000"));

      assertThat(scannerEngine.isSonarCloud()).isFalse();
    }
  }

  @Test
  void should_set_properties() throws Exception {
    try (var scannerEngine = underTest
      .setBootstrapProperty("sonar.scanner.dumpToFile", dumpFile.toString())
      .setBootstrapProperty("sonar.projectKey", "foo")
      .addBootstrapProperties(new HashMap<String, String>() {
        {
          put("sonar.login", "admin");
          put("sonar.password", "gniark");
        }
      })
      .bootstrap()) {
      assertThat(scannerEngine.getBootstrapProperties()).contains(
        entry("sonar.projectKey", "foo"),
        entry("sonar.login", "admin"),
        entry("sonar.password", "gniark"));
    }
  }

  @Test
  void should_set_default_platform_encoding() throws Exception {
    try (var scannerEngine = underTest
      .setBootstrapProperty("sonar.scanner.dumpToFile", dumpFile.toString())
      .bootstrap()) {

      scannerEngine.analyze(Map.of());

      assertThat(readDumpedProps().getProperty("sonar.sourceEncoding")).isEqualTo(Charset.defaultCharset().name());
    }
    verify(logger).log("Default locale: \"" + Locale.getDefault() + "\", source code encoding: \"" + Charset.defaultCharset().name() + "\" (analysis is platform dependent)", LogOutput.Level.INFO);
  }

  @Test
  void should_set_default_platform_encoding_when_empty() throws Exception {
    try (var scannerEngine = underTest
      .setBootstrapProperty("sonar.scanner.dumpToFile", dumpFile.toString())
      .bootstrap()) {

      scannerEngine.analyze(Map.of("sonar.sourceEncoding", ""));

      assertThat(readDumpedProps().getProperty("sonar.sourceEncoding")).isEqualTo(Charset.defaultCharset().name());
    }
    verify(logger).log("Default locale: \"" + Locale.getDefault() + "\", source code encoding: \"" + Charset.defaultCharset().name() + "\" (analysis is platform dependent)", LogOutput.Level.INFO);
  }

  @Test
  void should_use_parameterized_encoding() throws Exception {
    try (var scannerEngine = underTest
      .setBootstrapProperty("sonar.scanner.dumpToFile", dumpFile.toString())
      .bootstrap()) {

      scannerEngine.analyze(Map.of("sonar.sourceEncoding", "THE_ISO_1234"));

      assertThat(readDumpedProps().getProperty("sonar.sourceEncoding")).isEqualTo("THE_ISO_1234");
    }
  }

}
