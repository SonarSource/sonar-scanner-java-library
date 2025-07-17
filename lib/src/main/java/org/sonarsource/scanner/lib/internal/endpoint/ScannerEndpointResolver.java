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

import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.sonarsource.scanner.lib.EnvironmentConfig;
import org.sonarsource.scanner.lib.ScannerProperties;
import org.sonarsource.scanner.lib.internal.MessageException;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.trim;

public class ScannerEndpointResolver {

  private ScannerEndpointResolver() {
  }

  public static ScannerEndpoint resolveEndpoint(Map<String, String> properties) {
    if (properties.containsKey(ScannerProperties.HOST_URL)) {
      return resolveEndpointFromSonarHostUrl(properties);
    }
    return resolveSonarQubeCloudEndpoint(properties);
  }

  private static ScannerEndpoint resolveSonarQubeCloudEndpoint(Map<String, String> properties) {
    var hasCloudUrl = properties.containsKey(ScannerProperties.SONARQUBE_CLOUD_URL);
    if (hasCloudUrl) {
      return resolveCustomSonarQubeCloudEndpoint(properties);
    }
    failIfApiEndpointAloneDefined(properties);
    var regionCode = properties.get(ScannerProperties.SONAR_REGION);
    return OfficialSonarQubeCloudInstance.fromRegionCode(regionCode)
      .orElseThrow(() -> new MessageException(
        format("Invalid region '%s'. Valid regions are: %s. Please check the '%s' property or the '%s' environment variable.",
          regionCode, StringUtils.join(OfficialSonarQubeCloudInstance.getRegionCodesWithoutGlobal().stream().map(r -> "'" + r + "'").collect(toList()), ", "),
          ScannerProperties.SONAR_REGION, EnvironmentConfig.REGION_ENV_VARIABLE)))
      .getEndpoint();
  }

  private static void failIfApiEndpointAloneDefined(Map<String, String> properties) {
    var hasApiUrl = properties.containsKey(ScannerProperties.API_BASE_URL);
    if (hasApiUrl) {
      throw new MessageException(format("Defining '%s' without '%s' is not supported.", ScannerProperties.API_BASE_URL, ScannerProperties.SONARQUBE_CLOUD_URL));
    }
  }

  private static ScannerEndpoint resolveCustomSonarQubeCloudEndpoint(Map<String, String> properties) {
    var hasApiUrl = properties.containsKey(ScannerProperties.API_BASE_URL);
    var maybeCloudInstance = maybeResolveOfficialSonarQubeCloud(properties, ScannerProperties.SONARQUBE_CLOUD_URL);
    if (maybeCloudInstance.isPresent()) {
      return maybeCloudInstance.get();
    }
    if (!hasApiUrl) {
      throw new MessageException(format("Defining a custom '%s' without providing '%s' is not supported.", ScannerProperties.SONARQUBE_CLOUD_URL, ScannerProperties.API_BASE_URL));
    }
    return new ScannerEndpoint(
      cleanUrl(properties.get(ScannerProperties.SONARQUBE_CLOUD_URL)),
      cleanUrl(properties.get(ScannerProperties.API_BASE_URL)), true, null);
  }

  private static MessageException inconsistentUrlAndRegion(String prop2) {
    return new MessageException(format("Inconsistent values for properties '%s' and '%s'. Please only specify one of the two properties.", ScannerProperties.SONAR_REGION, prop2));
  }

  private static ScannerEndpoint resolveEndpointFromSonarHostUrl(Map<String, String> properties) {
    return maybeResolveOfficialSonarQubeCloud(properties, ScannerProperties.HOST_URL)
      .orElse(new SonarQubeServer(cleanUrl(properties.get(ScannerProperties.HOST_URL))));
  }

  private static Optional<ScannerEndpoint> maybeResolveOfficialSonarQubeCloud(Map<String, String> properties, String urlPropName) {
    var maybeCloudInstance = OfficialSonarQubeCloudInstance.fromWebEndpoint(cleanUrl(properties.get(urlPropName)));
    var hasRegion = properties.containsKey(ScannerProperties.SONAR_REGION);
    if (maybeCloudInstance.isPresent()) {
      if (hasRegion && OfficialSonarQubeCloudInstance.fromRegionCode(properties.get(ScannerProperties.SONAR_REGION)).filter(maybeCloudInstance.get()::equals).isEmpty()) {
        throw inconsistentUrlAndRegion(urlPropName);
      }
      return Optional.of(maybeCloudInstance.get().getEndpoint());
    }
    if (hasRegion) {
      throw inconsistentUrlAndRegion(urlPropName);
    }
    return Optional.empty();
  }

  private static String cleanUrl(String url) {
    String withoutTrailingSlash = trim(url);
    while (withoutTrailingSlash.endsWith("/")) {
      withoutTrailingSlash = Strings.CS.removeEnd(withoutTrailingSlash, "/");
    }
    return withoutTrailingSlash;
  }

}
