/*
 * SonarQube Scanner API
 * Copyright (C) 2011-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.sonarsource.scanner.api.internal.cache.Logger;

import static java.lang.String.format;
import static org.sonarsource.scanner.api.internal.InternalProperties.RUNNER_APP;
import static org.sonarsource.scanner.api.internal.InternalProperties.RUNNER_APP_VERSION;

class ServerConnection {

  private final String baseUrlWithoutTrailingSlash;
  private final String userAgent;
  private final OkHttpClient httpClient;

  private final Logger logger;

  ServerConnection(String baseUrl, String userAgent, Logger logger) {
    this.logger = logger;
    this.baseUrlWithoutTrailingSlash = removeTrailingSlash(baseUrl);
    this.userAgent = userAgent;
    this.httpClient = OkHttpClientFactory.create();
  }

  private static String removeTrailingSlash(String url) {
    return url.replaceAll("(/)+$", "");
  }

  public static ServerConnection create(Properties props, Logger logger) {
    String serverUrl = props.getProperty("sonar.host.url");
    String userAgent = format("%s/%s", props.getProperty(RUNNER_APP), props.getProperty(RUNNER_APP_VERSION));
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
  public void downloadFile(String urlPath, File toFile) throws IOException {
    if (!urlPath.startsWith("/")) {
      throw new IllegalArgumentException(format("URL path must start with slash: %s", urlPath));
    }
    String url = baseUrlWithoutTrailingSlash + urlPath;
    logger.debug(format("Download %s to %s", url, toFile.getAbsolutePath()));
    ResponseBody responseBody = callUrl(url);

    try (OutputStream fileOutput = new FileOutputStream(toFile); InputStream byteStream = responseBody.byteStream()) {
      IOUtils.copyLarge(byteStream, fileOutput);
    } catch (IOException | RuntimeException e) {
      FileUtils.deleteQuietly(toFile);
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
    ResponseBody responseBody = callUrl(url);
    return responseBody.string();
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
        throw new IllegalStateException(format("Status returned by url [%s] is not valid: [%s]", response.request().url(), response.code()));
      }
      return response.body();
    } catch (Exception e) {
      logger.error(format("SonarQube server [%s] can not be reached", baseUrlWithoutTrailingSlash));
      throw e;
    }
  }
}
