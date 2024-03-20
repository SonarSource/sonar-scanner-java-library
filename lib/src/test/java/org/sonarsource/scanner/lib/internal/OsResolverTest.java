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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sonarsource.scanner.lib.Paths2;
import org.sonarsource.scanner.lib.System2;
import org.sonarsource.scanner.lib.internal.cache.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OsResolverTest {

  private static final String OS_NAME = "os.name";

  @Mock
  private System2 system;
  @Mock
  private Paths2 paths;
  @Mock
  private Logger logger;

  @InjectMocks
  private OsResolver underTest;

  @TempDir
  private Path temp;

  @Test
  void getOs_windows() {
    when(system.getProperty(OS_NAME)).thenReturn("Windows 10");
    assertThat(underTest.getOs()).isEqualTo(OsResolver.OperatingSystem.WINDOWS);

    when(system.getProperty(OS_NAME)).thenReturn("win");
    assertThat(underTest.getOs()).isEqualTo(OsResolver.OperatingSystem.WINDOWS);
  }

  @Test
  void getOs_macos() {
    when(system.getProperty(OS_NAME)).thenReturn("MacOS X");
    assertThat(underTest.getOs()).isEqualTo(OsResolver.OperatingSystem.MACOS);

    when(system.getProperty(OS_NAME)).thenReturn("mac");
    assertThat(underTest.getOs()).isEqualTo(OsResolver.OperatingSystem.MACOS);

    when(system.getProperty(OS_NAME)).thenReturn("Darwin");
    assertThat(underTest.getOs()).isEqualTo(OsResolver.OperatingSystem.MACOS);
  }

  @Test
  void getOs_linux() {
    Path dummyPath = Path.of("dummy-path");
    when(paths.get("/etc/os-release")).thenReturn(dummyPath);
    when(paths.get("/usr/lib/os-release")).thenReturn(dummyPath);

    when(system.getProperty(OS_NAME)).thenReturn("Linux Ubuntu 22.2");
    assertThat(underTest.getOs()).isEqualTo(OsResolver.OperatingSystem.LINUX);

    when(system.getProperty(OS_NAME)).thenReturn("linux");
    assertThat(underTest.getOs()).isEqualTo(OsResolver.OperatingSystem.LINUX);
  }

  @Test
  void getOs_alpine() throws IOException {
    Path osReleaseFile = temp.resolve("os-release");
    Files.write(osReleaseFile, "sample\nID=Alpine\n".getBytes());
    when(paths.get("/etc/os-release")).thenReturn(osReleaseFile);

    when(system.getProperty(OS_NAME)).thenReturn("linux");
    assertThat(underTest.getOs()).isEqualTo(OsResolver.OperatingSystem.ALPINE);


    Files.write(osReleaseFile, "ID=Ubuntu\n".getBytes());
    assertThat(underTest.getOs()).isEqualTo(OsResolver.OperatingSystem.LINUX);
  }

  @Test
  void getOs_unknown() {
    when(system.getProperty(OS_NAME)).thenReturn("aix");
    assertThatThrownBy(() -> underTest.getOs())
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Failed to detect OS, use the property 'sonar.scanner.os' to set it manually.");
  }
}
