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
package org.sonarsource.scanner.lib.internal.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import testutils.LogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArchResolverTest {

  @RegisterExtension
  private final LogTester logTester = new LogTester().setLevel(Level.DEBUG);

  @Test
  void shouldUseUnameToDetectArch() throws IOException {
    var processWrapperFactory = mock(ProcessWrapperFactory.class);
    var process = mock(ProcessWrapperFactory.ProcessWrapper.class);
    when(process.getInputStream()).thenReturn(new ByteArrayInputStream("arm64".getBytes()));
    when(processWrapperFactory.create("uname", "-m")).thenReturn(process);
    ArchResolver archResolver = new ArchResolver(new System2(), processWrapperFactory, true);

    String arch = archResolver.getCpuArch();

    assertThat(arch).isEqualTo("arm64");
    assertThat(logTester.logs(Level.DEBUG)).contains("uname -m returned 'arm64'");
  }

  @Test
  void shouldNotUseUnameOnWindows() {
    ArchResolver archResolver = new ArchResolver(new System2(), mock(ProcessWrapperFactory.class), false);

    String arch = archResolver.getCpuArch();

    assertThat(arch).isEqualTo(System.getProperty("os.arch"));
  }

  @Test
  void shouldFallbackToSystemPropertiesIfExitIsNotZero() throws IOException, InterruptedException {
    var processWrapperFactory = mock(ProcessWrapperFactory.class);
    var process = mock(ProcessWrapperFactory.ProcessWrapper.class);
    when(process.getInputStream()).thenReturn(new ByteArrayInputStream("Error".getBytes()));
    when(process.waitFor()).thenReturn(-1);
    when(processWrapperFactory.create("uname", "-m")).thenReturn(process);
    ArchResolver archResolver = new ArchResolver(new System2(), processWrapperFactory, true);

    String arch = archResolver.getCpuArch();

    assertThat(arch).isEqualTo(System.getProperty("os.arch"));
    assertThat(logTester.logs(Level.DEBUG)).contains("uname -m returned 'Error'", "Command exited with code: -1");
  }

  @Test
  void shouldFallbackToSystemPropertiesIfExceptionOnWaitFor() throws IOException, InterruptedException {
    var processWrapperFactory = mock(ProcessWrapperFactory.class);
    var process = mock(ProcessWrapperFactory.ProcessWrapper.class);
    when(process.getInputStream()).thenReturn(new ByteArrayInputStream("Error".getBytes()));
    when(process.waitFor()).thenThrow(new InterruptedException());
    when(processWrapperFactory.create("uname", "-m")).thenReturn(process);
    ArchResolver archResolver = new ArchResolver(new System2(), processWrapperFactory, true);

    String arch = archResolver.getCpuArch();

    assertThat(arch).isEqualTo(System.getProperty("os.arch"));
    assertThat(logTester.logs(Level.DEBUG)).contains("Failed to get the architecture using 'uname -m'");
  }

  @Test
  void shouldFallbackToSystemPropertiesIfNoOutput() throws IOException {
    var processWrapperFactory = mock(ProcessWrapperFactory.class);
    var process = mock(ProcessWrapperFactory.ProcessWrapper.class);
    when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
    when(processWrapperFactory.create("uname", "-m")).thenReturn(process);
    ArchResolver archResolver = new ArchResolver(new System2(), processWrapperFactory, true);

    String arch = archResolver.getCpuArch();

    assertThat(arch).isEqualTo(System.getProperty("os.arch"));
    assertThat(logTester.logs(Level.DEBUG)).contains("Failed to get the architecture using 'uname -m'");
  }
}