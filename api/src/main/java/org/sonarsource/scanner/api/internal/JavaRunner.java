/*
 * SonarQube Scanner Commons
 * Copyright (C) 2011-2023 SonarSource SA
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public class JavaRunner {

  private final File javaExecutable;

  public JavaRunner(File javaExecutable) {
    this.javaExecutable = javaExecutable;
  }

  public void execute(List<String> args, @Nullable Map<String, String> envVars) {
    try {
      List<String> command = new ArrayList<>(args);
      command.add(0, javaExecutable.getAbsolutePath());
      ProcessBuilder builder = new ProcessBuilder()
        .inheritIO(); //TODO handle logs
      if (envVars != null) {
        builder.environment().putAll(envVars);
      }
      builder.command(command);
      if (builder.start().waitFor() != 0) {
        throw new IllegalStateException("Error returned by the Java command execution");
      }
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Failed to run the Java command", e);
    }
  }
}
