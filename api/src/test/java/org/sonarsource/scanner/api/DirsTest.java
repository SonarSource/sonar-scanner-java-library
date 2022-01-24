/*
 * SonarQube Scanner API
 * Copyright (C) 2011-2022 SonarSource SA
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
package org.sonarsource.scanner.api;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.scanner.api.internal.cache.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class DirsTest {

  private Map<String, String> p = new HashMap<>();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void should_init_default_task_work_dir() throws Exception {
    p.put("sonar.task", "views");
    new Dirs(mock(Logger.class)).init(p);

    File workDir = new File(p.get(ScannerProperties.WORK_DIR));
    assertThat(workDir).isNotNull().isDirectory();
    assertThat(workDir.getCanonicalPath()).isEqualTo(new File(".").getCanonicalPath());
  }

  @Test
  public void should_use_parameterized_task_work_dir() throws Exception {
    p.put("sonar.task", "views");
    p.put(ScannerProperties.WORK_DIR, "generated/reports");
    new Dirs(mock(Logger.class)).init(p);

    File workDir = new File(p.get(ScannerProperties.WORK_DIR));
    assertThat(workDir).isNotNull();
    // separators from windows to unix
    assertThat(workDir.getCanonicalPath().replace("\\", "/")).contains("generated/reports");
  }

  @Test
  public void should_init_default_project_dirs() throws Exception {
    p.put("sonar.task", "scan");
    new Dirs(mock(Logger.class)).init(p);

    File projectDir = new File(p.get(ScanProperties.PROJECT_BASEDIR));
    File workDir = new File(p.get(ScannerProperties.WORK_DIR));

    assertThat(projectDir).isNotNull().isDirectory();
    assertThat(workDir).isNotNull();

    assertThat(projectDir.getCanonicalPath()).isEqualTo(new File(".").getCanonicalPath());
    assertThat(workDir.getName()).isEqualTo(".scannerwork");
    assertThat(workDir.getParentFile()).isEqualTo(projectDir);
  }

  @Test
  public void should_set_relative_path_to_project_work_dir() throws Exception {
    File initialProjectDir = temp.getRoot();
    p.put("sonar.task", "scan");
    p.put(ScannerProperties.WORK_DIR, "relative/path");
    p.put(ScanProperties.PROJECT_BASEDIR, initialProjectDir.getAbsolutePath());
    new Dirs(mock(Logger.class)).init(p);

    File projectDir = new File(p.get(ScanProperties.PROJECT_BASEDIR));
    File workDir = new File(p.get(ScannerProperties.WORK_DIR));

    assertThat(projectDir).isNotNull().isDirectory();
    assertThat(projectDir.getCanonicalPath()).isEqualTo(initialProjectDir.getCanonicalPath());

    assertThat(workDir).isNotNull();
    assertThat(workDir.getCanonicalPath()).isEqualTo(new File(projectDir, "relative/path").getCanonicalPath());
  }
}
