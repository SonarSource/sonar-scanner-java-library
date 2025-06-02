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

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.sonar.batch.bootstrapper.Batch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class BatchIsolatedLauncherTest {
  private Batch batch;
  private BatchFactory factory;
  private BatchIsolatedLauncher launcher;

  @Before
  public void setUp() {
    factory = mock(BatchFactory.class);
    batch = mock(Batch.class);
    launcher = new BatchIsolatedLauncher(factory);
  }

  @Test
  public void proxy() {
    when(factory.createBatch(any(Map.class), any(org.sonarsource.scanner.api.internal.batch.LogOutput.class))).thenReturn(batch);
    HashMap<String, String> prop = new HashMap<>();

    launcher.execute(prop, (m, l) -> {
    });

    verify(factory).createBatch(any(Map.class), any(org.sonarsource.scanner.api.internal.batch.LogOutput.class));
    verify(batch).execute();

    verifyNoMoreInteractions(batch);
    verifyNoMoreInteractions(factory);
  }

}
