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
package org.sonarsource.scanner.api.internal.cache;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.scanner.api.internal.cache.FileHashes;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FileHashesTest {

  SecureRandom secureRandom = new SecureRandom();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void test_md5_hash() throws IOException {
    assertThat(hash("sonar")).isEqualTo("d85e336d61f5344395c42126fac239bc");

    // compare results with commons-codec
    for (int index = 0; index < 100; index++) {
      String random = randomString();
      assertThat(hash(random)).as(random).isEqualTo(
        DigestUtils.md5Hex(random).toLowerCase());
    }
  }

  @Test
  public void test_hash_file() throws IOException {
    File f = temp.newFile();
    Files.write(f.toPath(), "sonar".getBytes(StandardCharsets.UTF_8));
    assertThat(hashFile(f)).isEqualTo("d85e336d61f5344395c42126fac239bc");
  }

  @Test
  public void test_toHex() {
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
  public void fail_if_file_does_not_exist() throws IOException {
    File file = temp.newFile("does_not_exist");
    file.delete();

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Fail to compute hash of: " + file.getAbsolutePath());

    new FileHashes().of(file);
  }

  @Test
  public void fail_if_stream_is_closed() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Fail to compute hash");

    InputStream input = mock(InputStream.class);
    when(input.read(any(byte[].class), anyInt(), anyInt())).thenThrow(new IllegalThreadStateException());
    new FileHashes().of(input);
  }

  private String randomString() {
    return new BigInteger(130, secureRandom).toString(32);
  }

  private String hash(String s) throws IOException {
    try (InputStream in = new ByteArrayInputStream(s.getBytes())) {
      return new FileHashes().of(in);
    }
  }

  private String hashFile(File f) {
    return new FileHashes().of(f);
  }
}
