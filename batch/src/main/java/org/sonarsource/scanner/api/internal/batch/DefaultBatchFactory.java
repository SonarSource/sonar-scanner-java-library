/*
 * SonarQube Scanner API - Batch
 * Copyright (C) 2011-2018 SonarSource SA
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
package org.sonarsource.scanner.api.internal.batch;

import java.util.Map;
import org.sonar.batch.bootstrapper.Batch;
import org.sonar.batch.bootstrapper.EnvironmentInformation;

class DefaultBatchFactory implements BatchFactory {
  private static final String SCANNER_APP_KEY = "sonar.scanner.app";
  private static final String SCANNER_APP_VERSION_KEY = "sonar.scanner.appVersion";

  @Override
  public Batch createBatch(Map<String, String> properties, final org.sonarsource.scanner.api.internal.batch.LogOutput logOutput) {
    EnvironmentInformation env = new EnvironmentInformation(properties.get(SCANNER_APP_KEY), properties.get(SCANNER_APP_VERSION_KEY));
    return Batch.builder()
      .setEnvironment(env)
      .setGlobalProperties(properties)
      .setLogOutput((formattedMessage, level) -> logOutput.log(formattedMessage, LogOutput.Level.valueOf(level.name())))
      .build();
  }
}
