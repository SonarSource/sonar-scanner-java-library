/*
 * SonarScanner Java Library
 * Copyright (C) 2011-2025 SonarSource Sàrl
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.scanner.lib.internal.util.Utils;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class ScannerHttpClient {

  private static final Logger LOG = LoggerFactory.getLogger(ScannerHttpClient.class);
  private static final String EXCEPTION_MESSAGE_MISSING_SLASH = "URL path must start with slash: %s";

  private HttpClient sharedHttpClient;
  private HttpConfig httpConfig;

  public void init(HttpConfig httpConfig) {
    this.httpConfig = httpConfig;
    this.sharedHttpClient = HttpClientFactory.create(httpConfig);
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
   * @throws IllegalStateException if HTTP response code is different than 2xx
   */
  private void downloadFile(String url, Path toFile, boolean authentication) {
    LOG.debug("Download {} to {}", url, toFile.toAbsolutePath());

    callUrl(url, authentication, "application/octet-stream", response -> {
      try (InputStream in = response.body()) {
        Files.copy(in, toFile, StandardCopyOption.REPLACE_EXISTING);
        return null;
      } catch (IOException | RuntimeException e) {
        Utils.deleteQuietly(toFile);
        throw e;
      }
    });
  }

  public String callRestApi(String urlPath) {
    if (!urlPath.startsWith("/")) {
      throw new IllegalArgumentException(format(EXCEPTION_MESSAGE_MISSING_SLASH, urlPath));
    }
    String url = httpConfig.getRestApiBaseUrl() + urlPath;
    return callApi(url);
  }

  public String callWebApi(String urlPath) {
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
   * @throws IllegalStateException if HTTP response code is different than 2xx
   */
  private String callApi(String url) {
    return callUrl(url, true, null, response -> {
      try (InputStream in = response.body()) {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
    });
  }

  /**
   * Call the given URL.
   *
   * @param url            the URL to call
   * @param authentication if true, the request will be authenticated with the token
   * @param acceptHeader   the value of the Accept header
   */
  private <G> G callUrl(String url, boolean authentication, @Nullable String acceptHeader, ResponseHandler<G> responseHandler) {
    return callUrlWithRedirects(url, authentication, acceptHeader, responseHandler, 0);
  }

  private <G> G callUrlWithRedirects(String url, boolean authentication, @Nullable String acceptHeader, ResponseHandler<G> responseHandler, int redirectCount) {
    return callUrlWithRedirectsAndProxyAuth(url, authentication, acceptHeader, responseHandler, redirectCount, false);
  }

  private <G> G callUrlWithRedirectsAndProxyAuth(String url, boolean authentication, @Nullable String acceptHeader, ResponseHandler<G> responseHandler,
    int redirectCount, boolean proxyAuthAttempted) {
    if (redirectCount > 10) {
      throw new IllegalStateException("Too many redirects (>10) for URL: " + url);
    }

    var request = prepareRequest(url, acceptHeader, authentication, proxyAuthAttempted);

    HttpResponse<InputStream> response = null;
    Instant start = Instant.now();
    try {
      LOG.debug("--> {} {}", request.method(), request.uri());
      response = sharedHttpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

      if (response.statusCode() == 407 && !proxyAuthAttempted && httpConfig.getProxyUser() != null) {
        LOG.debug("Received 407 Proxy Authentication Required, retrying with Proxy-Authorization header");
        return callUrlWithRedirectsAndProxyAuth(url, authentication, acceptHeader, responseHandler, redirectCount, true);
      }

      if (isRedirect(response.statusCode())) {
        var locationHeader = response.headers().firstValue("Location");
        if (locationHeader.isPresent()) {
          String redirectUrl = locationHeader.get();
          if (!redirectUrl.startsWith("http")) {
            URI originalUri = URI.create(url);
            redirectUrl = originalUri.getScheme() + "://" + originalUri.getAuthority() + redirectUrl;
          }
          return callUrlWithRedirectsAndProxyAuth(redirectUrl, authentication, acceptHeader, responseHandler, redirectCount + 1, proxyAuthAttempted);
        }
      }

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        String errorBody = tryReadBody(response);
        throw new HttpException(URI.create(url).toURL(), response.statusCode(), errorBody);
      }

      return responseHandler.apply(requireNonNull(response, "Response is empty"));
    } catch (HttpException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(format("Call to URL [%s] failed: %s", url, e.getMessage()), e);
    } finally {
      if (response != null) {
        LOG.debug("<-- {} {} ({}ms)", response.statusCode(), response.uri(), Duration.between(start, Instant.now()).toMillis());
      }
    }
  }

  @CheckForNull
  private static String tryReadBody(HttpResponse<InputStream> response) {
    String errorBody = null;
    try (InputStream body = response.body()) {
      errorBody = new String(body.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      // Ignore
    }
    return errorBody;
  }

  private static boolean isRedirect(int statusCode) {
    return statusCode == 301 || statusCode == 302 || statusCode == 303 ||
           statusCode == 307 || statusCode == 308;
  }

  private interface ResponseHandler<G> {
    G apply(HttpResponse<InputStream> response) throws IOException;
  }

  private HttpRequest prepareRequest(String url, @Nullable String acceptHeader, boolean authentication, boolean addProxyAuth) {
    var timeout = httpConfig.getResponseTimeout().isZero() ? httpConfig.getSocketTimeout() : httpConfig.getResponseTimeout();

    var requestBuilder = HttpRequest.newBuilder()
      .GET()
      .uri(URI.create(url))
      .timeout(timeout)
      .header("User-Agent", httpConfig.getUserAgent());

    if (acceptHeader != null) {
      requestBuilder.header("Accept", acceptHeader);
    }

    if (authentication) {
      if (httpConfig.getToken() != null) {
        requestBuilder.header("Authorization", "Bearer " + httpConfig.getToken());
      } else if (httpConfig.getLogin() != null) {
        String credentials = httpConfig.getLogin() + ":" + (httpConfig.getPassword() != null ? httpConfig.getPassword() : "");
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        requestBuilder.header("Authorization", "Basic " + encodedCredentials);
      }
    }

    if (addProxyAuth && httpConfig.getProxyUser() != null) {
      String proxyCredentials = httpConfig.getProxyUser() + ":" + (httpConfig.getProxyPassword() != null ? httpConfig.getProxyPassword() : "");
      String encodedProxyCredentials = Base64.getEncoder().encodeToString(proxyCredentials.getBytes(StandardCharsets.UTF_8));
      requestBuilder.header("Proxy-Authorization", "Basic " + encodedProxyCredentials);
    }

    return requestBuilder.build();
  }
}
