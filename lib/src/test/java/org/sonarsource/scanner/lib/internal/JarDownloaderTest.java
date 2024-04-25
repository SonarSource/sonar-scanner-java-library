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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarsource.scanner.lib.internal.BootstrapIndexDownloader.JarEntry;
import org.sonarsource.scanner.lib.internal.JarDownloader.ScannerFileDownloader;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.cache.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class JarDownloaderTest {
  @Mock
  private BootstrapIndexDownloader bootstrapIndexDownloader;
  @Mock
  private ScannerFileDownloader scannerFileDownloader;
  @Mock
  private JarExtractor jarExtractor;
  @Mock
  private FileCache fileCache;

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void should_download_jar_files() throws Exception {
    File batchJar = temp.newFile("sonar-scanner-java-library-batch.jar");
    when(jarExtractor.extractToTemp("sonar-scanner-java-library-batch")).thenReturn(batchJar.toPath());

    Collection<JarEntry> jars = new ArrayList<>();
    jars.add(new JarEntry("cpd.jar", "CA124VADFSDS"));
    jars.add(new JarEntry("squid.jar", "34535FSFSDF"));

    // index of the files to download
    when(bootstrapIndexDownloader.getIndex()).thenReturn(jars);

    JarDownloader jarDownloader = new JarDownloader(scannerFileDownloader, bootstrapIndexDownloader, fileCache, jarExtractor, mock(Logger.class));
    List<File> files = jarDownloader.download();

    assertThat(files).isNotNull();
    verify(bootstrapIndexDownloader).getIndex();
    verify(fileCache, times(1)).get(eq("cpd.jar"), eq("CA124VADFSDS"), any(FileCache.Downloader.class));
    verify(fileCache, times(1)).get(eq("squid.jar"), eq("34535FSFSDF"), any(FileCache.Downloader.class));
    verifyNoMoreInteractions(fileCache);
  }

  @Test
  public void test_jar_downloader() throws Exception {
    ServerConnection connection = mock(ServerConnection.class);
    JarDownloader.ScannerFileDownloader downloader = new JarDownloader.ScannerFileDownloader(connection);
    File toFile = temp.newFile();
    downloader.download("squid.jar", toFile);
    verify(connection).downloadFile("/batch/file?name=squid.jar", toFile.toPath());
  }
}
