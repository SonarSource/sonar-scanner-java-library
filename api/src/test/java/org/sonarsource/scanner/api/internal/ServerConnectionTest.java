/*
 * SonarQube Scanner API
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
package org.sonarsource.scanner.api.internal;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import java.io.File;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.scanner.api.internal.ServerConnection;
import org.sonarsource.scanner.api.internal.cache.Logger;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class ServerConnectionTest {

  public static final String HELLO_WORLD = "hello, world!";

  @Rule
  public MockWebServer server = new MockWebServer();

  String serverUrl;

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private Logger logger;

  @Before
  public void setUp() throws Exception {
    serverUrl = server.url("").url().toString();

    logger = mock(Logger.class);
  }

  @Test
  public void download_success() throws Exception {
    ServerConnection connection = create(false, false);
    answer(HELLO_WORLD);

    String response = connection.downloadString("/batch/index.txt");

    assertThat(response).isEqualTo(HELLO_WORLD);
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
  public void should_support_server_url_without_trailing_slash() throws Exception {
    Properties props = new Properties();
    props.put("sonar.host.url", serverUrl.replaceAll("(/)+$", ""));
    ServerConnection connection = ServerConnection.create(props, logger);

    answer(HELLO_WORLD);
    String content = connection.downloadString("/batch/index.txt");
    assertThat(content).isEqualTo(HELLO_WORLD);
  }

  @Test
  public void should_support_server_url_with_trailing_slash() throws Exception {
    Properties props = new Properties();
    props.put("sonar.host.url", serverUrl.replaceAll("(/)+$", "") + "/");
    ServerConnection connection = ServerConnection.create(props, logger);

    answer(HELLO_WORLD);
    String content = connection.downloadString("/batch/index.txt");
    assertThat(content).isEqualTo(HELLO_WORLD);
  }

  private ServerConnection create(boolean enableCache, boolean preferCache) {
    return new ServerConnection(serverUrl, "user-agent", logger);
  }

  private void answer(String msg) {
    MockResponse response = new MockResponse().setBody(msg);
    server.enqueue(response);
  }
}
