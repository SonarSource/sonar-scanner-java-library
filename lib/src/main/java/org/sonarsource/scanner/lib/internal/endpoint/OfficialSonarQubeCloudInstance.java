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
package org.sonarsource.scanner.lib.internal.endpoint;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

public enum OfficialSonarQubeCloudInstance {
  GLOBAL("https://sonarcloud.io", "https://api.sonarcloud.io", null),
  US("https://sonarqube.us", "https://api.sonarqube.us", "US");


  private final ScannerEndpoint endpoint;

  OfficialSonarQubeCloudInstance(String webEndpoint, String apiEndpoint, @Nullable String regionLabel) {
    this.endpoint = new ScannerEndpoint(webEndpoint, apiEndpoint, true, regionLabel);
  }

  public static Set<String> getRegionCodesWithoutGlobal() {
    return getRegionsWithoutGlobal().map(Enum::name).map(s -> s.toLowerCase(Locale.ENGLISH)).collect(Collectors.toSet());
  }

  /**
   * For now, we are not sure the default region will be called "global" so don't let users use this enum value
   */
  private static Stream<OfficialSonarQubeCloudInstance> getRegionsWithoutGlobal() {
    return Arrays.stream(OfficialSonarQubeCloudInstance.values()).filter(r -> r != GLOBAL);
  }

  public static Optional<OfficialSonarQubeCloudInstance> fromRegionCode(@Nullable String regionCode) {
    if (StringUtils.isBlank(regionCode)) {
      return Optional.of(GLOBAL);
    }
    try {
      var value = OfficialSonarQubeCloudInstance.valueOf(regionCode.toUpperCase(Locale.ENGLISH));
      if (value == GLOBAL) {
        // For now, we are not sure the default region will be called "global" so don't let users use this enum value
        return Optional.empty();
      }
      return Optional.of(value);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public static Optional<OfficialSonarQubeCloudInstance> fromWebEndpoint(String url) {
    return Arrays.stream(OfficialSonarQubeCloudInstance.values())
      .filter(r -> r.endpoint.getWebEndpoint().equals(url))
      .findFirst();
  }

  public ScannerEndpoint getEndpoint() {
    return endpoint;
  }
}
