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
package org.sonar.runner.impl;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.runner.cache.Logger;
import org.sonar.runner.cache.PersistentCache;
import org.sonar.runner.cache.PersistentCacheBuilder;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ServerConnectionTest {

  public static final String HELLO_WORLD = "hello, world!";

  @Rule
  public MockWebServer server = new MockWebServer();

  String serverUrl;

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private PersistentCache cache;

  private Logger logger;

  @Before
  public void setUp() throws Exception {
    serverUrl = server.url("").url().toString();

    cache = new PersistentCacheBuilder(mock(Logger.class))
      .setAreaForGlobal("server")
      .setSonarHome(temp.getRoot().toPath())
      .build();
    logger = mock(Logger.class);
  }

  @Test
  public void ignore_error_on_cache_write() throws Exception {
    cache = mock(PersistentCache.class);
    doThrow(IOException.class).when(cache).put(anyString(), any(byte[].class));
    answer(HELLO_WORLD);

    ServerConnection connection = create(true, false);
    String response = connection.download("/batch/index.txt");

    assertThat(response).isEqualTo(HELLO_WORLD);
    verify(logger).warn(startsWith("Failed to cache WS call:"));
  }

  @Test
  public void download_success() throws Exception {
    ServerConnection connection = create(false, false);
    answer(HELLO_WORLD);

    String response = connection.download("/batch/index.txt");

    assertThat(response).isEqualTo(HELLO_WORLD);
  }

  @Test
  public void test_prefer_cache() throws IOException {
    File cacheDir = cache.getDirectory().toFile();
    answer(HELLO_WORLD);
    ServerConnection connection = create(true, true);

    //not cached
    assertThat(cacheDir.list().length).isEqualTo(0);
    String str = connection.download("/batch/index.txt");

    //cached
    assertThat(str).isEqualTo(HELLO_WORLD);
    assertThat(cacheDir.list().length).isEqualTo(1);

    //got response in cached
    str = connection.download("/batch/index.txt");
    assertThat(str).isEqualTo(HELLO_WORLD);
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  public void test_dont_prefer_cache() throws IOException {
    File cacheDir = cache.getDirectory().toFile();
    answer(HELLO_WORLD);
    ServerConnection connection = create(true, false);

    //not cached
    assertThat(cacheDir.list().length).isEqualTo(0);
    String str = connection.download("/batch/index.txt");

    //cached
    assertThat(str).isEqualTo(HELLO_WORLD);
    assertThat(cacheDir.list().length).isEqualTo(1);

    answer("request2");
    //got response in cached
    str = connection.download("/batch/index.txt");
    assertThat(str).isEqualTo("request2");

    server.shutdown();
    str = connection.download("/batch/index.txt");
    assertThat(str).isEqualTo("request2");
  }

  @Test
  public void test_downloadFile() throws Exception {
    File toFile = temp.newFile();
    answer(HELLO_WORLD);

    ServerConnection underTest = create(false, false);
    underTest.downloadFile("/batch/index.txt", toFile);

    assertThat(FileUtils.readFileToString(toFile)).isEqualTo(HELLO_WORLD);
  }

  @Test
  public void fallback_to_cache_if_connection_error() throws IOException {
    cache = mock(PersistentCache.class);
    ServerConnection underTest = new ServerConnection("http://locahost:1", "user-agent", false, true, cache, logger);

    try {
      underTest.download("/batch/index.txt");
      fail();
    } catch (IOException e) {
      verify(cache).getString(anyString());
    }
  }

  @Test
  public void should_cache_jar_list() throws Exception {
    File cacheDir = cache.getDirectory().toFile();
    answer(HELLO_WORLD);
    ServerConnection connection = create(true, false);

    assertThat(cacheDir.list().length).isEqualTo(0);
    String str = connection.download("/batch/index.txt");

    assertThat(str).isEqualTo(HELLO_WORLD);
    assertThat(cacheDir.list().length).isEqualTo(1);

    server.shutdown();
    str = connection.download("/batch/index.txt");
    assertThat(str).isEqualTo(HELLO_WORLD);
  }

  @Test
  public void fail_if_server_is_down_and_cache_is_disabled() throws IOException {
    File cacheDir = cache.getDirectory().toFile();
    ServerConnection underTest = create(false, false);

    answer(HELLO_WORLD);
    assertThat(cacheDir.list().length).isEqualTo(0);
    String str = underTest.download("/batch/index.txt");
    assertThat(str).isEqualTo(HELLO_WORLD);

    server.shutdown();
    try {
      underTest.download("/batch/index.txt");
      fail("exception expected");
    } catch (IOException e) {
      // cache never used
      assertThat(cacheDir.list().length).isEqualTo(0);
    }
  }

  @Test
  public void cache_should_be_disabled_by_default() throws Exception {
    Properties props = new Properties();
    props.put("sonar.host.url", serverUrl);
    ServerConnection connection = ServerConnection.create(props, cache, logger, true);

    assertThat(connection.isCacheEnabled()).isFalse();
  }

  @Test
  public void cache_should_be_enabled_if_issues_mode() throws Exception {
    Properties props = new Properties();
    props.put("sonar.host.url", serverUrl);
    props.put("sonar.analysis.mode", "issues");
    ServerConnection connection = ServerConnection.create(props, cache, logger, true);

    assertThat(connection.isCacheEnabled()).isTrue();
  }

  @Test
  public void should_support_server_url_without_trailing_slash() throws Exception {
    Properties props = new Properties();
    props.put("sonar.host.url", serverUrl.replaceAll("(/)+$", ""));
    ServerConnection connection = ServerConnection.create(props, cache, logger, true);

    answer(HELLO_WORLD);
    String content = connection.download("/batch/index.txt");
    assertThat(content).isEqualTo(HELLO_WORLD);
  }

  @Test
  public void should_support_server_url_with_trailing_slash() throws Exception {
    Properties props = new Properties();
    props.put("sonar.host.url", serverUrl.replaceAll("(/)+$", "") + "/");
    ServerConnection connection = ServerConnection.create(props, cache, logger, true);

    answer(HELLO_WORLD);
    String content = connection.download("/batch/index.txt");
    assertThat(content).isEqualTo(HELLO_WORLD);
  }

  private ServerConnection create(boolean enableCache, boolean preferCache) {
    return new ServerConnection(serverUrl, "user-agent", preferCache, enableCache, cache, logger);
  }

  private void answer(String msg) {
    MockResponse response = new MockResponse().setBody(msg);
    server.enqueue(response);
  }
}
