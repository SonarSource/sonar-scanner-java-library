/*
 * SonarQube Scanner API
 * Copyright (C) 2011-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.scanner.api.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.sonarsource.scanner.api.Utils;
import org.sonarsource.scanner.api.internal.cache.Logger;

import static java.lang.String.format;
import static org.sonarsource.scanner.api.internal.InternalProperties.SCANNER_APP;
import static org.sonarsource.scanner.api.internal.InternalProperties.SCANNER_APP_VERSION;

class ServerConnection {

  private final String baseUrlWithoutTrailingSlash;
  private final String userAgent;
  private final OkHttpClient httpClient;

  private final Logger logger;

  ServerConnection(String baseUrl, String userAgent, Logger logger) {
    this.logger = logger;
    this.baseUrlWithoutTrailingSlash = removeTrailingSlash(baseUrl);
    this.userAgent = userAgent;
    this.httpClient = OkHttpClientFactory.create(logger);
  }

  private static String removeTrailingSlash(String url) {
    return url.replaceAll("(/)+$", "");
  }

  public static ServerConnection create(Map<String, String> props, Logger logger) {
    String serverUrl = props.get("sonar.host.url");
    String userAgent = format("%s/%s", props.get(SCANNER_APP), props.get(SCANNER_APP_VERSION));
    return new ServerConnection(serverUrl, userAgent, logger);
  }

  /**
   * Download file
   *
   * @param urlPath path starting with slash, for instance {@code "/batch/index"}
   * @param toFile  the target file
   * @throws IOException           if connectivity problem or timeout (network) or IO error (when writing to file)
   * @throws IllegalStateException if HTTP response code is different than 2xx
   */
  public void downloadFile(String urlPath, Path toFile) throws IOException {
    if (!urlPath.startsWith("/")) {
      throw new IllegalArgumentException(format("URL path must start with slash: %s", urlPath));
    }
    String url = baseUrlWithoutTrailingSlash + urlPath;
    logger.debug(format("Download %s to %s", url, toFile.toAbsolutePath().toString()));

    try (ResponseBody responseBody = callUrl(url);
         InputStream in = responseBody.byteStream()) {
      Files.copy(in, toFile, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException | RuntimeException e) {
      Utils.deleteQuietly(toFile);
      throw e;
    }
  }

  /**
   * @throws IOException           if connectivity problem or timeout (network) or IO error (when writing to file)
   * @throws IllegalStateException if HTTP response code is different than 2xx
   */
  public String downloadString(String urlPath) throws IOException {
    if (!urlPath.startsWith("/")) {
      throw new IllegalArgumentException(format("URL path must start with slash: %s", urlPath));
    }
    String url = baseUrlWithoutTrailingSlash + urlPath;
    logger.debug(format("Download: %s", url));
    try (ResponseBody responseBody = callUrl(url)) {
      return responseBody.string();
    }
  }

  /**
   * @throws IOException           if connectivity error/timeout (network)
   * @throws IllegalStateException if HTTP code is different than 2xx
   */
  private ResponseBody callUrl(String url) throws IOException {
    try {
      Request request = new Request.Builder()
        .url(url)
        .addHeader("User-Agent", userAgent)
        .get()
        .build();
      Response response = httpClient.newCall(request).execute();
      if (!response.isSuccessful()) {
        response.close();
        throw new IllegalStateException(format("Status returned by url [%s] is not valid: [%s]", response.request().url(), response.code()));
      }
      return response.body();
    } catch (Exception e) {
      logger.error(format("%s server [%s] can not be reached", url.toLowerCase(Locale.ENGLISH).contains("sonarcloud") ? "SonarCloud" : "SonarQube", baseUrlWithoutTrailingSlash));
      throw e;
    }
  }
}
