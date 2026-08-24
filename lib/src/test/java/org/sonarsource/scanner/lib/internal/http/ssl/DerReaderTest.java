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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DerReaderTest {

  @Test
  void reads_short_form_length() {
    var reader = new DerReader(DerBuilder.octetString(new byte[]{1, 2, 3}));

    var value = reader.readValue();

    assertThat(value.tag()).isEqualTo(DerReader.TAG_OCTET_STRING);
    assertThat(value.contentBytes()).containsExactly(1, 2, 3);
  }

  @Test
  void reads_long_form_length() {
    var content = new byte[200];
    var reader = new DerReader(DerBuilder.octetString(content));

    var value = reader.readValue();

    assertThat(value.length()).isEqualTo(200);
  }

  @Test
  void readAll_reads_every_sibling_value() {
    var reader = new DerReader(DerBuilder.sequence(DerBuilder.integer(1), DerBuilder.integer(2), DerBuilder.integer(3))).readValue().asReader();

    var values = reader.readAll();

    assertThat(values).hasSize(3);
  }

  @Test
  void throws_when_length_uses_indefinite_form() {
    byte[] bytes = {0x30, (byte) 0x80};

    assertThatThrownBy(() -> new DerReader(bytes).readValue())
      .isInstanceOf(Pkcs12ParsingException.class)
      .hasMessageContaining("Unsupported DER length encoding");
  }

  @Test
  void throws_when_length_uses_more_than_four_bytes() {
    byte[] bytes = {0x30, (byte) 0x85, 0, 0, 0, 0, 1};

    assertThatThrownBy(() -> new DerReader(bytes).readValue())
      .isInstanceOf(Pkcs12ParsingException.class)
      .hasMessageContaining("Unsupported DER length encoding");
  }

  @Test
  void throws_when_long_form_length_overflows_to_negative() {
    byte[] bytes = {0x30, (byte) 0x84, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

    assertThatThrownBy(() -> new DerReader(bytes).readValue())
      .isInstanceOf(Pkcs12ParsingException.class)
      .hasMessageContaining("DER content length is too large");
  }

  @Test
  void throws_when_declared_length_exceeds_available_bytes() {
    byte[] bytes = {0x30, 0x7F, 1, 2};

    assertThatThrownBy(() -> new DerReader(bytes).readValue())
      .isInstanceOf(Pkcs12ParsingException.class)
      .hasMessageContaining("Truncated or malformed DER content");
  }

}
