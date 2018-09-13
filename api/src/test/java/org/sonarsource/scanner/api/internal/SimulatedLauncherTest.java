/*
 * SonarQube Scanner API
 * Copyright (C) 2011-2018 SonarSource SA
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
package org.sonarsource.scanner.api.internal;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.scanner.api.internal.batch.LogOutput;
import org.sonarsource.scanner.api.internal.cache.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class SimulatedLauncherTest {
  private static final String VERSION = "5.2";
  private SimulatedLauncher launcher;
  private Logger logger;
  private String filename;
  private LogOutput logOutput = (m, l) -> {
  };

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void setUp() {
    logger = mock(Logger.class);
    launcher = new SimulatedLauncher(VERSION, logger);
    filename = new File(temp.getRoot(), "props").getAbsolutePath();
  }

  @Test(expected = IllegalStateException.class)
  public void failIfInvalidFile() {
    Map<String, String> props = createProperties();
    props.put(InternalProperties.SCANNER_DUMP_TO_FILE, temp.getRoot().getAbsolutePath());

    launcher.execute(props, logOutput);
  }

  @Test(expected = IllegalStateException.class)
  public void failToWriteProperty() throws IOException {
    Map<String, String> props = createProperties();
    BufferedWriter writer = mock(BufferedWriter.class);
    doThrow(new IOException("error")).when(writer).write(anyString());

    SimulatedLauncher.writeProp(writer, props.entrySet().iterator().next());
  }

  @Test
  public void testDump() throws IOException {
    Map<String, String> props = createProperties();
    launcher.execute(props, logOutput);
    assertDump(props);
  }

  @Test(expected = IllegalStateException.class)
  public void error_dump() throws IOException {
    Map<String, String> prop = new HashMap<>();
    prop.put(InternalProperties.SCANNER_DUMP_TO_FILE, "an invalid # file \\//?name*?\"");
    launcher.execute(prop, logOutput);
  }

  private Map<String, String> createProperties() {
    Map<String, String> prop = new HashMap<>();
    prop.put("key1:subkey", "value1");
    prop.put("key2", "value2");
    prop.put(InternalProperties.SCANNER_DUMP_TO_FILE, filename);
    return prop;
  }

  @Test
  public void version() {
    assertThat(launcher.getVersion()).isEqualTo(VERSION);
  }

  private void assertDump(Map<String, String> props) throws IOException {
    if (props != null) {
      Properties p = new Properties();
      p.load(new FileInputStream(new File(filename)));
      assertThat(p).isEqualTo(props);
    } else {
      assertThat(new File(filename)).doesNotExist();
    }
  }

}
