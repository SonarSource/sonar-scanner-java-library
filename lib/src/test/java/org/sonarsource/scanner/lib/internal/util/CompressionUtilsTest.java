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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompressionUtilsTest {

  @TempDir
  private Path temp;

  @Test
  void unzipping_creates_target_directory_if_it_does_not_exist() throws IOException {
    var zip = Paths.get("src/test/resources/archive.zip");
    var tempDir = temp.resolve("dir");

    var subDir = tempDir.resolve("subDir");
    CompressionUtils.unzip(zip, subDir);
    assertThat(subDir.toFile().list()).hasSize(3);
  }

  @Test
  void unzip_file() throws IOException {
    var zip = Paths.get("src/test/resources/archive.zip");
    var toDir = temp.resolve("dir");
    CompressionUtils.unzip(zip, toDir);
    assertThat(toDir.toFile().list()).hasSize(3);
  }

  @Test
  void fail_if_unzipping_file_outside_target_directory() {
    var zip = Paths.get("src/test/resources/zip-slip.zip");
    var toDir = temp.resolve("dir");

    assertThatThrownBy(() -> CompressionUtils.unzip(zip, toDir))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Unzipping an entry outside the target directory is not allowed: ../../../../../../../../../../../../../../../../" +
        "../../../../../../../../../../../../../../../../../../../../../../../../tmp/evil.txt");
  }

  @Test
  void extract_tar_gz() throws IOException {
    var tar = Paths.get("src/test/resources/archive.tar.gz");
    var toDir = temp.resolve("dir");
    CompressionUtils.extractTarGz(tar, toDir);
    assertThat(toDir.toFile().list()).hasSize(3);
  }
}
