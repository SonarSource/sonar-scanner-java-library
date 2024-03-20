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
package org.sonarsource.scanner.lib.internal.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import javax.annotation.Nullable;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.sonarsource.scanner.lib.ScannerProperties;
import org.sonarsource.scanner.lib.Utils;
import org.sonarsource.scanner.lib.internal.InternalProperties;
import org.sonarsource.scanner.lib.internal.cache.Logger;

import static java.lang.String.format;

public class ServerConnection {

  private String baseUrlWithoutTrailingSlash;
  private String userAgent;
  @Nullable
  private String credentials;
  private OkHttpClient httpClient;
  private final Logger logger;

  public ServerConnection(Logger logger) {
    this.logger = logger;
  }

  public void init(Map<String, String> bootstrapProperties, Path sonarUserHome) {
    baseUrlWithoutTrailingSlash = removeTrailingSlash(bootstrapProperties.get(ScannerProperties.HOST_URL));
    userAgent = format("%s/%s", bootstrapProperties.get(InternalProperties.SCANNER_APP),
      bootstrapProperties.get(InternalProperties.SCANNER_APP_VERSION));
    String token = bootstrapProperties.get(ScannerProperties.SONAR_TOKEN);
    String login = bootstrapProperties.getOrDefault(ScannerProperties.SONAR_LOGIN, token);
    if (login != null) {
      credentials = Credentials.basic(login, bootstrapProperties.getOrDefault(ScannerProperties.SONAR_PASSWORD, ""));
    }
    httpClient = OkHttpClientFactory.create(bootstrapProperties, sonarUserHome);
  }

  private static String removeTrailingSlash(String url) {
    return url.replaceAll("(/)+$", "");
  }

  /**
   * Download file from the server.
   *
   * @param urlPath path starting with slash, for instance {@code "/batch/index"}
   * @param toFile  the target file
   * @throws IOException           if connectivity problem or timeout (network) or IO error (when writing to file)
   * @throws IllegalStateException if HTTP response code is different than 2xx
   */
  public void downloadServerFile(String urlPath, Path toFile) throws IOException {
    if (!urlPath.startsWith("/")) {
      throw new IllegalArgumentException(format("URL path must start with slash: %s", urlPath));
    }
    String url = baseUrlWithoutTrailingSlash + urlPath;
    downloadFile(url, toFile, true);
  }

  /**
   * Download file from the given URL.
   *
   * @param url            the URL of the file to download
   * @param toFile         the target file
   * @param authentication if true, the request will be authenticated with the token
   * @throws IOException           if connectivity problem or timeout (network) or IO error (when writing to file)
   * @throws IllegalStateException if HTTP response code is different than 2xx
   */
  public void downloadFile(String url, Path toFile, boolean authentication) throws IOException {
    if (httpClient == null) {
      throw new IllegalStateException("ServerConnection must be initialized");
    }
    logger.debug(format("Download %s to %s", url, toFile.toAbsolutePath()));

    try (ResponseBody responseBody = callUrl(url, authentication, "application/octet-stream");
         InputStream in = responseBody.byteStream()) {
      Files.copy(in, toFile, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException | RuntimeException e) {
      Utils.deleteQuietly(toFile);
      throw e;
    }
  }

  /**
   * Call a server API and get the response as a string.
   *
   * @param urlPath path starting with slash, for instance {@code "/batch/index"}
   * @throws IOException           if connectivity problem or timeout (network)
   * @throws IllegalStateException if HTTP response code is different than 2xx
   */
  public String callServerApi(String urlPath) throws IOException {
    if (httpClient == null) {
      throw new IllegalStateException("ServerConnection must be initialized");
    }
    if (!urlPath.startsWith("/")) {
      throw new IllegalArgumentException(format("URL path must start with slash: %s", urlPath));
    }
    String url = baseUrlWithoutTrailingSlash + urlPath;
    logger.debug(format("Call server API: %s", url));
    try (ResponseBody responseBody = callUrl(url, true, null)) {
      return responseBody.string();
    }
  }

  /**
   * Call the given URL.
   *
   * @param url            the URL to call
   * @param authentication if true, the request will be authenticated with the token
   * @param acceptHeader   the value of the Accept header
   * @throws IOException           if connectivity error/timeout (network)
   * @throws IllegalStateException if HTTP code is different than 2xx
   */
  private ResponseBody callUrl(String url, boolean authentication, @Nullable String acceptHeader) throws IOException {
    try {
      var requestBuilder = new Request.Builder()
        .get()
        .url(url)
        .addHeader("User-Agent", userAgent);
      if (authentication && credentials != null) {
        requestBuilder.header("Authorization", credentials);
      }
      if (acceptHeader != null) {
        requestBuilder.header("Accept", acceptHeader);
      }
      Request request = requestBuilder.build();
      Response response = httpClient.newCall(request).execute();
      if (!response.isSuccessful()) {
        response.close();
        throw new IllegalStateException(format("Error status returned by url [%s]: %s", response.request().url(), response.code()));
      }
      return response.body();
    } catch (Exception e) {
      logger.error(format("Call to URL [%s] failed", url));
      throw e;
    }
  }
}
