/*
 * SonarQube Scanner API - Batch
 * Copyright (C) 2011-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.sonar.batch.bootstrapper.Batch;
import org.sonarsource.scanner.api.internal.batch.IsolatedLauncher;

/**
 * This class is executed within the classloader provided by the server. It contains the installed plugins and
 * the same version of sonar-batch as the server.
 */
public class BatchIsolatedLauncher implements IsolatedLauncher {
  private Batch batch = null;
  private final BatchFactory factory;

  public BatchIsolatedLauncher() {
    
    this(new DefaultBatchFactory());
  }

  public BatchIsolatedLauncher(BatchFactory factory) {
    this.factory = factory;
  }

  @Override
  public void start(Properties globalProperties, org.sonarsource.scanner.api.internal.batch.LogOutput logOutput) {
    batch = factory.createBatch(globalProperties, logOutput, null);
    batch.start();
  }

  @Override
  public void stop() {
    batch.stop();
  }

  @Override
  public void execute(Properties properties) {
    batch.executeTask((Map) properties);
  }

  /**
   * This method exists for backward compatibility with SonarQube &lt; 5.2. 
   */
  @Override
  public void executeOldVersion(Properties properties, List<Object> extensions) {
    factory.createBatch(properties, null, extensions).execute();
  }

  @Override
  public String getVersion() {
    InputStream is = this.getClass().getClassLoader().getResourceAsStream("sq-version.txt");
    if (is == null) {
      return null;
    }
    try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      return br.readLine();
    } catch (IOException e) {
      return null;
    }
  }
}
