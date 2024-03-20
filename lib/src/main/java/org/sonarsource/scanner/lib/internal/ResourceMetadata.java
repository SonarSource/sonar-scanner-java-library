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
package org.sonarsource.scanner.lib.internal;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nullable;

public abstract class ResourceMetadata {
  @SerializedName("filename")
  private final String filename;
  @SerializedName("sha256")
  private final String sha256;
  @SerializedName("downloadUrl")
  private final String downloadUrl;

  ResourceMetadata(String filename, String sha256, @Nullable String downloadUrl) {
    this.filename = filename;
    this.sha256 = sha256;
    this.downloadUrl = downloadUrl;
  }

  public String getFilename() {
    return filename;
  }

  public String getSha256() {
    return sha256;
  }

  public String getDownloadUrl() {
    return downloadUrl;
  }
}
