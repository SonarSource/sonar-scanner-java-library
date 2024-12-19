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
package org.sonarsource.scanner.lib.internal.facade.simulation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.scanner.lib.internal.InternalProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationScannerEngineFacadeTest {

  private SimulationScannerEngineFacade underTest;
  private String filename;

  @TempDir
  private File temp;

  @BeforeEach
  void setUp() {
    underTest = new SimulationScannerEngineFacade(new HashMap<>(), true, null);
    filename = new File(temp, "props").getAbsolutePath();
  }

  @Test
  void failIfInvalidFile() {
    Map<String, String> props = createProperties();
    props.put(InternalProperties.SCANNER_DUMP_TO_FILE, temp.getAbsolutePath());

    assertThatThrownBy(() -> underTest.doAnalyze(props))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Fail to export scanner properties");
  }

  @Test
  void testDump() throws IOException {
    Map<String, String> props = createProperties();
    underTest.doAnalyze(props);
    assertDump(props);
  }

  @Test
  void error_dump() {
    Map<String, String> props = new HashMap<>();
    props.put(InternalProperties.SCANNER_DUMP_TO_FILE, "an invalid # file \\//?name*?\"");
    assertThatThrownBy(() -> underTest.doAnalyze(props))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Fail to export scanner properties");
  }

  private Map<String, String> createProperties() {
    Map<String, String> prop = new HashMap<>();
    prop.put("key1:subkey", "value1");
    prop.put("key2", "value2");
    prop.put(InternalProperties.SCANNER_DUMP_TO_FILE, filename);
    return prop;
  }

  private void assertDump(Map<String, String> props) throws IOException {
    Properties p = new Properties();
    try (FileInputStream fis = new FileInputStream(filename)) {
      p.load(fis);
    }
    assertThat(p).isEqualTo(props);
  }
}
