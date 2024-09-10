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
package org.sonarsource.scanner.lib.internal;

import java.io.IOException;
import java.util.Collection;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.scanner.lib.internal.BootstrapIndexDownloader.JarEntry;
import org.sonarsource.scanner.lib.internal.http.ServerConnection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BootstrapIndexDownloaderTest {
  private ServerConnection connection;
  private BootstrapIndexDownloader bootstrapIndexDownloader;

  @BeforeEach
  void setUp() {
    connection = mock(ServerConnection.class);
    bootstrapIndexDownloader = new BootstrapIndexDownloader(connection);
  }

  @Test
  void should_download_jar_files() throws Exception {
    // index of the files to download
    when(connection.callWebApi("/batch/index")).thenReturn(
      "cpd.jar|CA124VADFSDS\n" +
        "squid.jar|34535FSFSDF\n");

    Collection<JarEntry> index = bootstrapIndexDownloader.getIndex();
    assertThat(index).hasSize(2);
    assertThat(index).extracting("filename", "hash")
      .containsOnly(Tuple.tuple("cpd.jar", "CA124VADFSDS"), Tuple.tuple("squid.jar", "34535FSFSDF"));
    verify(connection, times(1)).callWebApi("/batch/index");
  }

  @Test
  void test_invalid_index() throws Exception {
    when(connection.callWebApi("/batch/index")).thenReturn("cpd.jar\n");

    assertThatThrownBy(() -> bootstrapIndexDownloader.getIndex())
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Fail to parse entry in bootstrap index: cpd.jar");
  }

  @Test
  void test_handles_empty_line_gracefully() throws Exception {
    when(connection.callWebApi("/batch/index")).thenReturn("\n");

    Collection<JarEntry> index = bootstrapIndexDownloader.getIndex();
    assertThat(index).hasSize(0);
    verify(connection, times(1)).callWebApi("/batch/index");
  }

  @Test
  void test_handles_empty_string_with_exception() throws Exception {
    when(connection.callWebApi("/batch/index")).thenReturn("");

    assertThatThrownBy(() -> bootstrapIndexDownloader.getIndex())
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Fail to parse entry in bootstrap index: ");
  }

  @Test
  void should_fail() throws IOException {
    when(connection.callWebApi("/batch/index")).thenThrow(new IOException("io error"));

    assertThatThrownBy(() -> bootstrapIndexDownloader.getIndex())
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Fail to get bootstrap index from server");
  }
}
