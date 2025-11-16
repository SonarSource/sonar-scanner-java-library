/*
 * SonarScanner Java Library
 * Copyright (C) 2011-2025 SonarSource Sàrl
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

import java.util.Collection;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.scanner.lib.internal.facade.inprocess.BootstrapIndexDownloader.JarEntry;
import org.sonarsource.scanner.lib.internal.http.ScannerHttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BootstrapIndexDownloaderTest {
  private ScannerHttpClient connection;
  private BootstrapIndexDownloader bootstrapIndexDownloader;

  @BeforeEach
  void setUp() {
    connection = mock(ScannerHttpClient.class);
    bootstrapIndexDownloader = new BootstrapIndexDownloader(connection);
  }

  @Test
  void should_download_jar_files() {
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
  void test_invalid_index() {
    when(connection.callWebApi("/batch/index")).thenReturn("cpd.jar\n");

    assertThatThrownBy(() -> bootstrapIndexDownloader.getIndex())
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Failed to parse the entry in the bootstrap index: cpd.jar");
  }

  @Test
  void test_handles_empty_line_gracefully() {
    when(connection.callWebApi("/batch/index")).thenReturn("\n");

    Collection<JarEntry> index = bootstrapIndexDownloader.getIndex();
    assertThat(index).hasSize(0);
    verify(connection, times(1)).callWebApi("/batch/index");
  }

  @Test
  void test_handles_empty_string_with_exception() {
    when(connection.callWebApi("/batch/index")).thenReturn("");

    assertThatThrownBy(() -> bootstrapIndexDownloader.getIndex())
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Failed to parse the entry in the bootstrap index: ");
  }

  @Test
  void should_fail() {
    when(connection.callWebApi("/batch/index")).thenThrow(new IllegalStateException("io error"));

    assertThatThrownBy(() -> bootstrapIndexDownloader.getIndex())
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Failed to get the bootstrap index from the server");
  }
}
