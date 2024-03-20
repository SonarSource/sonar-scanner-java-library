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

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.scanner.lib.System2;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.cache.HashMismatchException;
import org.sonarsource.scanner.lib.internal.cache.Logger;
import org.sonarsource.scanner.lib.internal.http.ServerConnection;

public class ScannerEngineLauncherFactory {

  static final String API_PATH_ENGINE = "/analysis/engine";
  private final Logger logger;
  private final JavaRunnerFactory javaRunnerFactory;

  public ScannerEngineLauncherFactory(Logger logger, System2 system) {
    this.logger = logger;
    this.javaRunnerFactory = new JavaRunnerFactory(logger, system);
  }

  ScannerEngineLauncherFactory(Logger logger, JavaRunnerFactory javaRunnerFactory) {
    this.logger = logger;
    this.javaRunnerFactory = javaRunnerFactory;
  }

  public ScannerEngineLauncher createLauncher(ServerConnection serverConnection, FileCache fileCache, Map<String, String> properties) {
    JavaRunner javaRunner = javaRunnerFactory.createRunner(serverConnection, fileCache, properties);
    jreSanityCheck(javaRunner);
    File scannerEngine = getScannerEngine(serverConnection, fileCache, true);
    return new ScannerEngineLauncher(javaRunner, scannerEngine, logger);
  }

  private static void jreSanityCheck(JavaRunner javaRunner) {
    javaRunner.execute(Collections.singletonList("--version"), null);
  }

  private File getScannerEngine(ServerConnection serverConnection, FileCache fileCache, boolean retry) {
    try {
      var scannerEngineMetadata = getScannerEngineMetadata(serverConnection);
      return fileCache.get(scannerEngineMetadata.getFilename(), scannerEngineMetadata.getSha256(), "SHA-256",
        new ScannerEngineDownloader(serverConnection, scannerEngineMetadata));
    } catch (HashMismatchException e) {
      if (retry) {
        // A new scanner-engine might have been published between the metadata fetch and the download
        logger.error("Failed to get the scanner-engine, retrying...");
        return getScannerEngine(serverConnection, fileCache, false);
      }
      throw e;
    }
  }

  private static ScannerEngineMetadata getScannerEngineMetadata(ServerConnection serverConnection) {
    try {
      String response = serverConnection.callServerApi(API_PATH_ENGINE);
      return new Gson().fromJson(response, ScannerEngineMetadata.class);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to get scanner-engine metadata", e);
    }
  }

  static class ScannerEngineMetadata extends ResourceMetadata {
    public ScannerEngineMetadata(String filename, String sha256, @Nullable String downloadUrl) {
      super(filename, sha256, downloadUrl);
    }
  }

  static class ScannerEngineDownloader implements FileCache.Downloader {
    private final ServerConnection connection;
    private final ScannerEngineMetadata scannerEngineMetadata;

    ScannerEngineDownloader(ServerConnection connection, ScannerEngineMetadata scannerEngineMetadata) {
      this.connection = connection;
      this.scannerEngineMetadata = scannerEngineMetadata;
    }

    @Override
    public void download(String filename, File toFile) throws IOException {
      if (StringUtils.isNotBlank(scannerEngineMetadata.getDownloadUrl())) {
        connection.downloadFile(scannerEngineMetadata.getDownloadUrl(), toFile.toPath(), false);
      } else {
        connection.downloadServerFile(API_PATH_ENGINE, toFile.toPath());
      }
    }
  }
}
