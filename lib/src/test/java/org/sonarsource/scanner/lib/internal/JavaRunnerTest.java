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

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sonarsource.scanner.lib.internal.cache.Logger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JavaRunnerTest {

  @Mock
  private Logger logger;

  @Test
  void execute_shouldLogProcessOutput() {
    JavaRunner runner = new JavaRunner(null, logger);

    runner.execute(List.of("--version"), "test");
    verify(logger, after(1000).atLeastOnce()).info(anyString());

    runner.execute(List.of("-version"), null);
    verify(logger, after(1000).atLeastOnce()).error(matches("[stderr] .*"));
  }

  @Test
  void execute_whenInvalidRunner_shouldFail() {
    JavaRunner runner = new JavaRunner(new File("invalid-runner"), logger);
    List<String> command = List.of("--version");
    assertThatThrownBy(() -> runner.execute(command, "test"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Failed to run the Java command");
  }

  @Test
  void execute_shouldFailWhenBadRunner() {
    JavaRunner runner = new JavaRunner(null, logger);
    List<String> command = List.of("unknown-command");
    assertThatThrownBy(() -> runner.execute(command, "test"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Error returned by the Java command execution");
  }

  @Test
  void tryParse_shouldParseLogMessages() {
    JavaRunner runner = new JavaRunner(null, logger);

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

    verify(logger).error("Some error message\nexception");
    verify(logger).warn("Some warn message");
    verify(logger).debug("Some debug message");
    verify(logger).trace("Some trace message");
    verify(logger).info("Some unknown level message");
  }

  @Test
  void tryParse_whenCannotParse_shouldLogInfo() {
    JavaRunner runner = new JavaRunner(null, logger);
    runner.tryParse("INFO: test");
    verify(logger).info("[stdout] INFO: test");
  }
}
