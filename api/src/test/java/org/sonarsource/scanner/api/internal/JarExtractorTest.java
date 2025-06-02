/*
 * SonarQube Scanner API
 * Copyright (C) 2011-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.scanner.api.internal;

import org.junit.Test;
import org.sonarsource.scanner.api.internal.JarExtractor;
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
