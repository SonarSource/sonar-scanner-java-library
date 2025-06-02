/*
 * SonarQube Scanner API
 * Copyright (C) 2011-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.scanner.api.internal;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.scanner.api.internal.cache.Logger;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
  public void downloadString_fails_on_url_validation() {
    ServerConnection connection = create(false, false);
    answer(HELLO_WORLD);

    assertThatThrownBy(() -> connection.downloadString("should_fail"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("URL path must start with slash: should_fail");
  }

  @Test
  public void test_downloadFile() throws Exception {
    Path toFile = temp.newFile().toPath();
    answer(HELLO_WORLD);

    ServerConnection underTest = create(false, false);
    underTest.downloadFile("/batch/index.txt", toFile);

    assertThat(new String(Files.readAllBytes(toFile), StandardCharsets.UTF_8)).isEqualTo(HELLO_WORLD);
  }

  @Test
  public void downloadFile_fails_on_url_validation() {
    ServerConnection connection = create(false, false);
    answer(HELLO_WORLD);

    assertThatThrownBy(() -> connection.downloadFile("should_fail", Paths.get("test-path")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("URL path must start with slash: should_fail");
  }

  @Test
  public void should_throw_ISE_if_response_not_successful() throws Exception {
    Path toFile = temp.newFile().toPath();
    answer(HELLO_WORLD, 400);

    ServerConnection underTest = create(false, false);
    assertThatThrownBy(() -> underTest.downloadFile("/batch/index.txt", toFile))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage(format("Status returned by url [http://%s:%d/batch/index.txt] is not valid: [400]", server.getHostName(), server.getPort()));
  }

  @Test
  public void should_support_server_url_without_trailing_slash() throws Exception {
    Map<String, String> props = new HashMap<>();
    props.put("sonar.host.url", serverUrl.replaceAll("(/)+$", ""));
    ServerConnection connection = ServerConnection.create(props, logger);

    answer(HELLO_WORLD);
    String content = connection.downloadString("/batch/index.txt");
    assertThat(content).isEqualTo(HELLO_WORLD);
  }

  @Test
  public void should_support_server_url_with_trailing_slash() throws Exception {
    Map<String, String> props = new HashMap<>();
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
    answer(msg, 200);
  }

  private void answer(String msg, int responseCode) {
    MockResponse response = new MockResponse().setBody(msg).setResponseCode(responseCode);
    server.enqueue(response);
  }
}
