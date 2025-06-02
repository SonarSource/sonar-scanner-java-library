/*
 * SonarQube Scanner API - Batch
 * Copyright (C) 2011-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.scanner.api.internal.batch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
  public void execute(Map<String, String> properties, org.sonarsource.scanner.api.internal.batch.LogOutput logOutput) {
    factory.createBatch(properties, logOutput).execute();
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
