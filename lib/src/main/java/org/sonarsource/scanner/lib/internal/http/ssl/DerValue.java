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

import java.math.BigInteger;
import java.util.Arrays;

/**
 * A single decoded DER TLV (tag, length, value), value being a slice of the original byte array.
 */
final class DerValue {

  private final int tag;
  private final byte[] data;
  private final int offset;
  private final int length;

  DerValue(int tag, byte[] data, int offset, int length) {
    this.tag = tag;
    this.data = data;
    this.offset = offset;
    this.length = length;
  }

  int tag() {
    return tag;
  }

  byte[] data() {
    return data;
  }

  int offset() {
    return offset;
  }

  int length() {
    return length;
  }

  byte[] contentBytes() {
    return Arrays.copyOfRange(data(), offset(), offset() + length());
  }

  DerReader asReader() {
    return new DerReader(data(), offset(), length());
  }

  String asObjectIdentifier() {
    if (tag() != DerReader.TAG_OBJECT_IDENTIFIER) {
      throw new Pkcs12ParsingException("Expected an OBJECT IDENTIFIER but found DER tag " + tag());
    }
    var oid = new StringBuilder();
    int first = data()[offset()] & 0xFF;
    oid.append(first / 40).append('.').append(first % 40);
    long value = 0;
    for (int i = 1; i < length(); i++) {
      int b = data()[offset() + i] & 0xFF;
      value = (value << 7) | (b & 0x7F);
      if ((b & 0x80) == 0) {
        oid.append('.').append(value);
        value = 0;
      }
    }
    return oid.toString();
  }

  BigInteger asInteger() {
    if (tag() != DerReader.TAG_INTEGER) {
      throw new Pkcs12ParsingException("Expected an INTEGER but found DER tag " + tag());
    }
    return new BigInteger(contentBytes());
  }
}
