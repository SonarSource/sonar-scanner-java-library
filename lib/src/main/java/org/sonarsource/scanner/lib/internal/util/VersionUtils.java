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
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

public class VersionUtils {

  private static final String SEQUENCE_SEPARATOR = ".";
  private static final String QUALIFIER_SEPARATOR = "-";

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
  public static boolean isAtLeastIgnoringQualifier(@Nullable String version, String targetVersion) {
    if (isOfUnexpectedFormat(version)) {
      return false;
    }
    return Version.parse(StringUtils.substringBefore(version, "-")).compareTo(Version.parse(targetVersion)) >= 0;
  }

  public static int compareMajor(@Nullable String version, int number) {
    if (isOfUnexpectedFormat(version)) {
      return Integer.compare(0, number);
    }

    String s = trimToEmpty(version);
    String qualifier = substringAfter(s, QUALIFIER_SEPARATOR);
    if (!qualifier.isEmpty()) {
      s = substringBefore(s, QUALIFIER_SEPARATOR);
    }

    String[] fields = s.split(Pattern.quote(SEQUENCE_SEPARATOR));
    try {
      return Integer.compare(Integer.parseInt(fields[0]), number);
    } catch (NumberFormatException e) {
      return Integer.compare(0, number);
    }
  }

  private static boolean isOfUnexpectedFormat(@Nullable String version) {
    return StringUtils.isBlank(version) || String.valueOf(version.trim().charAt(0)).matches("\\D");
  }
}
