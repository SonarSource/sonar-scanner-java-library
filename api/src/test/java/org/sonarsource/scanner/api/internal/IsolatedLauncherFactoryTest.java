/*
 * SonarQube Scanner API
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
package org.sonarsource.scanner.api.internal;

import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import org.junit.Before;
import org.junit.Test;
import org.sonarsource.scanner.api.internal.batch.IsolatedLauncher;
import org.sonarsource.scanner.api.internal.batch.LogOutput;
import org.sonarsource.scanner.api.internal.cache.Logger;

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
