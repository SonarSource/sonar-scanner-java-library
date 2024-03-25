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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.sonarsource.scanner.api.System2;
import org.sonarsource.scanner.api.internal.cache.Logger;

import static org.sonarsource.scanner.api.ScannerProperties.SCANNER_ARCH;
import static org.sonarsource.scanner.api.ScannerProperties.SCANNER_OS;

public class OsArchProvider {

  private final System2 system;
  private final Logger logger;

  public OsArchProvider(System2 system, Logger logger) {
    this.system = system;
    this.logger = logger;
  }

  public OsArch getOsArch(Map<String, String> properties) {
    OperatingSystem os = Optional.ofNullable(properties.get(SCANNER_OS)).map(OperatingSystem::from).orElse(detectOs());
    String arch = Optional.ofNullable(properties.get(SCANNER_ARCH)).orElse(system.getProperty("os.arch"));
    if (arch == null || os == null) {
      throw new IllegalStateException(String.format("Failed to detect OS and architecture, use the properties '%s' and '%s' to set them" +
                                                    " manually.", SCANNER_OS, SCANNER_ARCH));
    }
    return new OsArch(os, arch);
  }

  private OperatingSystem detectOs() {
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
    return null;
  }

  private boolean isAlpine() {
    List<String> content;
    try {
      content = Files.readAllLines(Paths.get("/etc/os-release"));
    } catch (IOException e) {
      try {
        content = Files.readAllLines(Paths.get("/usr/lib/os-release"));
      } catch (IOException e2) {
        logger.debug("Failed to read the os-release file");
        return false;
      }
    }
    return content.stream().anyMatch(line -> line.contains("alpine"));
  }

  /**
   * Operating systems supported by the JRE auto-provisioning.
   */
  public enum OperatingSystem {
    LINUX, WINDOWS, MACOS, ALPINE;

    public static OperatingSystem from(String value) {
      return Arrays.stream(OperatingSystem.values())
        .filter(softwareQualityRest -> softwareQualityRest.name().equalsIgnoreCase(value))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported OperatingSystem: " + value));
    }
  }

  public static class OsArch {
    private final OperatingSystem os;
    private final String arch;

    public OsArch(OperatingSystem os, String arch) {
      this.os = os;
      this.arch = arch;
    }

    public OperatingSystem getOs() {
      return os;
    }

    public String getArch() {
      return arch;
    }

    @Override
    public String toString() {
      return "os[" + os.name().toLowerCase(Locale.ENGLISH) + "], arch[" + arch + "]";
    }
  }
}
