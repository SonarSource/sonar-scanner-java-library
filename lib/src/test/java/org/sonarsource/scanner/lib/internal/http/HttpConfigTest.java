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
package org.sonarsource.scanner.lib.internal.http;

import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpConfigTest {

  private static final String SONAR_WS_TIMEOUT = "sonar.ws.timeout";

  private final Map<String, String> bootstrapProperties = new HashMap<>();

  @TempDir
  private Path sonarUserHomeDir;
  private Path sonarUserHome;

  @BeforeEach
  void prepareMocks() {
    this.sonarUserHome = sonarUserHomeDir;
    bootstrapProperties.clear();
  }

  @Test
  void support_custom_timeouts() {
    int readTimeoutSec = 2000;

    HttpConfig underTest = new HttpConfig(Map.of(SONAR_WS_TIMEOUT, String.valueOf(readTimeoutSec)), sonarUserHome);

    assertThat(underTest.getSocketTimeout()).isEqualTo(Duration.of(2000, ChronoUnit.SECONDS));
  }

  @Test
  void support_custom_timeouts_throws_exception_on_non_number() {
    var props = Map.of(SONAR_WS_TIMEOUT, "fail");
    assertThatThrownBy(() -> new HttpConfig(props, sonarUserHome))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage(SONAR_WS_TIMEOUT + " is not a valid integer: fail");
  }

  @Test
  void it_should_throw_if_invalid_proxy_port() {
    bootstrapProperties.put("sonar.scanner.proxyHost", "localhost");
    bootstrapProperties.put("sonar.scanner.proxyPort", "not_a_number");

    assertThatThrownBy(() -> new HttpConfig(bootstrapProperties, sonarUserHome))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("sonar.scanner.proxyPort is not a valid integer: not_a_number");
  }


}
