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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.scanner.lib.ScannerProperties;
import org.sonarsource.scanner.lib.Utils;
import org.sonarsource.scanner.lib.internal.InternalProperties;

import static java.lang.String.format;

public class ScannerHttpClient {

  private static final Logger LOG = LoggerFactory.getLogger(ScannerHttpClient.class);

  private static final String EXCEPTION_MESSAGE_MISSING_SLASH = "URL path must start with slash: %s";

  private String webApiBaseUrl;
  private String restApiBaseUrl;
  private String userAgent;
  @Nullable
  private String token;
  @Nullable
  private String login;
  @Nullable
  private String password;
  private OkHttpClient httpClient;

  public void init(Map<String, String> bootstrapProperties, Path sonarUserHome) {
    webApiBaseUrl = removeTrailingSlash(bootstrapProperties.get(ScannerProperties.HOST_URL));
    restApiBaseUrl = removeTrailingSlash(bootstrapProperties.get(ScannerProperties.API_BASE_URL));
    userAgent = format("%s/%s", bootstrapProperties.get(InternalProperties.SCANNER_APP),
      bootstrapProperties.get(InternalProperties.SCANNER_APP_VERSION));
    this.token = bootstrapProperties.get(ScannerProperties.SONAR_TOKEN);
    this.login = bootstrapProperties.get(ScannerProperties.SONAR_LOGIN);
    this.password = bootstrapProperties.get(ScannerProperties.SONAR_PASSWORD);
    var httpConfig = new HttpConfig(bootstrapProperties, sonarUserHome);
    httpClient = OkHttpClientFactory.create(httpConfig);
  }

  public static String removeTrailingSlash(String url) {
    return url.replaceAll("(/)+$", "");
  }

  public void downloadFromRestApi(String urlPath, Path toFile) throws IOException {
    if (!urlPath.startsWith("/")) {
      throw new IllegalArgumentException(format(EXCEPTION_MESSAGE_MISSING_SLASH, urlPath));
    }
    String url = restApiBaseUrl + urlPath;
    downloadFile(url, toFile, true);
  }

  public void downloadFromWebApi(String urlPath, Path toFile) throws IOException {
    if (!urlPath.startsWith("/")) {
      throw new IllegalArgumentException(format(EXCEPTION_MESSAGE_MISSING_SLASH, urlPath));
    }
    String url = webApiBaseUrl + urlPath;
    downloadFile(url, toFile, true);
  }

  public void downloadFromExternalUrl(String url, Path toFile) throws IOException {
    downloadFile(url, toFile, false);
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
  private void downloadFile(String url, Path toFile, boolean authentication) throws IOException {
    if (httpClient == null) {
      throw new IllegalStateException("ServerConnection must be initialized");
    }
    LOG.debug("Download {} to {}", url, toFile.toAbsolutePath());

    try (ResponseBody responseBody = callUrl(url, authentication, "application/octet-stream");
         InputStream in = responseBody.byteStream()) {
      Files.copy(in, toFile, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException | RuntimeException e) {
      Utils.deleteQuietly(toFile);
      throw e;
    }
  }

  public String callRestApi(String urlPath) throws IOException {
    if (!urlPath.startsWith("/")) {
      throw new IllegalArgumentException(format(EXCEPTION_MESSAGE_MISSING_SLASH, urlPath));
    }
    String url = restApiBaseUrl + urlPath;
    return callApi(url);
  }

  public String callWebApi(String urlPath) throws IOException {
    if (!urlPath.startsWith("/")) {
      throw new IllegalArgumentException(format(EXCEPTION_MESSAGE_MISSING_SLASH, urlPath));
    }
    String url = webApiBaseUrl + urlPath;
    return callApi(url);
  }

  /**
   * Call a server API and get the response as a string.
   *
   * @param url the url to call
   * @throws IOException           if connectivity problem or timeout (network)
   * @throws IllegalStateException if HTTP response code is different than 2xx
   */
  private String callApi(String url) throws IOException {
    if (httpClient == null) {
      throw new IllegalStateException("ServerConnection must be initialized");
    }
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
   * @throws IllegalStateException if HTTP code is different than 2xx
   */
  private ResponseBody callUrl(String url, boolean authentication, @Nullable String acceptHeader) {
    var requestBuilder = new Request.Builder()
      .get()
      .url(url)
      .addHeader("User-Agent", userAgent);
    if (authentication) {
      if (token != null) {
        requestBuilder.header("Authorization", "Bearer " + token);
      } else if (login != null) {
        requestBuilder.header("Authorization", Credentials.basic(login, password != null ? password : ""));
      }
    }
    if (acceptHeader != null) {
      requestBuilder.header("Accept", acceptHeader);
    }
    Request request = requestBuilder.build();
    Response response;
    try {
      response = httpClient.newCall(request).execute();
    } catch (Exception e) {
      throw new IllegalStateException(format("Call to URL [%s] failed", url), e);
    }
    if (!response.isSuccessful()) {
      response.close();
      throw new IllegalStateException(format("Error status returned by url [%s]: %s", response.request().url(), response.code()));
    }
    return response.body();
  }
}
