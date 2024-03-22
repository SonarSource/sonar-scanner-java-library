/*
 * SonarQube Scanner Commons
 * Copyright (C) 2011-2024 SonarSource SA
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
  public void isAtLeast_shouldCompareCorrectly() {
    assertThat(VersionUtils.isAtLeast("10.5", "10.5")).isTrue();
    assertThat(VersionUtils.isAtLeast("10.10", "10.5")).isTrue();
    assertThat(VersionUtils.isAtLeast(null, "10.5")).isFalse();
    assertThat(VersionUtils.isAtLeast("105", "10.5")).isTrue();
    assertThat(VersionUtils.isAtLeast("10", "10.5")).isFalse();
    assertThat(VersionUtils.isAtLeast("10.0", "10.5")).isFalse();
    assertThat(VersionUtils.isAtLeast("", "10.5")).isFalse();
    assertThat(VersionUtils.isAtLeast("trash", "10.5")).isFalse();
    assertThat(VersionUtils.isAtLeast("11.0.0", "10.5")).isTrue();
    assertThat(VersionUtils.isAtLeast("10.4.9", "10.5")).isFalse();
    assertThat(VersionUtils.isAtLeast("10.5-SNAPSHOT", "10.5")).isFalse();
    assertThat(VersionUtils.isAtLeast("10.6-SNAPSHOT", "10.5")).isTrue();
    assertThat(VersionUtils.isAtLeast("10.5.0.1234", "10.5")).isTrue();
  }
}
