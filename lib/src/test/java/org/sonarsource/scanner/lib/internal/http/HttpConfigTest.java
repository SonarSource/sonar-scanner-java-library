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
package org.sonarsource.scanner.lib.internal.http;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.Level;
import org.sonarsource.scanner.lib.internal.util.System2;
import testutils.LogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpConfigTest {

  private static final String SONAR_WS_TIMEOUT = "sonar.ws.timeout";

  private final Map<String, String> bootstrapProperties = new HashMap<>();

  @RegisterExtension
  private final LogTester logTester = new LogTester();

  @TempDir
  private Path sonarUserHomeDir;
  private Path sonarUserHome;

  private System2 system = mock();

  @BeforeEach
  void prepareMocks() {
    this.sonarUserHome = sonarUserHomeDir;
    bootstrapProperties.clear();
    when(system.getProperty("java.home")).thenReturn(System.getProperty("java.home"));
  }

  @Test
  void support_custom_timeouts() {
    int readTimeoutSec = 2000;

    HttpConfig underTest = new HttpConfig(Map.of(SONAR_WS_TIMEOUT, String.valueOf(readTimeoutSec)), sonarUserHome, system);

    assertThat(underTest.getSocketTimeout()).isEqualTo(Duration.of(2000, ChronoUnit.SECONDS));
  }

  @Test
  void support_custom_timeouts_throws_exception_on_non_number() {
    var props = Map.of(SONAR_WS_TIMEOUT, "fail");
    assertThatThrownBy(() -> new HttpConfig(props, sonarUserHome, system))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage(SONAR_WS_TIMEOUT + " is not a valid integer: fail");
  }

  @Test
  void it_should_throw_if_invalid_proxy_port() {
    bootstrapProperties.put("sonar.scanner.proxyHost", "localhost");
    bootstrapProperties.put("sonar.scanner.proxyPort", "not_a_number");

    assertThatThrownBy(() -> new HttpConfig(bootstrapProperties, sonarUserHome, system))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("sonar.scanner.proxyPort is not a valid integer: not_a_number");
  }

  @Test
  void should_warn_if_both_login_and_token_properties_set() {
    bootstrapProperties.put("sonar.login", "mockTokenValue");
    bootstrapProperties.put("sonar.token", "mockTokenValue");

    new HttpConfig(bootstrapProperties, sonarUserHome, system);

    assertThat(logTester.logs(Level.WARN)).contains("Both 'sonar.login' and 'sonar.token' (or the 'SONAR_TOKEN' env variable) are set, but only the latter will be used.");
  }

  @Test
  void should_set_ssl_config_from_default_jvm_location() {
    logTester.setLevel(Level.DEBUG);

    var underTest = new HttpConfig(bootstrapProperties, sonarUserHome, system);

    var cacerts = Paths.get(System.getProperty("java.home"), "lib", "security", "cacerts");
    var trustStore = underTest.getSslConfig().getTrustStore();
    assertThat(trustStore.getPath()).isEqualTo(cacerts);
    assertThat(trustStore.getKeyStorePassword()).contains("changeit");
    assertThat(trustStore.isFromJvm()).isTrue();

    assertThat(logTester.logs(Level.DEBUG)).contains("Using JVM default truststore: " + cacerts);
  }

  @Test
  void should_skip_ssl_config_from_jvm_if_property_set() {
    logTester.setLevel(Level.DEBUG);
    bootstrapProperties.put("sonar.scanner.skipJvmSslConfig", "true");

    var underTest = new HttpConfig(bootstrapProperties, sonarUserHome, system);

    assertThat(underTest.getSslConfig().getTrustStore()).isNull();
    assertThat(underTest.getSslConfig().getKeyStore()).isNull();
  }

  @Test
  void should_set_ssl_config_from_jvm_system_properties(@TempDir Path tempDir) throws IOException {
    var jvmTruststore = tempDir.resolve("jvmTrust.p12");
    Files.createFile(jvmTruststore);
    var jvmKeyStore = tempDir.resolve("jvmKey.p12");
    Files.createFile(jvmKeyStore);
    when(system.getProperty("javax.net.ssl.trustStore")).thenReturn(jvmTruststore.toString());
    when(system.getProperty("javax.net.ssl.trustStorePassword")).thenReturn("jvmTrustPassword");
    when(system.getProperty("javax.net.ssl.keyStore")).thenReturn(jvmKeyStore.toString());
    when(system.getProperty("javax.net.ssl.keyStorePassword")).thenReturn("jvmKeyPassword");

    logTester.setLevel(Level.DEBUG);

    var underTest = new HttpConfig(bootstrapProperties, sonarUserHome, system);

    var trustStore = underTest.getSslConfig().getTrustStore();
    assertThat(trustStore.getPath()).isEqualTo(jvmTruststore);
    assertThat(trustStore.getKeyStorePassword()).contains("jvmTrustPassword");
    assertThat(trustStore.isFromJvm()).isTrue();

    assertThat(logTester.logs(Level.DEBUG)).contains("Using JVM truststore: " + jvmTruststore.toString());

    var keystore = underTest.getSslConfig().getKeyStore();
    assertThat(keystore.getPath()).isEqualTo(jvmKeyStore);
    assertThat(keystore.getKeyStorePassword()).contains("jvmKeyPassword");
    assertThat(keystore.isFromJvm()).isTrue();

    assertThat(logTester.logs(Level.DEBUG)).contains("Using JVM keystore: " + jvmKeyStore.toString());
  }

}
