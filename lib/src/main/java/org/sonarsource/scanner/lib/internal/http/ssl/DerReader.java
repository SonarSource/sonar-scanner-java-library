/*
 * SonarScanner Java Library
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.scanner.lib.internal.http.ssl;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal reader for the subset of DER (ASN.1 Distinguished Encoding Rules) used by PKCS#12 files.
 * PKCS#12 producers always emit DER, never indefinite-length BER, so only definite-length
 * short-form and long-form encodings need to be supported.
 */
final class DerReader {

  static final int TAG_INTEGER = 0x02;
  static final int TAG_OCTET_STRING = 0x04;
  static final int TAG_OBJECT_IDENTIFIER = 0x06;
  static final int TAG_SEQUENCE = 0x30;

  private final byte[] data;
  private final int end;
  private int pos;

  DerReader(byte[] data) {
    this(data, 0, data.length);
  }

  DerReader(byte[] data, int offset, int length) {
    this.data = data;
    this.pos = offset;
    this.end = offset + length;
  }

  boolean hasRemaining() {
    return pos < end;
  }

  DerValue readValue() {
    requireRemaining(1);
    int tag = data[pos++] & 0xFF;
    int length = readLength();
    requireRemaining(length);
    int start = pos;
    pos += length;
    return new DerValue(tag, data, start, length);
  }

  List<DerValue> readAll() {
    List<DerValue> values = new ArrayList<>();
    while (hasRemaining()) {
      values.add(readValue());
    }
    return values;
  }

  private int readLength() {
    requireRemaining(1);
    int first = data[pos++] & 0xFF;
    if ((first & 0x80) == 0) {
      return first;
    }
    int numberOfLengthBytes = first & 0x7F;
    if (numberOfLengthBytes == 0 || numberOfLengthBytes > 4) {
      throw new Pkcs12ParsingException("Unsupported DER length encoding");
    }
    requireRemaining(numberOfLengthBytes);
    int length = 0;
    for (int i = 0; i < numberOfLengthBytes; i++) {
      length = (length << 8) | (data[pos++] & 0xFF);
    }
    if (length < 0) {
      throw new Pkcs12ParsingException("DER content length is too large");
    }
    return length;
  }

  private void requireRemaining(int n) {
    if (pos + n > end) {
      throw new Pkcs12ParsingException("Truncated or malformed DER content");
    }
  }
}
