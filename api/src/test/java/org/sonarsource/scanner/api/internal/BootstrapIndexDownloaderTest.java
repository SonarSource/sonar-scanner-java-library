/*
 * SonarQube Scanner API
 * Copyright (C) 2011-2018 SonarSource SA
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
package org.sonarsource.scanner.api.internal;

import java.io.IOException;
import java.util.Collection;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonarsource.scanner.api.internal.BootstrapIndexDownloader.JarEntry;
import org.sonarsource.scanner.api.internal.cache.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BootstrapIndexDownloaderTest {
  private ServerConnection connection;
  private BootstrapIndexDownloader bootstrapIndexDownloader;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void setUp() {
    connection = mock(ServerConnection.class);
    bootstrapIndexDownloader = new BootstrapIndexDownloader(connection, mock(Logger.class));
  }

  @Test
  public void should_download_jar_files() throws Exception {
    // index of the files to download
    when(connection.downloadString("/batch/index")).thenReturn(
      "cpd.jar|CA124VADFSDS\n" +
        "squid.jar|34535FSFSDF\n");

    Collection<JarEntry> index = bootstrapIndexDownloader.getIndex();
    assertThat(index).hasSize(2);
    assertThat(index).extracting("filename", "hash")
      .containsOnly(Tuple.tuple("cpd.jar", "CA124VADFSDS"), Tuple.tuple("squid.jar", "34535FSFSDF"));
    verify(connection, times(1)).downloadString("/batch/index");
  }

  @Test
  public void test_invalid_index() throws Exception {
    when(connection.downloadString("/batch/index")).thenReturn("cpd.jar\n");

    exception.expect(IllegalStateException.class);
    exception.expectMessage("Fail to parse entry in bootstrap index: cpd.jar");

    bootstrapIndexDownloader.getIndex();
  }

  @Test
  public void should_fail() throws IOException {
    when(connection.downloadString("/batch/index")).thenThrow(new IOException("io error"));

    exception.expect(IllegalStateException.class);
    exception.expectMessage("Fail to get bootstrap index from server");
    bootstrapIndexDownloader.getIndex();
  }
}
