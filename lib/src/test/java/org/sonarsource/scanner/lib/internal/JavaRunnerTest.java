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
package org.sonarsource.scanner.lib.internal;

import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import testutils.LogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class JavaRunnerTest {

  @RegisterExtension
  LogTester logTester = new LogTester().setLevel(Level.TRACE);

  @Test
  void execute_shouldLogProcessOutput() {
    JavaRunner runner = new JavaRunner(Paths.get("java"), JreCacheHit.DISABLED);

    runner.execute(List.of("--version"), "test");
    await().untilAsserted(() -> {
      assertThat(logTester.logs(Level.INFO)).isNotEmpty().allMatch(s -> s.startsWith("[stdout] "));
    });

    runner.execute(List.of("-version"), null);
    await().untilAsserted(() -> {
      assertThat(logTester.logs(Level.ERROR)).isNotEmpty().allMatch(s -> s.startsWith("[stderr] "));
    });
  }

  @Test
  void execute_whenInvalidRunner_shouldFail() {
    JavaRunner runner = new JavaRunner(Paths.get("invalid-runner"), JreCacheHit.DISABLED);
    List<String> command = List.of("--version");
    assertThatThrownBy(() -> runner.execute(command, "test"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Failed to run the Java command");
  }

  @Test
  void execute_shouldFailWhenBadRunner() {
    JavaRunner runner = new JavaRunner(Paths.get("java"), JreCacheHit.DISABLED);
    List<String> command = List.of("unknown-command");
    assertThatThrownBy(() -> runner.execute(command, "test"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Error returned by the Java command execution");
  }

  @Test
  void tryParse_shouldParseLogMessages() {
    JavaRunner runner = new JavaRunner(Paths.get("java"), JreCacheHit.DISABLED);

    runner.tryParse("{\n" +
      "    \"level\": \"ERROR\",\n" +
      "    \"formattedMessage\": \"Some error message\",\n" +
      "    \"throwable\": \"exception\"\n" +
      "}");
    runner.tryParse("{\"level\": \"WARN\", \"formattedMessage\": \"Some warn message\"}");
    runner.tryParse("{\"level\": \"DEBUG\", \"formattedMessage\": \"Some debug message\"}");
    runner.tryParse("{\"level\": \"TRACE\", \"formattedMessage\": \"Some trace message\"}");
    runner.tryParse("{\"level\": \"INFO\", \"formattedMessage\": \"Some info message\"}");
    runner.tryParse("{\"level\": \"UNKNOWN-LEVEL\", \"formattedMessage\": \"Some unknown level message\"}");

    assertThat(logTester.logs(Level.ERROR)).containsOnly("Some error message\nexception");
    assertThat(logTester.logs(Level.WARN)).containsOnly("Some warn message");
    assertThat(logTester.logs(Level.DEBUG)).containsOnly("Some debug message");
    assertThat(logTester.logs(Level.TRACE)).containsOnly("Some trace message");
    assertThat(logTester.logs(Level.INFO)).containsOnly("Some info message", "Some unknown level message");
  }

  @Test
  void tryParse_whenCannotParse_shouldLogInfo() {
    JavaRunner runner = new JavaRunner(Paths.get("java"), JreCacheHit.DISABLED);
    runner.tryParse("INFO: test");
    assertThat(logTester.logs(Level.INFO)).containsOnly("[stdout] INFO: test");
  }
}
