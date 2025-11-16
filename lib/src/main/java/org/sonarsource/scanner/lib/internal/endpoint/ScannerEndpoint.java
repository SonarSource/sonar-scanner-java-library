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

import java.util.Optional;
import javax.annotation.Nullable;

public class ScannerEndpoint {

  private final String webEndpoint;
  private final String apiEndpoint;
  private final boolean isSonarQubeCloud;
  private final String regionLabel;

  public ScannerEndpoint(String webEndpoint, String apiEndpoint, boolean isSonarQubeCloud, @Nullable String regionLabel) {
    this.webEndpoint = webEndpoint;
    this.apiEndpoint = apiEndpoint;
    this.isSonarQubeCloud = isSonarQubeCloud;
    this.regionLabel = regionLabel;
  }

  public boolean isSonarQubeCloud() {
    return isSonarQubeCloud;
  }

  public String getApiEndpoint() {
    return apiEndpoint;
  }

  public String getWebEndpoint() {
    return webEndpoint;
  }

  public Optional<String> getRegionLabel() {
    return Optional.ofNullable(regionLabel);
  }
}
