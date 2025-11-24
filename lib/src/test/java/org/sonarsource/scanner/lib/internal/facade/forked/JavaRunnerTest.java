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
package org.sonarsource.scanner.lib.internal.facade.forked;

import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import testutils.LogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sonarsource.scanner.lib.internal.facade.forked.JavaRunner.JRE_VERSION_ERROR;

class JavaRunnerTest {

  @RegisterExtension
  LogTester logTester = new LogTester().setLevel(Level.TRACE);

  private final ConcurrentLinkedDeque<String> stdOut = new ConcurrentLinkedDeque<>();

  @Test
  void execute_shouldConsummeProcessStdOut() {
    JavaRunner runner = new JavaRunner(Paths.get("java"), JreCacheHit.DISABLED);

    // java --version is printing to stdout
    assertThat(runner.execute(List.of("--version"), "test", stdOut::add)).isTrue();

    assertThat(stdOut).isNotEmpty();
    assertThat(logTester.logs(Level.ERROR)).allMatch(s -> s.startsWith("[stderr] "));
  }

  @Test
  void execute_shouldLogProcessStdError() {
    JavaRunner runner = new JavaRunner(Paths.get("java"), JreCacheHit.DISABLED);

    // java -version is printing to stderr
    assertThat(runner.execute(List.of("-version"), null, stdOut::add)).isTrue();

    assertThat(stdOut).isEmpty();
    assertThat(logTester.logs(Level.ERROR)).isNotEmpty().allMatch(s -> s.startsWith("[stderr] "));
  }

  @Test
  void execute_shouldLogProcessStdError_and_skip_writing_to_stdin_when_process_fails_early() {
    JavaRunner runner = new JavaRunner(Paths.get("java"), JreCacheHit.DISABLED);
    List<String> command = List.of("-xyz");
    assertThat(runner.execute(command, "test", stdOut::add)).isFalse();

    assertThat(logTester.logs(Level.ERROR)).anyMatch(s -> s.startsWith("[stderr] Unrecognized option: -xyz"));
  }

  @Test
  void execute_whenInvalidRunner_shouldFail() {
    JavaRunner runner = new JavaRunner(Paths.get("invalid-runner"), JreCacheHit.DISABLED);
    List<String> command = List.of("--version");
    assertThatThrownBy(() -> runner.execute(command, "test", stdOut::add))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Failed to run the Java command");
  }

  @Test
  void execute_shouldReturnFalseWhenNonZeroExitCode() {
    JavaRunner runner = new JavaRunner(Paths.get("java"), JreCacheHit.DISABLED);
    List<String> command = List.of("unknown-command");
    assertThat(runner.execute(command, null, stdOut::add)).isFalse();
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void execute_shouldLogUnsupportedClassVersionError_whenOsIsWindows() {
    JavaRunner runner = new JavaRunner(Paths.get("cmd.exe"), JreCacheHit.DISABLED);
    List<String> command = List.of("/c", "echo UnsupportedClassVersionError 1>&2");

    assertThat(runner.execute(command, null, stdOut::add)).isTrue();
    assertThat(logTester.logs(Level.ERROR)).contains(JRE_VERSION_ERROR);
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void execute_shouldLogUnsupportedClassVersionError_whenOsIsLinux() {
    JavaRunner runner = new JavaRunner(Paths.get("sh"), JreCacheHit.DISABLED);
    List<String> command = List.of("-c", ">&2 echo UnsupportedClassVersionError");

    assertThat(runner.execute(command, null, stdOut::add)).isTrue();
    assertThat(logTester.logs(Level.ERROR)).contains(JRE_VERSION_ERROR);
  }

}
