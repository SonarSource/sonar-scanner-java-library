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

import java.net.ConnectException;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.scanner.lib.internal.batch.IsolatedLauncher;
import org.sonarsource.scanner.lib.internal.batch.LogOutput;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    var rules = new ClassloadRules(new HashSet<>(), new HashSet<>());
    assertThatThrownBy(() -> factory.createLauncher(legacyScannerEngineDownloader, rules))
      .isInstanceOf(ScannerException.class);
  }

  @Test
  void should_omit_connection_error_exceptions_and_return_error_message() {
    when(legacyScannerEngineDownloader.getOrDownload()).thenThrow(
      new IllegalStateException("Fail to get bootstrap index from server", new ConnectException("Failed to connect to localhost/127.0.0.1:9000"))
    );
    assertThatThrownBy(() -> factory.createLauncher(legacyScannerEngineDownloader, new ClassloadRules(new HashSet<>(), new HashSet<>())))
      .isInstanceOf(ScannerException.class)
      .hasMessage("Unable to execute SonarScanner analysis: Failed to connect to localhost/127.0.0.1:9000");
  }

  public static class FakeIsolatedLauncher implements IsolatedLauncher {
    public static Map<String, String> props = null;

    @Override
    public void execute(Map<String, String> properties, LogOutput logOutput) {
      FakeIsolatedLauncher.props = properties;
    }
  }
}
