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

import java.net.URL;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

public class HttpException extends RuntimeException {
  private final int code;

  public HttpException(URL requestUrl, int statusCode, String statusText, @Nullable String body) {
    super(formatMessage(requestUrl, statusCode, statusText, body));
    this.code = statusCode;
  }

  private static String formatMessage(URL requestUrl, int code, String statusText, @Nullable String body) {
    String message = "GET " + requestUrl + " failed with HTTP " + code;
    if (StringUtils.isNotBlank(statusText)) {
      message += " " + statusText;
    }
    if (LoggerFactory.getLogger(HttpException.class).isDebugEnabled() && StringUtils.isNotBlank(body)) {
      message += "\n" + body;
    }
    return message;
  }

  public int getCode() {
    return code;
  }

}
