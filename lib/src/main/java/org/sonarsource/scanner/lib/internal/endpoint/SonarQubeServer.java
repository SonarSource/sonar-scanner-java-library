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

import org.apache.commons.lang3.StringUtils;

public class SonarQubeServer extends ScannerEndpoint {

  public SonarQubeServer(String webEndpoint) {
    super(webEndpoint, buildApiEndpoint(webEndpoint), false, null);
  }

  private static String buildApiEndpoint(String webEndpoint) {
    return StringUtils.removeEnd(webEndpoint, "/") + "/api/v2";
  }

}
