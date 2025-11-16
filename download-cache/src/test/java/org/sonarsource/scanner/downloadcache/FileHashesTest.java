/*
 * SonarScanner Download Cache Utility
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
package org.sonarsource.scanner.downloadcache;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.SecureRandom;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileHashesTest {
  private final SecureRandom secureRandom = new SecureRandom();

  private final FileHashes underTest = new FileHashes();


  @Test
  void test_md5_hash() throws IOException {
    assertThat(hash("sonar", "MD5")).isEqualTo("d85e336d61f5344395c42126fac239bc");

    // compare results with commons-codec
    for (int index = 0; index < 100; index++) {
      String random = randomString();
      assertThat(hash(random, "MD5")).as(random).isEqualTo(
        DigestUtils.md5Hex(random).toLowerCase());
    }
  }

  @Test
  void test_sha256_hash() throws IOException {
    assertThat(hash("sonar", "SHA-256")).isEqualTo("48ce1a75f18924f02f7d555a0c30d5c2f5f09eba641a555555d355a477bb9ae6");

    // compare results with commons-codec
    for (int index = 0; index < 100; index++) {
      String random = randomString();
      assertThat(hash(random, "SHA-256")).as(random).isEqualTo(
        DigestUtils.sha256Hex(random).toLowerCase());
    }
  }

  @Test
  void test_toHex() {
    // lower-case
    assertThat(FileHashes.toHex("aloa_bi_bop_a_loula".getBytes())).isEqualTo("616c6f615f62695f626f705f615f6c6f756c61");

    // compare results with commons-codec
    for (int index = 0; index < 100; index++) {
      String random = randomString();
      assertThat(FileHashes.toHex(random.getBytes())).as(random).isEqualTo(
        Hex.encodeHexString(random.getBytes()).toLowerCase());
    }
  }

  @Test
  void fail_if_file_does_not_exist(@TempDir Path tmpDir) {
    var doesNotExist = tmpDir.resolve("does_not_exist").toFile();

    assertThatThrownBy(() -> underTest.of(doesNotExist, "MD5"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Fail to compute hash of: " + doesNotExist);
  }

  @Test
  void fail_if_stream_is_closed() throws Exception {
    InputStream input = mock(InputStream.class);
    when(input.read(any(byte[].class), anyInt(), anyInt())).thenThrow(new IllegalThreadStateException());

    assertThatThrownBy(() -> underTest.of(input, "MD5"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Fail to compute hash");
  }

  private String randomString() {
    return new BigInteger(130, secureRandom).toString(32);
  }

  private String hash(String s, String hashAlgorithm) throws IOException {
    try (InputStream in = new ByteArrayInputStream(s.getBytes())) {
      return underTest.of(in, hashAlgorithm);
    }
  }
}
