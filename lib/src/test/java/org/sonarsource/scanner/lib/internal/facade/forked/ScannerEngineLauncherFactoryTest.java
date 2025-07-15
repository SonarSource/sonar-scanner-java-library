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
package org.sonarsource.scanner.lib.internal.facade.forked;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.Level;
import org.sonarsource.scanner.lib.internal.MessageException;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.http.ScannerHttpClient;
import testutils.LogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.scanner.lib.internal.facade.forked.ScannerEngineLauncherFactory.API_PATH_ENGINE;

class ScannerEngineLauncherFactoryTest {

  private final ScannerHttpClient scannerHttpClient = mock(ScannerHttpClient.class);
  private final FileCache fileCache = mock(FileCache.class);
  private final JavaRunnerFactory javaRunnerFactory = mock(JavaRunnerFactory.class);

  @RegisterExtension
  private final LogTester logTester = new LogTester();

  @TempDir
  private Path temp;

  @Test
  void createLauncher_use_engine_provisioning_by_default() {
    when(scannerHttpClient.callRestApi(API_PATH_ENGINE)).thenReturn("{\"filename\":\"scanner-engine.jar\",\"sha256\":\"123456\"}");
    when(javaRunnerFactory.createRunner(eq(scannerHttpClient), eq(fileCache), anyMap())).thenReturn(mock(JavaRunner.class));

    ScannerEngineLauncherFactory factory = new ScannerEngineLauncherFactory(javaRunnerFactory);
    factory.createLauncher(scannerHttpClient, fileCache, Map.of());

    verify(fileCache).getOrDownload(eq("scanner-engine.jar"), eq("123456"), eq("SHA-256"),
      any(ScannerEngineLauncherFactory.ScannerEngineDownloader.class));
  }

  @Test
  void createLauncher_use_local_scanner_engine_if_specified(@TempDir Path temp) throws IOException {
    Path jarPath = temp.resolve("my-engine.jar");
    Files.createFile(jarPath);
    when(javaRunnerFactory.createRunner(eq(scannerHttpClient), eq(fileCache), anyMap())).thenReturn(mock(JavaRunner.class));

    ScannerEngineLauncherFactory factory = new ScannerEngineLauncherFactory(javaRunnerFactory);
    factory.createLauncher(scannerHttpClient, fileCache, Map.of("sonar.scanner.engineJarPath", jarPath.toString()));

    verifyNoInteractions(fileCache);
    assertThat(logTester.logs(Level.INFO)).contains("Using the configured Scanner Engine '" + jarPath + "'");
  }

  @Test
  void createLauncher_fails_if_local_scanner_engine_doesnt_exist() {
    when(javaRunnerFactory.createRunner(eq(scannerHttpClient), eq(fileCache), anyMap())).thenReturn(mock(JavaRunner.class));

    ScannerEngineLauncherFactory factory = new ScannerEngineLauncherFactory(javaRunnerFactory);
    Map<String, String> properties = Map.of("sonar.scanner.engineJarPath", "dontexist.jar");
    assertThatThrownBy(() -> factory.createLauncher(scannerHttpClient, fileCache, properties))
      .isInstanceOf(MessageException.class)
      .hasMessage("Scanner Engine jar path 'dontexist.jar' does not exist. Please check property 'sonar.scanner.engineJarPath'.");
  }

  @Test
  void createLauncher_fail_to_download_engine_metadata() {
    when(scannerHttpClient.callRestApi(API_PATH_ENGINE)).thenThrow(new IllegalStateException("Some error"));
    when(javaRunnerFactory.createRunner(eq(scannerHttpClient), eq(fileCache), anyMap())).thenReturn(mock(JavaRunner.class));

    ScannerEngineLauncherFactory factory = new ScannerEngineLauncherFactory(javaRunnerFactory);
    Map<String, String> properties = Map.of();

    assertThatThrownBy(() -> factory.createLauncher(scannerHttpClient, fileCache, properties))
      .isInstanceOf(MessageException.class)
      .hasMessage("Failed to get the scanner-engine metadata: Some error");

    verifyNoInteractions(fileCache);
  }

  @Test
  void scannerEngineDownloader_download() throws IOException {
    String filename = "scanner-engine.jar";
    var output = temp.resolve(filename);
    new ScannerEngineLauncherFactory.ScannerEngineDownloader(scannerHttpClient,
      new ScannerEngineLauncherFactory.ScannerEngineMetadata(filename, "123456", null))
        .download(filename, output);
    verify(scannerHttpClient).downloadFromRestApi(API_PATH_ENGINE, output);
  }

  @Test
  void scannerEngineDownloader_download_withDownloadUrl() throws IOException {
    String filename = "scanner-engine.jar";
    var output = temp.resolve(filename);
    new ScannerEngineLauncherFactory.ScannerEngineDownloader(scannerHttpClient,
      new ScannerEngineLauncherFactory.ScannerEngineMetadata(filename, "123456", "https://localhost/scanner-engine.jar"))
        .download(filename, output);
    verify(scannerHttpClient).downloadFromExternalUrl("https://localhost/scanner-engine.jar", output);
  }
}
