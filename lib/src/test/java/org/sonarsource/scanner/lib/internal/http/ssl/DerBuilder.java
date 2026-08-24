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

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-rolled DER encoder used only by tests, to build the malformed and exotic PKCS12
 * structures that real tools (openssl, keytool) never produce but a hostile or corrupt file could.
 */
public final class DerBuilder {

  private DerBuilder() {
  }

  public static byte[] tlv(int tag, byte[] content) {
    var lengthBytes = length(content.length);
    var result = new byte[1 + lengthBytes.length + content.length];
    result[0] = (byte) tag;
    System.arraycopy(lengthBytes, 0, result, 1, lengthBytes.length);
    System.arraycopy(content, 0, result, 1 + lengthBytes.length, content.length);
    return result;
  }

  public static byte[] sequence(byte[]... children) {
    return tlv(DerReader.TAG_SEQUENCE, concat(children));
  }

  public static byte[] integer(long value) {
    return tlv(DerReader.TAG_INTEGER, BigInteger.valueOf(value).toByteArray());
  }

  public static byte[] octetString(byte[] content) {
    return tlv(DerReader.TAG_OCTET_STRING, content);
  }

  public static byte[] oid(String dottedNotation) {
    var arcs = dottedNotation.split("\\.");
    var out = new ByteArrayOutputStream();
    out.write(Integer.parseInt(arcs[0]) * 40 + Integer.parseInt(arcs[1]));
    for (var i = 2; i < arcs.length; i++) {
      var encodedArc = encodeBase128(Long.parseLong(arcs[i]));
      out.write(encodedArc, 0, encodedArc.length);
    }
    return tlv(DerReader.TAG_OBJECT_IDENTIFIER, out.toByteArray());
  }

  /**
   * Wraps a full inner TLV as an EXPLICIT context-specific tag, e.g. {@code [0] EXPLICIT ANY}.
   */
  public static byte[] explicit(int tagNumber, byte[] innerFullTlv) {
    return tlv(0xA0 | tagNumber, innerFullTlv);
  }

  /**
   * Wraps raw content as an IMPLICIT primitive context-specific tag, e.g. {@code [0] IMPLICIT OCTET STRING}.
   */
  public static byte[] implicitPrimitive(int tagNumber, byte[] content) {
    return tlv(0x80 | tagNumber, content);
  }

  public static byte[] concat(byte[]... parts) {
    var total = 0;
    for (byte[] part : parts) {
      total += part.length;
    }
    var result = new byte[total];
    var pos = 0;
    for (byte[] part : parts) {
      System.arraycopy(part, 0, result, pos, part.length);
      pos += part.length;
    }
    return result;
  }

  private static byte[] length(int len) {
    if (len < 0x80) {
      return new byte[]{(byte) len};
    }
    var numberOfLengthBytes = 1;
    var v = len;
    while ((v >>>= 8) != 0) {
      numberOfLengthBytes++;
    }
    var result = new byte[1 + numberOfLengthBytes];
    result[0] = (byte) (0x80 | numberOfLengthBytes);
    var remaining = len;
    for (var i = numberOfLengthBytes; i >= 1; i--) {
      result[i] = (byte) (remaining & 0xFF);
      remaining >>>= 8;
    }
    return result;
  }

  private static byte[] encodeBase128(long value) {
    if (value == 0) {
      return new byte[]{0};
    }
    List<Byte> groups = new ArrayList<>();
    var v = value;
    while (v > 0) {
      groups.add(0, (byte) (v & 0x7F));
      v >>>= 7;
    }
    for (var i = 0; i < groups.size() - 1; i++) {
      groups.set(i, (byte) (groups.get(i) | 0x80));
    }
    var result = new byte[groups.size()];
    for (var i = 0; i < result.length; i++) {
      result[i] = groups.get(i);
    }
    return result;
  }
}
