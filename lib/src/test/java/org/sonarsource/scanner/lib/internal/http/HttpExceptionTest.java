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

import java.net.MalformedURLException;
import java.net.URL;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpExceptionTest {

  @Test
  void getMessage_shouldIncludeReasonPhraseForKnownStatusCodes() throws MalformedURLException {
    var url = new URL("http://example.com/api");

    assertThat(new HttpException(url, 200, null).getMessage()).isNotBlank().contains("OK");
    assertThat(new HttpException(url, 201, null).getMessage()).isNotBlank().contains("Created");
    assertThat(new HttpException(url, 204, null).getMessage()).isNotBlank().contains("No Content");
    assertThat(new HttpException(url, 301, null).getMessage()).isNotBlank().contains("Moved Permanently");
    assertThat(new HttpException(url, 302, null).getMessage()).isNotBlank().contains("Found");
    assertThat(new HttpException(url, 303, null).getMessage()).isNotBlank().contains("See Other");
    assertThat(new HttpException(url, 304, null).getMessage()).isNotBlank().contains("Not Modified");
    assertThat(new HttpException(url, 307, null).getMessage()).isNotBlank().contains("Temporary Redirect");
    assertThat(new HttpException(url, 308, null).getMessage()).isNotBlank().contains("Permanent Redirect");
    assertThat(new HttpException(url, 400, null).getMessage()).isNotBlank().contains("Bad Request");
    assertThat(new HttpException(url, 401, null).getMessage()).isNotBlank().contains("Unauthorized");
    assertThat(new HttpException(url, 403, null).getMessage()).isNotBlank().contains("Forbidden");
    assertThat(new HttpException(url, 404, null).getMessage()).isNotBlank().contains("Not Found");
    assertThat(new HttpException(url, 405, null).getMessage()).isNotBlank().contains("Method Not Allowed");
    assertThat(new HttpException(url, 407, null).getMessage()).isNotBlank().contains("Proxy Authentication Required");
    assertThat(new HttpException(url, 408, null).getMessage()).isNotBlank().contains("Request Timeout");
    assertThat(new HttpException(url, 409, null).getMessage()).isNotBlank().contains("Conflict");
    assertThat(new HttpException(url, 410, null).getMessage()).isNotBlank().contains("Gone");
    assertThat(new HttpException(url, 500, null).getMessage()).isNotBlank().contains("Internal Server Error");
    assertThat(new HttpException(url, 501, null).getMessage()).isNotBlank().contains("Not Implemented");
    assertThat(new HttpException(url, 502, null).getMessage()).isNotBlank().contains("Bad Gateway");
    assertThat(new HttpException(url, 503, null).getMessage()).isNotBlank().contains("Service Unavailable");
    assertThat(new HttpException(url, 504, null).getMessage()).isNotBlank().contains("Gateway Timeout");
  }

  @Test
  void getMessage_shouldNotIncludeReasonPhraseForUnknownStatusCode() throws MalformedURLException {
    var url = new URL("http://example.com/api");

    assertThat(new HttpException(url, 999, null).getMessage()).isNotBlank();
  }

  @Test
  void getCode_shouldReturnStatusCode() throws MalformedURLException {
    var url = new URL("http://example.com/api");

    assertThat(new HttpException(url, 404, null).getCode()).isEqualTo(404);
  }
}
