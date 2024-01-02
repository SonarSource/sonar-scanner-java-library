/*
 * SonarQube Scanner Commons
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
package org.sonarsource.scanner.api.internal;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class JarExtractor {

  public Path extractToTemp(String filenameWithoutSuffix) {
    String filename = filenameWithoutSuffix + ".jar";
    URL url = getClass().getResource("/" + filename);
    try {
      Path copy = Files.createTempFile(filenameWithoutSuffix, ".jar");
      copy.toFile().deleteOnExit();
      try (InputStream in = url.openStream()) {
        Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
      }
      return copy;
    } catch (Exception e) {
      throw new IllegalStateException("Fail to extract " + filename, e);
    }
  }
}
