/*
 * SonarQube Scanner API
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
package org.sonarsource.scanner.api.internal;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionUtilsTest {

  @Test
  public void parse_version() {
    assertThat(VersionUtils.isAtLeast52("5.2")).isTrue();
    assertThat(VersionUtils.isAtLeast52(null)).isFalse();
    assertThat(VersionUtils.isAtLeast52("52")).isTrue();
    assertThat(VersionUtils.isAtLeast52("5.0")).isFalse();
    assertThat(VersionUtils.isAtLeast52("")).isFalse();
    assertThat(VersionUtils.isAtLeast52("trash")).isFalse();
    assertThat(VersionUtils.isAtLeast52("6.0.0")).isTrue();
    assertThat(VersionUtils.isAtLeast52("5.2-SNAPSHOT")).isTrue();
    assertThat(VersionUtils.isAtLeast52("6.3.0.1234")).isTrue();
  }
}
