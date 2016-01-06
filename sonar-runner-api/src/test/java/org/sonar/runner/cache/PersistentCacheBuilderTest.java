/*
 * SonarQube Runner - API
 * Copyright (C) 2011-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonar.runner.cache;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class PersistentCacheBuilderTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  private Map<String, String> savedEnv;

  @Before
  public void saveEnv() {
    savedEnv = new HashMap<>(System.getenv());
  }

  @After
  public void restoreEnv() throws Exception {
    setEnvForTesting(savedEnv);
  }

  @Test
  public void user_home_property_can_be_null() {
    PersistentCache cache = new PersistentCacheBuilder(mock(Logger.class)).setSonarHome(null).setAreaForGlobal("url").build();
    assertTrue(Files.isDirectory(cache.getDirectory()));
    assertThat(cache.getDirectory()).endsWith(Paths.get("url", "global"));
  }

  @Test
  public void set_user_home() {
    PersistentCache cache = new PersistentCacheBuilder(mock(Logger.class)).setSonarHome(temp.getRoot().toPath()).setAreaForGlobal("url").build();

    assertThat(cache.getDirectory()).isDirectory();
    assertThat(cache.getDirectory()).startsWith(temp.getRoot().toPath());
    assertTrue(Files.isDirectory(cache.getDirectory()));
  }

  @Test
  public void read_system_env() throws Exception {
    File homeFromEnv = temp.newFolder();
    File homeFromSysProp = temp.newFolder();

    HashMap<String, String> env = new HashMap<>();
    env.put("SONAR_USER_HOME", homeFromEnv.getAbsolutePath());
    setEnvForTesting(env);

    PersistentCache cache = new PersistentCacheBuilder(mock(Logger.class)).setAreaForGlobal("url").build();
    assertTrue(Files.isDirectory(cache.getDirectory()));
    assertThat(cache.getDirectory()).startsWith(homeFromEnv.toPath());

    setEnvForTesting(new HashMap<String, String>());
    System.setProperty("user.home", homeFromSysProp.getAbsolutePath());

    cache = new PersistentCacheBuilder(mock(Logger.class)).setAreaForGlobal("url").build();
    assertTrue(Files.isDirectory(cache.getDirectory()));
    assertThat(cache.getDirectory()).startsWith(homeFromSysProp.toPath());
  }

  @Test
  public void directories() {
    System.setProperty("user.home", temp.getRoot().getAbsolutePath());

    PersistentCache cache = new PersistentCacheBuilder(mock(Logger.class)).setAreaForProject("url", "0", "proj").build();
    assertThat(cache.getDirectory()).endsWith(Paths.get(".sonar", "ws_cache", "url", "0", "projects", "proj"));

    cache = new PersistentCacheBuilder(mock(Logger.class)).setAreaForLocalProject("url", "0").build();
    assertThat(cache.getDirectory()).endsWith(Paths.get(".sonar", "ws_cache", "url", "0", "local"));

    cache = new PersistentCacheBuilder(mock(Logger.class)).setAreaForGlobal("url").build();
    assertThat(cache.getDirectory()).endsWith(Paths.get(".sonar", "ws_cache", "url", "global"));
  }

  protected static void setEnvForTesting(Map<String, String> newenv) throws Exception {
    try {
      Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
      Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
      theEnvironmentField.setAccessible(true);
      Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
      env.putAll(newenv);
      Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
      theCaseInsensitiveEnvironmentField.setAccessible(true);
      Map<String, String> cienv = (Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
      cienv.putAll(newenv);
    } catch (NoSuchFieldException e) {
      Class[] classes = Collections.class.getDeclaredClasses();
      Map<String, String> env = System.getenv();
      for (Class cl : classes) {
        if ("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
          Field field = cl.getDeclaredField("m");
          field.setAccessible(true);
          Object obj = field.get(env);
          Map<String, String> map = (Map<String, String>) obj;
          map.clear();
          map.putAll(newenv);
        }
      }
    }
  }
}
