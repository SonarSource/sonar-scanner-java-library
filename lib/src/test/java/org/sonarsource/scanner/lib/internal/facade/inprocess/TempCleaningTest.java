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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class TempCleaningTest {

  @Test
  void should_clean_jvm_tmp_dir() {
    TempCleaning cleaning = new TempCleaning();
    assertThat(cleaning.tempDir).isDirectory().exists();
  }

  @Test
  void should_clean(@TempDir Path dir) throws Exception {
    Path oldBatch = dir.resolve("sonar-scanner-java-library-batch656.jar");
    Files.write(oldBatch, "foo".getBytes(StandardCharsets.UTF_8));
    FileTime fTime = FileTime.fromMillis(System.currentTimeMillis() - 3 * TempCleaning.ONE_DAY_IN_MILLISECONDS);
    Files.setLastModifiedTime(oldBatch, fTime);

    Path youngBatch = dir.resolve("sonar-scanner-java-library-batch123.jar");
    Files.write(youngBatch, "foo".getBytes(StandardCharsets.UTF_8));

    Path doNotDelete = dir.resolve("jacoco.txt");
    Files.write(doNotDelete, "foo".getBytes(StandardCharsets.UTF_8));

    assertThat(oldBatch).exists();
    assertThat(youngBatch).exists();
    assertThat(doNotDelete).exists();
    new TempCleaning(dir).clean();

    assertThat(oldBatch).doesNotExist();
    assertThat(youngBatch).exists();
    assertThat(doNotDelete).exists();
  }
}
