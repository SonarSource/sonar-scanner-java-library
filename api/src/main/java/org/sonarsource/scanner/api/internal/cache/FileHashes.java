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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;

/**
 * Hashes used to store files in the cache directory.
 *
 * @since 3.5
 */
class FileHashes {

  private static final int STREAM_BUFFER_LENGTH = 1024;

  String of(File file) {
    try {
      return of(new FileInputStream(file));
    } catch (IOException e) {
      throw new IllegalStateException("Fail to compute hash of: " + file.getAbsolutePath(), e);
    }
  }

  /**
   * Computes the hash of given stream. The stream is closed by this method.
   */
  String of(InputStream input) {
    try (InputStream is = input) {
      MessageDigest digest = MessageDigest.getInstance("MD5");
      byte[] hash = digest(is, digest);
      return toHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException("Fail to compute hash", e);
    }
  }

  private static byte[] digest(InputStream input, MessageDigest digest) throws IOException {
    final byte[] buffer = new byte[STREAM_BUFFER_LENGTH];
    int read = input.read(buffer, 0, STREAM_BUFFER_LENGTH);
    while (read > -1) {
      digest.update(buffer, 0, read);
      read = input.read(buffer, 0, STREAM_BUFFER_LENGTH);
    }
    return digest.digest();
  }

  static String toHex(byte[] bytes) {
    BigInteger bi = new BigInteger(1, bytes);
    String hexFormat = "%0" + (bytes.length << 1) + "x";
    return String.format(hexFormat, bi);
  }
}
