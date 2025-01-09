/*
 * SonarScanner Java Library
 * Copyright (C) 2011-2025 SonarSource SA
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.facade.inprocess.BootstrapIndexDownloader.JarEntry;
import org.sonarsource.scanner.lib.internal.facade.inprocess.LegacyScannerEngineDownloader.ScannerFileDownloader;
import org.sonarsource.scanner.lib.internal.http.ScannerHttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LegacyScannerEngineDownloaderTest {
  private final BootstrapIndexDownloader bootstrapIndexDownloader = mock(BootstrapIndexDownloader.class);
  private final ScannerFileDownloader scannerFileDownloader = mock(ScannerFileDownloader.class);
  private final JarExtractor jarExtractor = mock(JarExtractor.class);
  private final FileCache fileCache = mock(FileCache.class);

  @Test
  void should_download_jar_files(@TempDir Path tmpDir) {
    var batchJar = tmpDir.resolve("sonar-scanner-java-library-batch.jar");
    when(jarExtractor.extractToTemp("sonar-scanner-java-library-batch")).thenReturn(batchJar);

    Collection<JarEntry> jars = new ArrayList<>();
    jars.add(new JarEntry("cpd.jar", "CA124VADFSDS"));
    jars.add(new JarEntry("squid.jar", "34535FSFSDF"));

    // index of the files to download
    when(bootstrapIndexDownloader.getIndex()).thenReturn(jars);

    LegacyScannerEngineDownloader legacyScannerEngineDownloader = new LegacyScannerEngineDownloader(scannerFileDownloader, bootstrapIndexDownloader, fileCache, jarExtractor);
    var files = legacyScannerEngineDownloader.getOrDownload();

    assertThat(files).isNotNull();
    verify(bootstrapIndexDownloader).getIndex();
    verify(fileCache, times(1)).getOrDownload(eq("cpd.jar"), eq("CA124VADFSDS"), eq("MD5"), any(FileCache.Downloader.class));
    verify(fileCache, times(1)).getOrDownload(eq("squid.jar"), eq("34535FSFSDF"), eq("MD5"), any(FileCache.Downloader.class));
    verifyNoMoreInteractions(fileCache);
  }

  @Test
  void test_jar_downloader(@TempDir Path tmpDir) throws Exception {
    ScannerHttpClient connection = mock(ScannerHttpClient.class);
    LegacyScannerEngineDownloader.ScannerFileDownloader downloader = new LegacyScannerEngineDownloader.ScannerFileDownloader(connection);
    var toFile = Files.createTempFile(tmpDir, "squid", ".jar");
    downloader.download("squid.jar", toFile);
    verify(connection).downloadFromWebApi("/batch/file?name=squid.jar", toFile);
  }
}
