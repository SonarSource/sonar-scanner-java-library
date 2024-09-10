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
package org.sonarsource.scanner.lib;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class DirsTest {

  private final Map<String, String> p = new HashMap<>();

  @Test
  void should_init_default_project_dirs() throws Exception {
    new Dirs().init(p);

    File projectDir = new File(p.get(AnalysisProperties.PROJECT_BASEDIR));
    File workDir = new File(p.get(ScannerProperties.WORK_DIR));

    assertThat(projectDir).isNotNull().isDirectory();
    assertThat(projectDir.getCanonicalPath()).isEqualTo(new File(".").getCanonicalPath());
    assertThat(workDir)
      .hasName(".scannerwork")
      .hasParent(projectDir);
  }

  @Test
  void should_set_relative_path_to_project_work_dir(@TempDir File initialProjectDir) throws Exception {
    p.put(ScannerProperties.WORK_DIR, "relative/path");
    p.put(AnalysisProperties.PROJECT_BASEDIR, initialProjectDir.getAbsolutePath());
    new Dirs().init(p);

    File projectDir = new File(p.get(AnalysisProperties.PROJECT_BASEDIR));
    File workDir = new File(p.get(ScannerProperties.WORK_DIR));

    assertThat(projectDir).isNotNull().isDirectory();
    assertThat(projectDir.getCanonicalPath()).isEqualTo(initialProjectDir.getCanonicalPath());

    assertThat(workDir).isNotNull();
    assertThat(workDir.getCanonicalPath()).isEqualTo(new File(projectDir, "relative/path").getCanonicalPath());
  }
}
