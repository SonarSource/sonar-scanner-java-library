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
package org.sonarsource.scanner.lib.internal.facade.inprocess;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JarExtractorTest {

  private final JarExtractor underTest = new JarExtractor();

  @Test
  void test_extract() throws Exception {
    Path jarFile = underTest.extractToTemp("fake");
    assertThat(jarFile).exists();
    assertThat(Files.readString(jarFile)).isEqualTo("Fake jar for unit tests");
    assertThat(jarFile.toUri().toURL().toString()).doesNotContain("jar:file");
  }

  @Test
  void should_fail_to_extract() {
    assertThatThrownBy(() -> underTest.extractToTemp("unknown"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Fail to extract unknown.jar");
  }
}
