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
package org.sonarsource.scanner.lib.internal.facade.inprocess;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IsolatedClassloaderTest {

  private IsolatedClassloader classLoader;

  @BeforeEach
  void setUp() {
    ClassLoader parent = getClass().getClassLoader();
    classLoader = new IsolatedClassloader(parent, new ClassloadRules(new HashSet<String>(), new HashSet<String>()));
  }

  @Test
  void should_use_isolated_system_classloader_when_parent_is_excluded() throws ClassNotFoundException, IOException {
    // JUnit is available in the parent classloader (classpath used to execute this test) but not in the core JVM
    assertThat(classLoader.loadClass("java.lang.String", false)).isNotNull();

    assertThatThrownBy(() -> classLoader.loadClass("org.junit.jupiter.api.Test", false)).isInstanceOf(ClassNotFoundException.class).hasMessageContaining("org.junit.jupiter.api.Test");
    classLoader.close();
  }

  @Test
  void should_use_parent_to_load() throws ClassNotFoundException {
    ClassloadRules rules = mock(ClassloadRules.class);
    when(rules.canLoad("org.junit.jupiter.api.Test")).thenReturn(true);
    classLoader = new IsolatedClassloader(getClass().getClassLoader(), rules);
    assertThat(classLoader.loadClass("org.junit.jupiter.api.Test", false)).isNotNull();
  }

  @Test
  void add_jars() throws MalformedURLException {
    var f = Paths.get("dummy");
    Path[] files = {f};
    classLoader.addFiles(Arrays.asList(files));

    assertThat(classLoader.getURLs()).contains(f.toUri().toURL());
  }

  @Test
  void dont_get_resource_from_parent() {
    URL resource2 = classLoader.getParent().getResource("fake.jar");
    assertThat(resource2).isNotNull();

    // should not find resource through parent classloader
    URL resource = classLoader.getResource("fake.jar");
    assertThat(resource).isNull();
  }

  @Test
  void dont_get_resources_from_parent() throws IOException {
    Enumeration<URL> resource2 = classLoader.getParent().getResources("fake.jar");
    assertThat(resource2.hasMoreElements()).isTrue();

    // should not find resource through parent classloader
    Enumeration<URL> resource = classLoader.getResources("fake.jar");
    assertThat(resource.hasMoreElements()).isFalse();
  }
}
