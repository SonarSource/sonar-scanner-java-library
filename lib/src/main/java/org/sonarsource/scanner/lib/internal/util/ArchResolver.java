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
package org.sonarsource.scanner.lib.internal.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchResolver {

  private static final Logger LOG = LoggerFactory.getLogger(ArchResolver.class);

  private final System2 system;
  private final ProcessWrapperFactory processWrapperFactory;
  private final boolean useUname;

  public ArchResolver() {
    this(new System2(), new ProcessWrapperFactory(), SystemUtils.IS_OS_UNIX);
  }

  ArchResolver(System2 system, ProcessWrapperFactory processWrapperFactory, boolean useUname) {
    this.system = system;
    this.processWrapperFactory = processWrapperFactory;
    this.useUname = useUname;
  }

  /**
   * We don't want to only rely on the system property 'os.arch' to detect the architecture on macOS, since it returns the target architecture of
   * the current JVM, which may be different from the architecture of the OS. For example, a 32-bit JVM can run on a 64-bit OS.
   */
  public String getCpuArch() {
    Optional<String> archFromUname = Optional.empty();
    if (useUname) {
      archFromUname = tryGetArchUsingUname();
    }
    return archFromUname.orElseGet(() -> system.getProperty("os.arch"));
  }

  private Optional<String> tryGetArchUsingUname() {
    try {
      ProcessWrapperFactory.ProcessWrapper process = processWrapperFactory.create("uname", "-m");

      String arch;
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        arch = reader.lines().findFirst().orElseThrow(() -> new IllegalStateException("No output from 'uname -m'"));
        LOG.debug("uname -m returned '{}'", arch);
      }

      int exit = process.waitFor();
      if (exit != 0) {
        LOG.debug("Command exited with code: {}", exit);
        return Optional.empty();
      }

      return Optional.of(arch);
    } catch (Exception e) {
      LOG.debug("Failed to get the architecture using 'uname -m'", e);
      return Optional.empty();
    }
  }

}
