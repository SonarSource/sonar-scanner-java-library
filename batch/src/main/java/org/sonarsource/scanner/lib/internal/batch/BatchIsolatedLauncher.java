/*
 * SonarScanner Java Library - Batch
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
package org.sonarsource.scanner.lib.internal.batch;

import java.util.Map;

/**
 * This class is executed within the classloader provided by the server. It contains the installed plugins and
 * the same version of sonar-batch as the server.
 */
public class BatchIsolatedLauncher implements IsolatedLauncher {
  private final BatchFactory factory;

  public BatchIsolatedLauncher() {
    this(new DefaultBatchFactory());
  }

  public BatchIsolatedLauncher(BatchFactory factory) {
    this.factory = factory;
  }

  @Override
  public void execute(Map<String, String> properties, LogOutput logOutput) {
    factory.createBatch(properties, logOutput).execute();
  }
}
