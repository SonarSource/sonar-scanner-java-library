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

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.scanner.lib.internal.cache.CachedFile;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.cache.HashMismatchException;
import org.sonarsource.scanner.lib.internal.http.ScannerHttpClient;
import org.sonarsource.scanner.lib.internal.util.ProcessWrapperFactory;
import org.sonarsource.scanner.lib.internal.util.System2;

public class ScannerEngineLauncherFactory {

  private static final Logger LOG = LoggerFactory.getLogger(ScannerEngineLauncherFactory.class);

  static final String API_PATH_ENGINE = "/analysis/engine";
  private final JavaRunnerFactory javaRunnerFactory;

  public ScannerEngineLauncherFactory(System2 system) {
    this.javaRunnerFactory = new JavaRunnerFactory(system, new ProcessWrapperFactory());
  }

  ScannerEngineLauncherFactory(JavaRunnerFactory javaRunnerFactory) {
    this.javaRunnerFactory = javaRunnerFactory;
  }

  public ScannerEngineLauncher createLauncher(ScannerHttpClient scannerHttpClient, FileCache fileCache, Map<String, String> properties) {
    JavaRunner javaRunner = javaRunnerFactory.createRunner(scannerHttpClient, fileCache, properties);
    jreSanityCheck(javaRunner);
    var scannerEngine = getScannerEngine(scannerHttpClient, fileCache, true);
    return new ScannerEngineLauncher(javaRunner, scannerEngine);
  }

  private static void jreSanityCheck(JavaRunner javaRunner) {
    javaRunner.execute(Collections.singletonList("--version"), null, LOG::debug);
  }

  private static CachedFile getScannerEngine(ScannerHttpClient scannerHttpClient, FileCache fileCache, boolean retry) {
    try {
      var scannerEngineMetadata = getScannerEngineMetadata(scannerHttpClient);
      return fileCache.getOrDownload(scannerEngineMetadata.getFilename(), scannerEngineMetadata.getSha256(), "SHA-256",
        new ScannerEngineDownloader(scannerHttpClient, scannerEngineMetadata));
    } catch (HashMismatchException e) {
      if (retry) {
        // A new scanner-engine might have been published between the metadata fetch and the download
        LOG.warn("Failed to get the scanner-engine, retrying...");
        return getScannerEngine(scannerHttpClient, fileCache, false);
      }
      throw e;
    }
  }

  private static ScannerEngineMetadata getScannerEngineMetadata(ScannerHttpClient scannerHttpClient) {
    try {
      String response = scannerHttpClient.callRestApi(API_PATH_ENGINE);
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
    private final ScannerHttpClient connection;
    private final ScannerEngineMetadata scannerEngineMetadata;

    ScannerEngineDownloader(ScannerHttpClient connection, ScannerEngineMetadata scannerEngineMetadata) {
      this.connection = connection;
      this.scannerEngineMetadata = scannerEngineMetadata;
    }

    @Override
    public void download(String filename, Path toFile) throws IOException {
      if (StringUtils.isNotBlank(scannerEngineMetadata.getDownloadUrl())) {
        connection.downloadFromExternalUrl(scannerEngineMetadata.getDownloadUrl(), toFile);
      } else {
        connection.downloadFromRestApi(API_PATH_ENGINE, toFile);
      }
    }
  }
}
