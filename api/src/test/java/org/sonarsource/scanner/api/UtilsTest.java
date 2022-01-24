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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class UtilsTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Test
  public void should_join_strings() {
    assertThat(Utils.join(new String[] {}, ",")).isEqualTo("");
    assertThat(Utils.join(new String[] {"foo"}, ",")).isEqualTo("foo");
    assertThat(Utils.join(new String[] {"foo", "bar"}, ",")).isEqualTo("foo,bar");
  }

  @Test
  public void task_should_require_project() {
    Map<String, String> props = new HashMap<>();
    assertThat(Utils.taskRequiresProject(props)).isTrue();

    props.put("sonar.task", "scan");
    assertThat(Utils.taskRequiresProject(props)).isTrue();
  }

  @Test
  public void write_properties() throws IOException {
    File f = temp.newFile();
    Properties p = new Properties();
    p.put("key", "value");
    Utils.writeProperties(f, p);
    assertThat(Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)).contains("key=value");
  }

  @Test
  public void task_should_not_require_project() {
    Map<String, String> props = new HashMap<>();
    props.put("sonar.task", "views");
    assertThat(Utils.taskRequiresProject(props)).isFalse();
  }

  @Test
  public void delete_non_empty_directory() throws IOException {
    /*-
     * Create test structure:
     * tmp
     *   |-folder1
     *        |- file1
     *        |- folder2
     *             |- file2
     */
    Path tmpDir = Files.createTempDirectory("junit");
    Path folder1 = tmpDir.resolve("folder1");
    Files.createDirectories(folder1);
    Path file1 = folder1.resolve("file1");
    Files.write(file1, "test1".getBytes());

    Path folder2 = folder1.resolve("folder2");
    Files.createDirectories(folder2);
    Path file2 = folder1.resolve("file2");
    Files.write(file2, "test2".getBytes());

    // delete it
    assertThat(tmpDir.toFile()).exists();
    Utils.deleteQuietly(tmpDir);
    assertThat(tmpDir.toFile()).doesNotExist();
  }

  @Test
  public void shouldHandleNullParams() {
    assertThat(Utils.loadEnvironmentProperties(createSonarQubeScannerProps(null))).isEmpty();
  }

  @Test
  public void shouldHandleParams() {
    String props = "{\"sonar.login\" : \"admin\"}";
    assertThat(Utils.loadEnvironmentProperties(createSonarQubeScannerProps(props))).containsExactly(entry("sonar.login", "admin"));
  }

  @Test
  public void shouldHandleEmptyJsonInParams() {
    String props = "{}";
    assertThat(Utils.loadEnvironmentProperties(createSonarQubeScannerProps(props))).isEmpty();
  }

  @Test
  public void shouldHandleJsonErrorsInParams() {
    String props = "";
    exception.expect(IllegalStateException.class);
    exception.expectMessage("Failed to parse JSON");
    assertThat(Utils.loadEnvironmentProperties(createSonarQubeScannerProps(props))).isEmpty();
  }

  private Map<String, String> createSonarQubeScannerProps(String params) {
    Map<String, String> env = new HashMap<>();
    env.put("SONARQUBE_SCANNER_PARAMS", params);
    return env;
  }
}
