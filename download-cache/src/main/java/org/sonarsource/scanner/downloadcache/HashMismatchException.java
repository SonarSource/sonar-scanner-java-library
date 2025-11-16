/*
 * SonarScanner Download Cache Utility
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
package org.sonarsource.scanner.downloadcache;

import java.nio.file.Path;

public class HashMismatchException extends Exception {

  private final String expectedFileHash;
  private final String downloadedFileHash;
  private final Path downloadedFile;

  public HashMismatchException(String expectedFileHash, String downloadedFileHash, Path downloadedFile) {
    super("Hash mismatch for file " + downloadedFile + ". Expected hash: " + expectedFileHash + ", actual hash: " + downloadedFileHash);
    this.expectedFileHash = expectedFileHash;
    this.downloadedFileHash = downloadedFileHash;
    this.downloadedFile = downloadedFile;
  }

  public String getExpectedFileHash() {
    return expectedFileHash;
  }

  public Path getDownloadedFile() {
    return downloadedFile;
  }

  public String getDownloadedFileHash() {
    return downloadedFileHash;
  }
}
