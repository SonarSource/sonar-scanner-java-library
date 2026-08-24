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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DerValueTest {

  @Test
  void asObjectIdentifier_decodes_multi_arc_oid() {
    var value = new DerReader(DerBuilder.oid("1.2.840.113549.1.5.13")).readValue();

    assertThat(value.asObjectIdentifier()).isEqualTo("1.2.840.113549.1.5.13");
  }

  @Test
  void asObjectIdentifier_throws_when_tag_is_not_object_identifier() {
    var value = new DerReader(DerBuilder.integer(42)).readValue();

    assertThatThrownBy(value::asObjectIdentifier)
      .isInstanceOf(Pkcs12ParsingException.class)
      .hasMessageContaining("Expected an OBJECT IDENTIFIER");
  }

  @Test
  void asInteger_decodes_value() {
    var value = new DerReader(DerBuilder.integer(2048)).readValue();

    assertThat(value.asInteger()).isEqualTo(BigInteger.valueOf(2048));
  }

  @Test
  void asInteger_throws_when_tag_is_not_integer() {
    var value = new DerReader(DerBuilder.octetString(new byte[]{1})).readValue();

    assertThatThrownBy(value::asInteger)
      .isInstanceOf(Pkcs12ParsingException.class)
      .hasMessageContaining("Expected an INTEGER");
  }

}
