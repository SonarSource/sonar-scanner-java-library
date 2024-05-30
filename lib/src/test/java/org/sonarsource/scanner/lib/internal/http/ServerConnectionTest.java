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
package org.sonarsource.scanner.lib.internal.http;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.scanner.lib.ScannerProperties;
import org.sonarsource.scanner.lib.internal.InternalProperties;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerConnectionTest {

  private static final String HELLO_WORLD = "hello, world!";

  @RegisterExtension
  static WireMockExtension sonarqube = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  @TempDir
  private Path sonarUserHome;

  @Test
  void download_success() throws Exception {
    ServerConnection connection = create();
    answer(HELLO_WORLD);

    String response = connection.callWebApi("/batch/index.txt");

    assertThat(response).isEqualTo(HELLO_WORLD);
  }

  @Test
  void callWebApi_fails_on_url_validation() {
    ServerConnection connection = create();
    answer(HELLO_WORLD);

    assertThatThrownBy(() -> connection.callWebApi("should_fail"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("URL path must start with slash: should_fail");
  }

  @Test
  void test_downloadFromWebApi(@TempDir Path tmpFolder) throws Exception {
    var toFile = tmpFolder.resolve("index.txt");
    answer(HELLO_WORLD);

    ServerConnection underTest = create();
    underTest.downloadFromWebApi("/batch/index.txt", toFile);

    assertThat(Files.readString(toFile)).isEqualTo(HELLO_WORLD);
  }

  @Test
  void downloadFromWebApi_fails_on_url_validation(@TempDir Path tmpFolder) {
    var toFile = tmpFolder.resolve("index.txt");
    ServerConnection connection = create();
    answer(HELLO_WORLD);

    assertThatThrownBy(() -> connection.downloadFromWebApi("should_fail", toFile))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("URL path must start with slash: should_fail");
  }

  @Test
  void should_throw_ISE_if_response_not_successful(@TempDir Path tmpFolder) {
    var toFile = tmpFolder.resolve("index.txt");
    answer(HELLO_WORLD, 400);

    ServerConnection underTest = create();
    assertThatThrownBy(() -> underTest.downloadFromWebApi("/batch/index.txt", toFile))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage(format("Error status returned by url [http://%s:%d/batch/index.txt]: 400", "localhost", sonarqube.getPort()));
  }

  @Test
  void should_support_server_url_without_trailing_slash() throws Exception {
    ServerConnection connection = create(sonarqube.baseUrl().replaceAll("(/)+$", ""));

    answer(HELLO_WORLD);
    String content = connection.callWebApi("/batch/index.txt");
    assertThat(content).isEqualTo(HELLO_WORLD);
  }

  @Test
  void should_support_server_url_with_trailing_slash() throws Exception {
    ServerConnection connection = create(sonarqube.baseUrl().replaceAll("(/)+$", "") + "/");

    answer(HELLO_WORLD);
    String content = connection.callWebApi("/batch/index.txt");
    assertThat(content).isEqualTo(HELLO_WORLD);
  }

  @Test
  void should_authenticate_with_token() throws Exception {
    Map<String, String> props = new HashMap<>();
    props.put("sonar.token", "some_token");
    ServerConnection connection = create(sonarqube.baseUrl(), props);

    answer(HELLO_WORLD);
    String content = connection.callWebApi("/batch/index.txt");
    assertThat(content).isEqualTo(HELLO_WORLD);

    sonarqube.verify(getRequestedFor(anyUrl())
      .withHeader("Authorization", equalTo("Bearer some_token")));
  }

  @Test
  void should_authenticate_with_username_password() throws Exception {
    Map<String, String> props = new HashMap<>();
    props.put("sonar.login", "some_username");
    props.put("sonar.password", "some_password");
    ServerConnection connection = create(sonarqube.baseUrl(), props);

    answer(HELLO_WORLD);
    String content = connection.callWebApi("/batch/index.txt");
    assertThat(content).isEqualTo(HELLO_WORLD);

    sonarqube.verify(getRequestedFor(anyUrl())
      .withHeader("Authorization",
        equalTo("Basic " + Base64.getEncoder().encodeToString("some_username:some_password".getBytes(StandardCharsets.UTF_8)))));
  }

  @Test
  void downloadFromExternalUrl_shouldNotPassAuth(@TempDir Path tmpFolder) throws Exception {
    var toFile = tmpFolder.resolve("index.txt");
    answer(HELLO_WORLD);

    ServerConnection underTest = create();
    underTest.downloadFromExternalUrl(sonarqube.baseUrl() + "/batch/index.txt", toFile);
    assertThat(Files.readString(toFile)).isEqualTo(HELLO_WORLD);

    sonarqube.verify(getRequestedFor(anyUrl())
      .withoutHeader("Authorization"));
  }

  private ServerConnection create() {
    return create(sonarqube.baseUrl());
  }

  private ServerConnection create(String url) {
    return create(url, new HashMap<>());
  }

  private ServerConnection create(String url, Map<String, String> additionalProps) {
    Map<String, String> props = new HashMap<>();
    props.put(ScannerProperties.HOST_URL, url);
    props.put(ScannerProperties.API_BASE_URL, url);
    props.put(InternalProperties.SCANNER_APP, "user");
    props.put(InternalProperties.SCANNER_APP_VERSION, "agent");
    props.putAll(additionalProps);

    ServerConnection connection = new ServerConnection();
    connection.init(props, sonarUserHome);
    return connection;
  }

  private void answer(String msg) {
    answer(msg, 200);
  }

  private void answer(String msg, int responseCode) {
    sonarqube.stubFor(get(anyUrl())
      .willReturn(aResponse().withBody(msg).withStatus(responseCode)));
  }
}
