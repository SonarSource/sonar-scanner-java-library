/*
 * SonarScanner Java Library
 * Copyright (C) 2011-2025 SonarSource Sàrl
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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OfficialSonarQubeCloudInstanceTest {

  @Test
  void shouldListRegionsCodes() {
    assertThat(OfficialSonarQubeCloudInstance.getRegionCodesWithoutGlobal()).containsExactlyInAnyOrder("us");
  }

  @Test
  void shouldGetInstanceFromRegionCodeIgnoringCase() {
    assertThat(OfficialSonarQubeCloudInstance.fromRegionCode("US")).contains(OfficialSonarQubeCloudInstance.US);
    assertThat(OfficialSonarQubeCloudInstance.fromRegionCode("us")).contains(OfficialSonarQubeCloudInstance.US);
    assertThat(OfficialSonarQubeCloudInstance.fromRegionCode("")).contains(OfficialSonarQubeCloudInstance.GLOBAL);
    assertThat(OfficialSonarQubeCloudInstance.fromRegionCode(null)).contains(OfficialSonarQubeCloudInstance.GLOBAL);
    assertThat(OfficialSonarQubeCloudInstance.fromRegionCode("foo")).isEmpty();
    // For now, we are not sure the default region will be called "global" so don't let users use this enum value
    assertThat(OfficialSonarQubeCloudInstance.fromRegionCode("global")).isEmpty();
  }

  @Test
  void shouldGetInstanceFromWebEndpoint() {
    assertThat(OfficialSonarQubeCloudInstance.fromWebEndpoint("https://sonarcloud.io")).contains(OfficialSonarQubeCloudInstance.GLOBAL);
    assertThat(OfficialSonarQubeCloudInstance.fromWebEndpoint("https://sonarqube.us")).contains(OfficialSonarQubeCloudInstance.US);
    assertThat(OfficialSonarQubeCloudInstance.fromWebEndpoint("https://sonarqube.fr")).isEmpty();
  }
}