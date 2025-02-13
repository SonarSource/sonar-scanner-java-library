/*
 * SonarScanner Java Library
 * Copyright (C) 2011-2025 SonarSource SA
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
package org.sonarsource.scanner.lib.internal.http;

import java.net.MalformedURLException;
import java.net.URI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpExceptionTest {

  @Test
  void testEqualsAndHashCode() throws MalformedURLException {
    var underTest1 = new HttpException(URI.create("http://foo1").toURL(), 401, "message1", "body1");
    var sameUnderTest = new HttpException(URI.create("http://foo1").toURL(), 401, "message1", "body1");

    assertThat(underTest1)
      .hasSameHashCodeAs(sameUnderTest)
      .isEqualTo(sameUnderTest)
      .isNotEqualTo(new HttpException(URI.create("http://foo2").toURL(), 401, "message1", "body1"))
      .isNotEqualTo(new HttpException(URI.create("http://foo1").toURL(), 402, "message1", "body1"))
      .isNotEqualTo(new HttpException(URI.create("http://foo1").toURL(), 401, "message2", "body1"))
      .isNotEqualTo(new HttpException(URI.create("http://foo1").toURL(), 401, "message1", "body2"));
  }
}
