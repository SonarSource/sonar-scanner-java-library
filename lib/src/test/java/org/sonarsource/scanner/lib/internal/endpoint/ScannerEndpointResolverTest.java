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
import org.junit.jupiter.api.Test;
import org.sonarsource.scanner.lib.ScannerProperties;
import org.sonarsource.scanner.lib.internal.MessageException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScannerEndpointResolverTest {

  @Test
  void should_resolve_sonarqube_cloud_global_by_default() throws Exception {
    var endpoint = ScannerEndpointResolver.resolveEndpoint(Map.of());

    assertThat(endpoint.isSonarQubeCloud()).isTrue();
    assertThat(endpoint.getWebEndpoint()).isEqualTo("https://sonarcloud.io");
    assertThat(endpoint.getApiEndpoint()).isEqualTo("https://api.sonarcloud.io");
  }

  @Test
  void should_recognize_sonarqube_cloud_endpoint_passed_through_host_url() throws Exception {
    var props = Map.of(ScannerProperties.HOST_URL, "https://sonarcloud.io");

    var endpoint = ScannerEndpointResolver.resolveEndpoint(props);

    assertThat(endpoint.isSonarQubeCloud()).isTrue();
    assertThat(endpoint.getWebEndpoint()).isEqualTo("https://sonarcloud.io");
    assertThat(endpoint.getApiEndpoint()).isEqualTo("https://api.sonarcloud.io");
  }

  @Test
  void should_recognize_sonarqube_cloud_endpoint_passed_through_cloud_url() throws Exception {
    var props = Map.of(ScannerProperties.SONARQUBE_CLOUD_URL, "https://sonarcloud.io");

    var endpoint = ScannerEndpointResolver.resolveEndpoint(props);

    assertThat(endpoint.isSonarQubeCloud()).isTrue();
    assertThat(endpoint.getWebEndpoint()).isEqualTo("https://sonarcloud.io");
    assertThat(endpoint.getApiEndpoint()).isEqualTo("https://api.sonarcloud.io");
  }

  @Test
  void should_fail_if_region_and_host_url_inconsistent() {
    var props = Map.of(
      ScannerProperties.HOST_URL, "https://mysonarqube.mycompany.fr",
      ScannerProperties.SONAR_REGION, "us"
    );

    assertThatThrownBy(() -> ScannerEndpointResolver.resolveEndpoint(props))
      .isInstanceOf(MessageException.class)
      .hasMessage("Defining 'sonar.region' and 'sonar.host.url' at the same time is not supported.");
  }

  @Test
  void should_fail_if_region_and_cloud_url_inconsistent() {
    var props = Map.of(
      ScannerProperties.SONARQUBE_CLOUD_URL, "https://sonarcloud.io",
      ScannerProperties.SONAR_REGION, "us"
    );

    assertThatThrownBy(() -> ScannerEndpointResolver.resolveEndpoint(props))
      .isInstanceOf(MessageException.class)
      .hasMessage("Defining 'sonar.region' and 'sonar.scanner.sonarcloudUrl' at the same time is not supported.");
  }

  @Test
  void should_fail_if_region_and_custom_url_defined() {
    var props = Map.of(
      ScannerProperties.SONARQUBE_CLOUD_URL, "https://preprod.sonarcloud.io",
      ScannerProperties.SONAR_REGION, "us"
    );

    assertThatThrownBy(() -> ScannerEndpointResolver.resolveEndpoint(props))
      .isInstanceOf(MessageException.class)
      .hasMessage("Defining 'sonar.region' and 'sonar.scanner.sonarcloudUrl' at the same time is not supported.");
  }

  @Test
  void should_not_fail_if_region_and_url_consistent() throws Exception {
    var props = Map.of(
      ScannerProperties.SONARQUBE_CLOUD_URL, "https://sonarqube.us",
      ScannerProperties.SONAR_REGION, "us"
    );

    var endpoint = ScannerEndpointResolver.resolveEndpoint(props);

    assertThat(endpoint.isSonarQubeCloud()).isTrue();
    assertThat(endpoint.getWebEndpoint()).isEqualTo("https://sonarqube.us");
    assertThat(endpoint.getApiEndpoint()).isEqualTo("https://api.sonarqube.us");
  }

  @Test
  void should_fail_if_only_api_endpoint_defined() {
    var props = Map.of(ScannerProperties.API_BASE_URL, "https://api.preprod.sonarcloud.io");

    assertThatThrownBy(() -> ScannerEndpointResolver.resolveEndpoint(props))
      .isInstanceOf(MessageException.class)
      .hasMessage("Defining 'sonar.scanner.apiBaseUrl' without 'sonar.scanner.sonarcloudUrl' is not supported.");
  }

  @Test
  void should_fail_if_invalid_region() {
    var props = Map.of(ScannerProperties.SONAR_REGION, "fr");

    assertThatThrownBy(() -> ScannerEndpointResolver.resolveEndpoint(props))
      .isInstanceOf(MessageException.class)
      .hasMessage("Invalid region 'fr'. Valid regions are: us. Please check the 'sonar.region' property or the 'SONAR_REGION' environment variable.");
  }

  @Test
  void should_support_us_region() throws Exception {
    var props = Map.of(ScannerProperties.SONAR_REGION, "us");

    var endpoint = ScannerEndpointResolver.resolveEndpoint(props);

    assertThat(endpoint.isSonarQubeCloud()).isTrue();
    assertThat(endpoint.getWebEndpoint()).isEqualTo("https://sonarqube.us");
    assertThat(endpoint.getApiEndpoint()).isEqualTo("https://api.sonarqube.us");
  }

  @Test
  void should_throw_if_custom_sq_cloud_url_is_set_but_not_api_url() {
    var props = Map.of(ScannerProperties.SONARQUBE_CLOUD_URL, "https://preprod.sonarcloud.io");

    assertThatThrownBy(() -> ScannerEndpointResolver.resolveEndpoint(props))
      .isInstanceOf(MessageException.class)
      .hasMessage("Defining a custom 'sonar.scanner.sonarcloudUrl' without 'sonar.scanner.apiBaseUrl' is not supported.");
  }

  @Test
  void should_resolve_custom_sq_cloud_endpoint() {
    var props = Map.of(
      ScannerProperties.SONARQUBE_CLOUD_URL, "https://preprod.sonarcloud.io",
      ScannerProperties.API_BASE_URL, "https://api.preprod.sonarcloud.io"
    );

    var endpoint = ScannerEndpointResolver.resolveEndpoint(props);

    assertThat(endpoint.isSonarQubeCloud()).isTrue();
    assertThat(endpoint.getWebEndpoint()).isEqualTo("https://preprod.sonarcloud.io");
    assertThat(endpoint.getApiEndpoint()).isEqualTo("https://api.preprod.sonarcloud.io");
  }

}