/*
 * SonarScanner Java Library
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
package org.sonarsource.scanner.lib;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import testutils.LogTester;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvironmentConfigTest {

  @RegisterExtension
  LogTester logTester = new LogTester();

  @Test
  void shouldProcessSpecificEnvVariables() {
    var inputProperties = EnvironmentConfig.load(
      Map.of("SONAR_HOST_URL", "http://foo",
        "SONAR_USER_HOME", "my/user/home",
        "SONAR_TOKEN", "myToken",
        "SONAR_REGION", "myRegion"));

    assertThat(inputProperties).containsOnly(
      entry("sonar.host.url", "http://foo"),
      entry("sonar.userHome", "my/user/home"),
      entry("sonar.token", "myToken"),
      entry("sonar.region", "myRegion"));
  }

  @Test
  void shouldProcessGenericEnvVariables() {
    var inputProperties = EnvironmentConfig.load(
      Map.of("SONAR_SCANNER", "ignored",
        "SONAR_SCANNER_", "ignored as well",
        "SONAR_SCANNER_FOO", "bar",
        "SONAR_SCANNER_FOO_BAZ", "bar",
        "SONAR_SCANNER_fuZz_bAz", "env vars are case insensitive"));

    assertThat(inputProperties).containsOnly(
      entry("sonar.scanner.foo", "bar"),
      entry("sonar.scanner.fooBaz", "bar"),
      entry("sonar.scanner.fuzzBaz", "env vars are case insensitive"));
  }

  @Test
  void shouldProcessJsonEnvVariables() {
    var inputProperties = EnvironmentConfig.load(
      Map.of("SONAR_SCANNER_JSON_PARAMS",
        "{\"key1\":\"value1\", \"key2\":\"value2\"}"));

    assertThat(inputProperties).containsOnly(
      entry("key1", "value1"),
      entry("key2", "value2"));
  }

  @Test
  void ignoreEmptyValueForJsonEnv() {
    var inputProperties = EnvironmentConfig.load(
      Map.of("SONAR_SCANNER_JSON_PARAMS", ""));

    assertThat(inputProperties).isEmpty();
  }

  @Test
  void throwIfInvalidFormat() {
    var env = Map.of("SONAR_SCANNER_JSON_PARAMS", "{garbage");
    var thrown = assertThrows(IllegalArgumentException.class, () -> EnvironmentConfig.load(env));

    assertThat(thrown).hasMessage("Failed to parse JSON properties from environment variable 'SONAR_SCANNER_JSON_PARAMS'");
  }

  @Test
  void jsonEnvVariablesShouldNotOverrideEnvSpecificProperties() {
    var inputProperties = EnvironmentConfig.load(
      Map.of("SONAR_HOST_URL", "http://foo",
        "SONAR_SCANNER_JSON_PARAMS",
        "{\"sonar.host.url\":\"should not override\", \"key2\":\"value2\"}"));

    assertThat(inputProperties).containsOnly(
      entry("sonar.host.url", "http://foo"),
      entry("key2", "value2"));

    assertThat(logTester.logs(Level.WARN)).containsOnly("Ignoring property 'sonar.host.url' from env variable 'SONAR_SCANNER_JSON_PARAMS' because it is already defined");
  }

  @Test
  void jsonEnvVariablesShouldNotOverrideGenericEnv() {
    var inputProperties = EnvironmentConfig.load(
      Map.of("SONAR_SCANNER_FOO", "value1",
        "SONAR_SCANNER_JSON_PARAMS", "{\"sonar.scanner.foo\":\"should not override\", \"key2\":\"value2\"}"));

    assertThat(inputProperties).containsOnly(
      entry("sonar.scanner.foo", "value1"),
      entry("key2", "value2"));

    assertThat(logTester.logs(Level.WARN)).containsOnly("Ignoring property 'sonar.scanner.foo' from env variable 'SONAR_SCANNER_JSON_PARAMS' because it is already defined");
  }

  @Test
  void shouldProcessOldJsonEnvVariables() {
    var inputProperties = EnvironmentConfig.load(
      Map.of("SONARQUBE_SCANNER_PARAMS",
        "{\"key1\":\"value1\", \"key2\":\"value2\"}"));

    assertThat(inputProperties).containsOnly(
      entry("key1", "value1"),
      entry("key2", "value2"));
  }

  @Test
  void oldJsonEnvVariablesIsIgnoredIfNewIsDefinedAndLogAWarning() {
    var inputProperties = EnvironmentConfig.load(
      Map.of("SONARQUBE_SCANNER_PARAMS", "{\"key1\":\"should not override\", \"key3\":\"value3\"}",
        "SONAR_SCANNER_JSON_PARAMS", "{\"key1\":\"value1\", \"key2\":\"value2\"}"));

    assertThat(inputProperties).containsOnly(
      entry("key1", "value1"),
      entry("key2", "value2"));

    assertThat(logTester.logs(Level.WARN)).containsOnly("Ignoring environment variable 'SONARQUBE_SCANNER_PARAMS' because 'SONAR_SCANNER_JSON_PARAMS' is set");
  }

  @Test
  void oldJsonEnvVariablesIsIgnoredIfNewIsDefinedButDontLogIfSameValue() {
    var inputProperties = EnvironmentConfig.load(
      Map.of("SONARQUBE_SCANNER_PARAMS", "{\"key1\":\"value1\", \"key2\":\"value2\"}",
        "SONAR_SCANNER_JSON_PARAMS", "{\"key1\":\"value1\", \"key2\":\"value2\"}"));

    assertThat(inputProperties).containsOnly(
      entry("key1", "value1"),
      entry("key2", "value2"));

    assertThat(logTester.logs()).isEmpty();
  }

}
