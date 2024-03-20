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

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import org.sonarsource.scanner.lib.Paths2;
import org.sonarsource.scanner.lib.System2;
import org.sonarsource.scanner.lib.internal.cache.Logger;

import static org.sonarsource.scanner.lib.ScannerProperties.SCANNER_OS;

public class OsResolver {

  private final System2 system;
  private final Paths2 paths;
  private final Logger logger;

  public OsResolver(System2 system, Paths2 paths, Logger logger) {
    this.system = system;
    this.paths = paths;
    this.logger = logger;
  }

  public OperatingSystem getOs() {
    String osName = system.getProperty("os.name");
    if (osName != null) {
      String osNameLowerCase = osName.toLowerCase(Locale.ENGLISH);
      if (osNameLowerCase.contains("mac") || osNameLowerCase.contains("darwin")) {
        return OperatingSystem.MACOS;
      } else if (osNameLowerCase.contains("win")) {
        return OperatingSystem.WINDOWS;
      } else if (osNameLowerCase.contains("linux")) {
        return isAlpine() ? OperatingSystem.ALPINE : OperatingSystem.LINUX;
      }
    }
    throw new IllegalStateException(String.format("Failed to detect OS, use the property '%s' to set it manually.", SCANNER_OS));
  }

  private boolean isAlpine() {
    List<String> content;
    try {
      content = Files.readAllLines(paths.get("/etc/os-release"));
    } catch (IOException e) {
      try {
        content = Files.readAllLines(paths.get("/usr/lib/os-release"));
      } catch (IOException e2) {
        logger.debug("Failed to read the os-release file");
        return false;
      }
    }
    return content.stream().anyMatch(line -> line.toLowerCase(Locale.ENGLISH).contains("alpine"));
  }

  /**
   * Operating systems supported by the JRE provisioning.
   */
  public enum OperatingSystem {
    LINUX, WINDOWS, MACOS, ALPINE
  }
}
