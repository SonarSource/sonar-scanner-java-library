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
import java.nio.file.Path;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.cache.Logger;
import org.sonarsource.scanner.lib.internal.http.ServerConnection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.scanner.lib.internal.ScannerEngineLauncherFactory.API_PATH_ENGINE;

class ScannerEngineLauncherFactoryTest {

  private final ServerConnection serverConnection = mock(ServerConnection.class);
  private final FileCache fileCache = mock(FileCache.class);
  private final JavaRunnerFactory javaRunnerFactory = mock(JavaRunnerFactory.class);

  @TempDir
  private Path temp;

  @Test
  void createLauncher() throws IOException {
    when(serverConnection.callRestApi(API_PATH_ENGINE)).thenReturn("{\"filename\":\"scanner-engine.jar\",\"sha256\":\"123456\"}");
    when(javaRunnerFactory.createRunner(eq(serverConnection), eq(fileCache), anyMap())).thenReturn(mock(JavaRunner.class));

    ScannerEngineLauncherFactory factory = new ScannerEngineLauncherFactory(mock(Logger.class), javaRunnerFactory);
    factory.createLauncher(serverConnection, fileCache, new HashMap<>());

    verify(fileCache).getOrDownload(eq("scanner-engine.jar"), eq("123456"), eq("SHA-256"),
      any(ScannerEngineLauncherFactory.ScannerEngineDownloader.class));
  }

  @Test
  void scannerEngineDownloader_download() throws IOException {
    String filename = "scanner-engine.jar";
    var output = temp.resolve(filename);
    new ScannerEngineLauncherFactory.ScannerEngineDownloader(serverConnection,
      new ScannerEngineLauncherFactory.ScannerEngineMetadata(filename, "123456", null))
      .download(filename, output);
    verify(serverConnection).downloadFromRestApi(API_PATH_ENGINE, output);
  }

  @Test
  void scannerEngineDownloader_download_withDownloadUrl() throws IOException {
    String filename = "scanner-engine.jar";
    var output = temp.resolve(filename);
    new ScannerEngineLauncherFactory.ScannerEngineDownloader(serverConnection,
      new ScannerEngineLauncherFactory.ScannerEngineMetadata(filename, "123456", "https://localhost/scanner-engine.jar"))
      .download(filename, output);
    verify(serverConnection).downloadFromExternalUrl("https://localhost/scanner-engine.jar", output);
  }
}
