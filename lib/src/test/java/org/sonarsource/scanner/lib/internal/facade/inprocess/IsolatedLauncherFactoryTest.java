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
package org.sonarsource.scanner.lib.internal.facade.inprocess;

import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.scanner.lib.internal.batch.IsolatedLauncher;
import org.sonarsource.scanner.lib.internal.batch.LogOutput;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class IsolatedLauncherFactoryTest {
  IsolatedLauncherFactory factory;
  Properties props;
  TempCleaning tempCleaning;
  LegacyScannerEngineDownloader legacyScannerEngineDownloader;

  @BeforeEach
  void setUp() {
    tempCleaning = mock(TempCleaning.class);
    factory = new IsolatedLauncherFactory(FakeIsolatedLauncher.class.getName(), tempCleaning);
    props = new Properties();
    legacyScannerEngineDownloader = mock(LegacyScannerEngineDownloader.class);
  }

  @Test
  void should_use_isolated_classloader() {
    var rules = new ClassloadRules(new HashSet<String>(), new HashSet<String>());
    assertThrows(ScannerException.class, () -> {
      factory.createLauncher(legacyScannerEngineDownloader, rules);
    });
  }

  public static class FakeIsolatedLauncher implements IsolatedLauncher {
    public static Map<String, String> props = null;

    @Override
    public void execute(Map<String, String> properties, LogOutput logOutput) {
      FakeIsolatedLauncher.props = properties;
    }
  }
}
