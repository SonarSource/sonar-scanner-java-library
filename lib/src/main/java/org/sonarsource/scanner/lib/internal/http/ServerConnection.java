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
import java.util.Locale;
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
import org.sonarsource.scanner.lib.internal.SonarUserHome;
import org.sonarsource.scanner.lib.internal.cache.Logger;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ServerConnection {

  private final String baseUrlWithoutTrailingSlash;
  private final String userAgent;
  private final OkHttpClient httpClient;

  private final String token;
  private final Logger logger;

  ServerConnection(String baseUrl, String userAgent, @Nullable String token, Logger logger, Map<String, String> bootstrapProperties, SonarUserHome sonarUserHome) {
    this.token = token;
    this.logger = logger;
    this.baseUrlWithoutTrailingSlash = removeTrailingSlash(baseUrl);
    this.userAgent = userAgent;
    this.httpClient = OkHttpClientFactory.create(logger, bootstrapProperties, sonarUserHome);
  }

  private static String removeTrailingSlash(String url) {
    return url.replaceAll("(/)+$", "");
  }

  public static ServerConnection create(Map<String, String> bootstrapProperties, Logger logger, SonarUserHome sonarUserHome) {
    String serverUrl = bootstrapProperties.get("sonar.host.url");
    String userAgent = format("%s/%s", bootstrapProperties.get(InternalProperties.SCANNER_APP), bootstrapProperties.get(InternalProperties.SCANNER_APP_VERSION));
    String token = bootstrapProperties.get(ScannerProperties.SONAR_TOKEN);
    return new ServerConnection(serverUrl, userAgent, token, logger, bootstrapProperties, sonarUserHome);
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
      var requestBuilder = new Request.Builder()
        .get()
        .url(url)
        .addHeader("User-Agent", userAgent);
      if (token != null) {
        requestBuilder.header("Authorization", Credentials.basic(token, "", UTF_8));
      }
      Request request = requestBuilder.build();
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
