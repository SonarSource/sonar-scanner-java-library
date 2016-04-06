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
package org.sonar.runner.impl;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.sonar.runner.cache.Logger;
import org.sonar.runner.cache.PersistentCache;

import static java.lang.String.format;
import static org.sonar.runner.impl.InternalProperties.RUNNER_APP;
import static org.sonar.runner.impl.InternalProperties.RUNNER_APP_VERSION;

class ServerConnection {

  private final String baseUrlWithoutTrailingSlash;
  private final String userAgent;
  private final OkHttpClient httpClient;

  private final PersistentCache wsCache;
  private final boolean preferCache;
  private final Logger logger;
  private final boolean isCacheEnabled;

  ServerConnection(String baseUrl, String userAgent, boolean preferCache, boolean cacheEnabled, PersistentCache cache, Logger logger) {
    this.isCacheEnabled = cacheEnabled;
    this.logger = logger;
    this.baseUrlWithoutTrailingSlash = removeTrailingSlash(baseUrl);
    this.userAgent = userAgent;
    this.wsCache = cache;
    this.preferCache = preferCache;
    this.httpClient = OkHttpClientFactory.create();
  }

  private static String removeTrailingSlash(String url) {
    return url.replaceAll("(/)+$", "");
  }

  public static ServerConnection create(Properties props, PersistentCache cache, Logger logger, boolean preferCache) {
    String serverUrl = props.getProperty("sonar.host.url");
    String userAgent = format("%s/%s", props.getProperty(RUNNER_APP), props.getProperty(RUNNER_APP_VERSION));
    boolean enableCache = "issues".equalsIgnoreCase(props.getProperty("sonar.analysis.mode"));
    return new ServerConnection(serverUrl, userAgent, preferCache, enableCache, cache, logger);
  }

  boolean isCacheEnabled() {
    return isCacheEnabled;
  }

  /**
   * Download file, without any caching mechanism.
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

    try (OutputStream fileOutput = new FileOutputStream(toFile)) {
      IOUtils.copyLarge(responseBody.byteStream(), fileOutput);
    } catch (IOException | RuntimeException e) {
      FileUtils.deleteQuietly(toFile);
      throw e;
    }
  }

  /**
   * Fetches from cache, if enabled, then request server if not cached. If both attempts fail, it throws the exception linked to the server connection failure.
   */
  public String download(String urlPath) throws IOException {
    if (!urlPath.startsWith("/")) {
      throw new IllegalArgumentException(format("URL path must start with slash: %s", urlPath));
    }
    String url = baseUrlWithoutTrailingSlash + urlPath;
    if (isCacheEnabled && preferCache) {
      return tryCacheFirst(url);
    }
    return tryServerFirst(url);
  }

  /**
   * @throws IOException           if connectivity problem or timeout (network) or IO error (when writing to file)
   * @throws IllegalStateException if HTTP response code is different than 2xx
   */
  private String downloadString(String url, boolean saveCache) throws IOException {
    logger.debug(format("Download: %s", url));
    ResponseBody responseBody = callUrl(url);
    String content = responseBody.string();
    if (saveCache) {
      try {
        wsCache.put(url, content.getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
        logger.warn("Failed to cache WS call: " + e.getMessage());
      }
    }
    return content;
  }

  private String tryCacheFirst(String url) throws IOException {
    String cached = getFromCache(url);
    if (cached != null) {
      return cached;
    }
    try {
      return downloadString(url, preferCache);
    } catch (IOException | RuntimeException e) {
      logger.error(format("Data is not cached and SonarQube server [%s] can not be reached", baseUrlWithoutTrailingSlash));
      throw e;
    }
  }

  private String tryServerFirst(String url) throws IOException {
    try {
      return downloadString(url, isCacheEnabled);
    } catch (IOException e) {
      // connectivity error, response not received
      if (isCacheEnabled) {
        logger.info(format("SonarQube server [%s] can not be reached, trying cache", baseUrlWithoutTrailingSlash));
        String cached = getFromCache(url);
        if (cached != null) {
          return cached;
        }
        logger.error(format("SonarQube server [%s] can not be reached and data is not cached", baseUrlWithoutTrailingSlash));
        throw e;
      }

      logger.error(format("SonarQube server [%s] can not be reached", baseUrlWithoutTrailingSlash));
      throw e;
    }
  }

  private String getFromCache(String fullUrl) {
    try {
      return wsCache.getString(fullUrl);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to access cache", e);
    }
  }

  /**
   * @throws IOException           if connectivity error/timeout (network)
   * @throws IllegalStateException if HTTP code is different than 2xx
   */
  private ResponseBody callUrl(String url) throws IOException, IllegalStateException {
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
  }
}
