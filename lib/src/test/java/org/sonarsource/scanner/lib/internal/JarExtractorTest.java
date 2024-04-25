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

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.fail;

public class JarExtractorTest {
  @Test
  public void test_extract() throws Exception {
    Path jarFile = new JarExtractor().extractToTemp("fake");
    assertThat(jarFile).exists();
    assertThat(new String(Files.readAllBytes(jarFile), StandardCharsets.UTF_8)).isEqualTo("Fake jar for unit tests");
    assertThat(jarFile.toUri().toURL().toString()).doesNotContain("jar:file");
  }

  @Test
  public void should_fail_to_extract() throws Exception {
    try {
      new JarExtractor().extractToTemp("unknown");
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage("Fail to extract unknown.jar");
    }
  }
}
