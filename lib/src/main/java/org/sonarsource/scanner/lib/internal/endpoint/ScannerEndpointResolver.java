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
import org.sonarsource.scanner.lib.EnvironmentConfig;
import org.sonarsource.scanner.lib.ScannerProperties;
import org.sonarsource.scanner.lib.internal.MessageException;

import static java.lang.String.format;

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
          regionCode, StringUtils.join(OfficialSonarQubeCloudInstance.getRegionCodes(), ", "), ScannerProperties.SONAR_REGION, EnvironmentConfig.REGION_ENV_VARIABLE)))
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
      throw new MessageException(format("Defining a custom '%s' without '%s' is not supported.", ScannerProperties.SONARQUBE_CLOUD_URL, ScannerProperties.API_BASE_URL));
    }
    return new ScannerEndpoint(
      properties.get(ScannerProperties.SONARQUBE_CLOUD_URL),
      properties.get(ScannerProperties.API_BASE_URL), true);
  }

  private static MessageException defining2incompatiblePropertiesUnsupported(String prop1, String prop2) {
    return new MessageException(format("Defining '%s' and '%s' at the same time is not supported.", prop1, prop2));
  }

  private static ScannerEndpoint resolveEndpointFromSonarHostUrl(Map<String, String> properties) {
    return maybeResolveOfficialSonarQubeCloud(properties, ScannerProperties.HOST_URL)
      .orElse(new SonarQubeServer(properties.get(ScannerProperties.HOST_URL)));
  }

  private static Optional<ScannerEndpoint> maybeResolveOfficialSonarQubeCloud(Map<String, String> properties, String urlPropName) {
    var maybeCloudInstance = OfficialSonarQubeCloudInstance.fromWebEndpoint(properties.get(urlPropName));
    var hasRegion = properties.containsKey(ScannerProperties.SONAR_REGION);
    if (maybeCloudInstance.isPresent()) {
      if (hasRegion && !OfficialSonarQubeCloudInstance.fromRegionCode(properties.get(ScannerProperties.SONAR_REGION)).equals(maybeCloudInstance)) {
        throw defining2incompatiblePropertiesUnsupported(ScannerProperties.SONAR_REGION, urlPropName);
      }
      return Optional.of(maybeCloudInstance.get().getEndpoint());
    }
    if (hasRegion) {
      throw defining2incompatiblePropertiesUnsupported(ScannerProperties.SONAR_REGION, urlPropName);
    }
    return Optional.empty();
  }

}
