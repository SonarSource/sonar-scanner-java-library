/*
 * SonarScanner Java Library
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
package org.sonarsource.scanner.lib.internal.util;

import java.lang.module.ModuleDescriptor.Version;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

public class VersionUtils {
  private VersionUtils() {
    // only util static methods
  }

  /**
   * Checks if a given version is at least the target version.
   *
   * @param version       the version to compare
   * @param targetVersion the target version to compare with
   * @return true if the version is at least the target version
   */
  public static boolean isAtLeast(@Nullable String version, String targetVersion) {
    if (StringUtils.isBlank(version) || String.valueOf(version.charAt(0)).matches("\\D")) {
      return false;
    }
    return Version.parse(version).compareTo(Version.parse(targetVersion)) >= 0;
  }
}
