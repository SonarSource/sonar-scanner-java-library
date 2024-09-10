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

import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
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
      .hasMessage("Extracting an entry outside the target directory is not allowed: ../../../../../../../../../../../../../../../../" +
        "../../../../../../../../../../../../../../../../../../../../../../../../tmp/evil.txt");
  }

  @Test
  void extract_tar_gz() throws IOException {
    var tar = Paths.get("src/test/resources/archive.tar.gz");
    var toDir = temp.resolve("dir");
    CompressionUtils.extractTarGz(tar, toDir);
    assertThat(toDir.toFile().list()).hasSize(3);
  }

  @Test
  void fail_if_extracting_targz_file_outside_target_directory() {
    var targz = Paths.get("src/test/resources/slip.tar.gz");
    var toDir = temp.resolve("dir");

    assertThatThrownBy(() -> CompressionUtils.extractTarGz(targz, toDir))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Extracting an entry outside the target directory is not allowed: ../../../../../../../../../../../../../../../../../"
        + "../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../"
        + "../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../"
        + "../tmp/slipped.txt");
  }

  @Test
  void fileMode_conversion() {
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("000", 8))).isEmpty();
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("001", 8))).containsExactlyInAnyOrder(OTHERS_EXECUTE);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("002", 8))).containsExactlyInAnyOrder(OTHERS_WRITE);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("003", 8))).containsExactlyInAnyOrder(OTHERS_WRITE, OTHERS_EXECUTE);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("004", 8))).containsExactlyInAnyOrder(OTHERS_READ);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("005", 8))).containsExactlyInAnyOrder(OTHERS_READ, OTHERS_EXECUTE);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("006", 8))).containsExactlyInAnyOrder(OTHERS_READ, OTHERS_WRITE);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("007", 8))).containsExactlyInAnyOrder(OTHERS_READ, OTHERS_WRITE, OTHERS_EXECUTE);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("010", 8))).containsExactlyInAnyOrder(GROUP_EXECUTE);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("020", 8))).containsExactlyInAnyOrder(GROUP_WRITE);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("030", 8))).containsExactlyInAnyOrder(GROUP_WRITE, GROUP_EXECUTE);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("040", 8))).containsExactlyInAnyOrder(GROUP_READ);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("050", 8))).containsExactlyInAnyOrder(GROUP_READ, GROUP_EXECUTE);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("060", 8))).containsExactlyInAnyOrder(GROUP_READ, GROUP_WRITE);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("070", 8))).containsExactlyInAnyOrder(GROUP_READ, GROUP_WRITE, GROUP_EXECUTE);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("100", 8))).containsExactlyInAnyOrder(OWNER_EXECUTE);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("200", 8))).containsExactlyInAnyOrder(OWNER_WRITE);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("300", 8))).containsExactlyInAnyOrder(OWNER_WRITE, OWNER_EXECUTE);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("400", 8))).containsExactlyInAnyOrder(OWNER_READ);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("500", 8))).containsExactlyInAnyOrder(OWNER_READ, OWNER_EXECUTE);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("600", 8))).containsExactlyInAnyOrder(OWNER_READ, OWNER_WRITE);
    assertThat(CompressionUtils.fromFileMode(Integer.parseInt("700", 8))).containsExactlyInAnyOrder(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE);
  }
}
