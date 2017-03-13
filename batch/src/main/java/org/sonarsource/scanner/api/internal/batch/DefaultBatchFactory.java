/*
 * SonarQube Scanner API - Batch
 * Copyright (C) 2011-2017 SonarSource SA
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

import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.picocontainer.annotations.Nullable;
import org.sonar.batch.bootstrapper.Batch;
import org.sonar.batch.bootstrapper.EnvironmentInformation;

class DefaultBatchFactory implements BatchFactory {
  private static final String SCANNER_APP_KEY = "sonar.scanner.app";
  private static final String SCANNER_APP_VERSION_KEY = "sonar.scanner.appVersion";
  
  @Override
  public Batch createBatch(Properties properties, @Nullable final org.sonarsource.scanner.api.internal.batch.LogOutput logOutput, @Nullable List<Object> extensions) {
    EnvironmentInformation env = new EnvironmentInformation(properties.getProperty(SCANNER_APP_KEY), properties.getProperty(SCANNER_APP_VERSION_KEY));
    Batch.Builder builder = Batch.builder()
      .setEnvironment(env)
      .setBootstrapProperties((Map) properties);

    if (extensions != null) {
      builder.addComponents(extensions);
    }

    if (logOutput != null) {
      // Do that in a separate class to avoid NoClassDefFoundError for org/sonar/batch/bootstrapper/LogOutput
      Compatibility.setLogOutputFor5dot2(builder, logOutput);
    }

    return builder.build();
  }
}
