/*
 * SonarScanner Java Library
 * Copyright (C) 2011-2024 SonarSource SA
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
package org.sonarsource.scanner.lib.internal;

import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import org.junit.Before;
import org.junit.Test;
import org.sonarsource.scanner.lib.internal.batch.IsolatedLauncher;
import org.sonarsource.scanner.lib.internal.batch.LogOutput;
import org.sonarsource.scanner.lib.internal.cache.Logger;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class IsolatedLauncherFactoryTest {
  IsolatedLauncherFactory factory;
  Properties props;
  TempCleaning tempCleaning;
  JarDownloader jarDownloader;

  @Before
  public void setUp() {
    tempCleaning = mock(TempCleaning.class);
    factory = new IsolatedLauncherFactory(FakeIsolatedLauncher.class.getName(), tempCleaning, mock(Logger.class));
    props = new Properties();
    jarDownloader = mock(JarDownloader.class);
  }

  @Test
  public void should_use_isolated_classloader() {
    try {
      factory.createLauncher(jarDownloader, new ClassloadRules(new HashSet<String>(), new HashSet<String>()));
      fail();
    } catch (ScannerException e) {
      // success
    }
  }

  public static class FakeIsolatedLauncher implements IsolatedLauncher {
    public static Map<String, String> props = null;

    @Override
    public void execute(Map<String, String> properties, LogOutput logOutput) {
      FakeIsolatedLauncher.props = properties;
    }

    @Override
    public String getVersion() {
      return null;
    }

  }
}
