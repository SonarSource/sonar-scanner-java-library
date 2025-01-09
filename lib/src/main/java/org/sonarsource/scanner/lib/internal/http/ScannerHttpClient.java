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
package org.sonarsource.scanner.lib.internal.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.annotation.Nullable;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.scanner.lib.internal.util.Utils;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class ScannerHttpClient {

  private static final Logger LOG = LoggerFactory.getLogger(ScannerHttpClient.class);

  private static final String EXCEPTION_MESSAGE_MISSING_SLASH = "URL path must start with slash: %s";


  private OkHttpClient sharedHttpClient;
  private HttpConfig httpConfig;

  public void init(HttpConfig httpConfig) {
    this.httpConfig = httpConfig;
    this.sharedHttpClient = OkHttpClientFactory.create(httpConfig);
  }


  public void downloadFromRestApi(String urlPath, Path toFile) {
    if (!urlPath.startsWith("/")) {
      throw new IllegalArgumentException(format(EXCEPTION_MESSAGE_MISSING_SLASH, urlPath));
    }
    String url = httpConfig.getRestApiBaseUrl() + urlPath;
    downloadFile(url, toFile, true);
  }

  public void downloadFromWebApi(String urlPath, Path toFile) {
    if (!urlPath.startsWith("/")) {
      throw new IllegalArgumentException(format(EXCEPTION_MESSAGE_MISSING_SLASH, urlPath));
    }
    String url = httpConfig.getWebApiBaseUrl() + urlPath;
    downloadFile(url, toFile, true);
  }

  public void downloadFromExternalUrl(String url, Path toFile) {
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
  private void downloadFile(String url, Path toFile, boolean authentication) {
    LOG.debug("Download {} to {}", url, toFile.toAbsolutePath());

    callUrl(url, authentication, "application/octet-stream", responseBody -> {
      try (InputStream in = responseBody.byteStream()) {
        Files.copy(in, toFile, StandardCopyOption.REPLACE_EXISTING);
        return null;
      } catch (IOException | RuntimeException e) {
        Utils.deleteQuietly(toFile);
        throw e;
      }
    });
  }

  public String callRestApi(String urlPath) throws IOException {
    if (!urlPath.startsWith("/")) {
      throw new IllegalArgumentException(format(EXCEPTION_MESSAGE_MISSING_SLASH, urlPath));
    }
    String url = httpConfig.getRestApiBaseUrl() + urlPath;
    return callApi(url);
  }

  public String callWebApi(String urlPath) throws IOException {
    if (!urlPath.startsWith("/")) {
      throw new IllegalArgumentException(format(EXCEPTION_MESSAGE_MISSING_SLASH, urlPath));
    }
    String url = httpConfig.getWebApiBaseUrl() + urlPath;
    return callApi(url);
  }

  /**
   * Call a server API and get the response as a string.
   *
   * @param url the url to call
   * @throws IOException           if connectivity problem or timeout (network)
   * @throws IllegalStateException if HTTP response code is different than 2xx
   */
  private String callApi(String url) {
    return callUrl(url, true, null, ResponseBody::string);
  }

  /**
   * Call the given URL.
   *
   * @param url            the URL to call
   * @param authentication if true, the request will be authenticated with the token
   * @param acceptHeader   the value of the Accept header
   */
  private <G> G callUrl(String url, boolean authentication, @Nullable String acceptHeader, ResponseHandler<G> responseHandler) {
    var httpClient = buildHttpClient(authentication);
    var request = prepareRequest(url, acceptHeader);
    try (Response response = httpClient.newCall(request).execute()) {
      var body = response.body();
      if (!response.isSuccessful()) {
        throw new HttpException(response.request().url().url(), response.code(), response.message(), body != null ? body.string() : null);
      }
      return responseHandler.apply(requireNonNull(body, "Response body is empty"));
    } catch (HttpException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(format("Call to URL [%s] failed: %s", url, e.getMessage()), e);
    }
  }

  private interface ResponseHandler<G> {
    G apply(ResponseBody responseBody) throws IOException;
  }

  private Request prepareRequest(String url, @Nullable String acceptHeader) {
    var requestBuilder = new Request.Builder()
      .get()
      .url(url)
      .addHeader("User-Agent", httpConfig.getUserAgent());
    if (acceptHeader != null) {
      requestBuilder.header("Accept", acceptHeader);
    }
    return requestBuilder.build();
  }

  private OkHttpClient buildHttpClient(boolean authentication) {
    if (authentication) {
      return sharedHttpClient.newBuilder()
        .addNetworkInterceptor(chain -> {
          Request request = chain.request();
          if (httpConfig.getToken() != null) {
            request = request.newBuilder()
              .header("Authorization", "Bearer " + httpConfig.getToken())
              .build();
          } else if (httpConfig.getLogin() != null) {
            request = request.newBuilder()
              .header("Authorization", Credentials.basic(httpConfig.getLogin(), httpConfig.getPassword() != null ? httpConfig.getPassword() : ""))
              .build();
          }
          return chain.proceed(request);
        })
        .build();
    } else {
      return sharedHttpClient;
    }
  }
}
