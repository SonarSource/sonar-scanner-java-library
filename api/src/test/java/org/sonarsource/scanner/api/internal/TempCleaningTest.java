/*
 * SonarQube Scanner API
 * Copyright (C) 2011-2019 SonarSource SA
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
package org.sonarsource.scanner.api.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.scanner.api.internal.cache.Logger;

public class TempCleaningTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void should_clean_jvm_tmp_dir() {
    TempCleaning cleaning = new TempCleaning(mock(Logger.class));
    assertThat(cleaning.tempDir).isDirectory().exists();
  }

  @Test
  public void should_clean() throws Exception {
    Path dir = temp.newFolder().toPath();
    Path oldBatch = dir.resolve("sonar-scanner-api-batch656.jar");
    Files.write(oldBatch, "foo".getBytes(StandardCharsets.UTF_8));
    FileTime fTime = FileTime.fromMillis(System.currentTimeMillis() - 3 * TempCleaning.ONE_DAY_IN_MILLISECONDS);
    Files.setLastModifiedTime(oldBatch, fTime);
    
    Path youngBatch = dir.resolve("sonar-scanner-api-batch123.jar");
    Files.write(youngBatch, "foo".getBytes(StandardCharsets.UTF_8));

    Path doNotDelete = dir.resolve("jacoco.txt");
    Files.write(doNotDelete, "foo".getBytes(StandardCharsets.UTF_8));

    assertThat(oldBatch).exists();
    assertThat(youngBatch).exists();
    assertThat(doNotDelete).exists();
    new TempCleaning(dir, mock(Logger.class)).clean();

    assertThat(oldBatch).doesNotExist();
    assertThat(youngBatch).exists();
    assertThat(doNotDelete).exists();
  }
}
