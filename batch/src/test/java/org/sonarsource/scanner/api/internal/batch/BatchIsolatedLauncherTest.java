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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.Before;
import org.junit.Test;
import org.sonar.batch.bootstrapper.Batch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyListOf;
import static org.mockito.ArgumentMatchers.isNull;
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
  public void executeOld() {
    when(factory.createBatch(any(Properties.class), isNull(), anyListOf(Object.class))).thenReturn(batch);
    Properties prop = new Properties();
    List<Object> list = new LinkedList<>();

    launcher.executeOldVersion(prop, list);

    verify(factory).createBatch(prop, null, list);
    verify(batch).execute();

    verifyNoMoreInteractions(batch);
    verifyNoMoreInteractions(factory);
  }

  @Test
  public void proxy() {
    when(factory.createBatch(any(Properties.class), isNull(), isNull())).thenReturn(batch);
    Properties prop = new Properties();

    launcher.start(prop, null);
    launcher.execute(prop);
    launcher.stop();

    verify(factory).createBatch(any(Properties.class), isNull(), isNull());
    verify(batch).start();
    verify(batch).executeTask((Map) prop);
    verify(batch).stop();

    verifyNoMoreInteractions(batch);
    verifyNoMoreInteractions(factory);
  }

}
