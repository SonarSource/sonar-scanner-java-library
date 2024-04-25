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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentMatcher;
import org.sonarsource.scanner.lib.internal.ClassloadRules;
import org.sonarsource.scanner.lib.internal.IsolatedLauncherFactory;
import org.sonarsource.scanner.lib.internal.batch.IsolatedLauncher;
import org.sonarsource.scanner.lib.internal.cache.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EmbeddedScannerTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void should_create() {
    assertThat(EmbeddedScanner.create("test", "1.0", mock(LogOutput.class))).isNotNull().isInstanceOf(EmbeddedScanner.class);
  }

  private IsolatedLauncherFactory launcherFactory;
  private IsolatedLauncher launcher;
  private EmbeddedScanner scanner;
  private Logger logger;
  private System2 system;

  @Before
  public void setUp() {
    launcherFactory = mock(IsolatedLauncherFactory.class);
    launcher = mock(IsolatedLauncher.class);
    logger = mock(Logger.class);
    system = mock(System2.class);

    when(launcher.getVersion()).thenReturn("5.2");
    when(launcherFactory.createLauncher(anyMap(), any(ClassloadRules.class))).thenReturn(launcher);
    scanner = new EmbeddedScanner(launcherFactory, logger, mock(LogOutput.class), system);
  }

  @Test
  public void test_server_version() {
    scanner.start();
    assertThat(scanner.serverVersion()).isEqualTo("5.2");
  }

  @Test
  public void test_run_before_start() {
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("started");

    scanner.execute(new HashMap<>());
  }

  @Test
  public void test_app() {
    EmbeddedScanner scanner = EmbeddedScanner.create("Eclipse", "3.1", mock(LogOutput.class));
    assertThat(scanner.app()).isEqualTo("Eclipse");
    assertThat(scanner.appVersion()).isEqualTo("3.1");
  }

  @Test
  public void should_set_localhost_as_host_by_default() {
    scanner.start();

    assertThat(scanner.globalProperty("sonar.host.url", null)).isEqualTo("http://localhost:9000");
  }

  @Test
  public void should_set_sonarcloud_as_host_if_executed_from_bitbucket_cloud_and_no_host_env() {
    when(system.getEnvironmentVariable("BITBUCKET_BUILD_NUMBER")).thenReturn("123");

    scanner.start();

    assertThat(scanner.globalProperty("sonar.host.url", null)).isEqualTo("https://sonarcloud.io");
  }

  @Test
  public void should_set_url_from_env_as_host_if_host_env_var_provided_even_on_bitbucket_cloud() {
    when(system.getEnvironmentVariable("BITBUCKET_BUILD_NUMBER")).thenReturn("123");
    when(system.getEnvironmentVariable("SONAR_HOST_URL")).thenReturn("http://from-env.org:9000");

    scanner.start();

    assertThat(scanner.globalProperty("sonar.host.url", null)).isEqualTo("http://from-env.org:9000");
  }

  @Test
  public void should_set_url_from_env_as_host_if_host_env_var_provided() {
    when(system.getEnvironmentVariable("SONAR_HOST_URL")).thenReturn("http://from-env.org:9000");

    scanner.start();

    assertThat(scanner.globalProperty("sonar.host.url", null)).isEqualTo("http://from-env.org:9000");
  }

  @Test
  public void should_set_properties() {
    EmbeddedScanner scanner = EmbeddedScanner.create("test", "1.0", mock(LogOutput.class));
    scanner.setGlobalProperty("sonar.projectKey", "foo");
    scanner.addGlobalProperties(new HashMap<String, String>() {
      {
        put("sonar.login", "admin");
        put("sonar.password", "gniark");
      }
    });

    assertThat(scanner.globalProperty("sonar.projectKey", null)).isEqualTo("foo");
    assertThat(scanner.globalProperty("sonar.login", null)).isEqualTo("admin");
    assertThat(scanner.globalProperty("sonar.password", null)).isEqualTo("gniark");
    assertThat(scanner.globalProperty("not.set", "this_is_default")).isEqualTo("this_is_default");
  }

  @Test
  public void should_launch_scanner() {
    scanner.setGlobalProperty("sonar.projectKey", "foo");
    scanner.start();
    scanner.execute(new HashMap<>());

    verify(launcherFactory).createLauncher(argThat(new ArgumentMatcher<Map>() {
      @Override
      public boolean matches(Map o) {
        return "foo".equals(o.get("sonar.projectKey"));
      }
    }), any(ClassloadRules.class));

    // it should have added a few properties to analysisProperties
    final String[] mustHaveKeys = {"sonar.working.directory", "sonar.sourceEncoding", "sonar.projectBaseDir"};

    verify(launcher).execute(argThat(new ArgumentMatcher<Map>() {
      @Override
      public boolean matches(Map o) {
        for (String s : mustHaveKeys) {
          if (!o.containsKey(s)) {
            return false;
          }
        }
        return true;
      }
    }), any(org.sonarsource.scanner.lib.internal.batch.LogOutput.class));
  }

  @Test
  public void should_launch_scanner_analysisProperties() {
    scanner.setGlobalProperty("sonar.projectKey", "foo");
    scanner.start();

    Map<String, String> analysisProperties = new HashMap<>();
    analysisProperties.put("sonar.projectKey", "value1");
    scanner.execute(analysisProperties);

    verify(launcherFactory).createLauncher(argThat(new ArgumentMatcher<Map>() {
      @Override
      public boolean matches(Map o) {
        return "foo".equals(o.get("sonar.projectKey"));
      }
    }), any(ClassloadRules.class));

    verify(launcher).execute(argThat(new ArgumentMatcher<Map>() {
      @Override
      public boolean matches(Map o) {
        return "value1".equals(o.get("sonar.projectKey"));
      }
    }), any(org.sonarsource.scanner.lib.internal.batch.LogOutput.class));
  }

  @Test
  public void should_launch_in_simulation_mode() throws IOException {
    launcherFactory = new IsolatedLauncherFactory(mock(Logger.class));
    scanner = new EmbeddedScanner(launcherFactory, mock(Logger.class), mock(LogOutput.class), system);

    File dump = temp.newFile();
    Map<String, String> p = new HashMap<>();

    p.put("sonar.projectKey", "foo");
    scanner.setGlobalProperty("sonar.scanner.dumpToFile", dump.getAbsolutePath());
    scanner.start();
    scanner.execute(p);

    Properties props = new Properties();
    props.load(new FileInputStream(dump));

    assertThat(props.getProperty("sonar.projectKey")).isEqualTo("foo");
  }

  @Test
  public void should_set_default_platform_encoding() throws Exception {
    Map<String, String> p = new HashMap<>();
    p.put("sonar.task", "scan");
    scanner.initSourceEncoding(p);
    assertThat(p.get("sonar.sourceEncoding")).isEqualTo(Charset.defaultCharset().name());
    verify(logger).info("Default locale: \"" + Locale.getDefault() + "\", source code encoding: \"" + Charset.defaultCharset().name() + "\" (analysis is platform dependent)");
  }

  @Test
  public void should_set_default_platform_encoding_when_empty() throws Exception {
    Map<String, String> p = new HashMap<>();
    p.put("sonar.task", "scan");
    p.put("sonar.sourceEncoding", "");
    scanner.initSourceEncoding(p);
    assertThat(p.get("sonar.sourceEncoding")).isEqualTo(Charset.defaultCharset().name());
    verify(logger).info("Default locale: \"" + Locale.getDefault() + "\", source code encoding: \"" + Charset.defaultCharset().name() + "\" (analysis is platform dependent)");
  }

  @Test
  public void cannot_start_twice() {
    scanner.start();

    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("started");
    scanner.start();
  }

  @Test
  public void should_use_parameterized_encoding() throws Exception {
    Map<String, String> p = new HashMap<>();
    p.put("sonar.task", "scan");
    p.put("sonar.sourceEncoding", "THE_ISO_1234");
    scanner.initSourceEncoding(p);
    assertThat(p.get("sonar.sourceEncoding")).isEqualTo("THE_ISO_1234");
  }

  @Test
  public void should_not_init_encoding_if_not_project_task() throws Exception {
    Map<String, String> p = new HashMap<>();
    p.put("sonar.task", "views");
    scanner.initSourceEncoding(p);
    assertThat(p.get("sonar.sourceEncoding")).isNull();
  }

}
