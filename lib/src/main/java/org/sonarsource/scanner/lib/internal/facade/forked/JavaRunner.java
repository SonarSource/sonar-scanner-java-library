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
package org.sonarsource.scanner.lib.internal.facade.forked;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaRunner {
  private static final Logger LOG = LoggerFactory.getLogger(JavaRunner.class);

  static final String JRE_VERSION_ERROR = "The version of the custom JRE provided to the SonarScanner using the 'sonar.scanner.javaExePath' parameter is incompatible " +
    "with your SonarQube target. You may need to upgrade the version of Java that executes the scanner. " +
    "Refer to https://docs.sonarsource.com/sonarqube-community-build/analyzing-source-code/scanners/scanner-environment/general-requirements/ for more details.";

  private final Path javaExecutable;
  private final JreCacheHit jreCacheHit;

  public JavaRunner(Path javaExecutable, JreCacheHit jreCacheHit) {
    this.javaExecutable = javaExecutable;
    this.jreCacheHit = jreCacheHit;
  }

  public JreCacheHit getJreCacheHit() {
    return jreCacheHit;
  }

  public boolean execute(List<String> args, @Nullable String input, Consumer<String> stdOutConsummer) {
    try {
      List<String> command = new ArrayList<>(args);
      command.add(0, javaExecutable.toString());
      if (LOG.isDebugEnabled()) {
        LOG.debug("Executing: {}", String.join(" ", command));
      }
      Process process = new ProcessBuilder(command).start();
      var stdoutConsummer = new StreamGobbler(process.getInputStream(), stdOutConsummer);
      var stdErrConsummer = new StreamGobbler(process.getErrorStream(), stderr -> LOG.error("[stderr] {}", stderr));
      stdErrConsummer.start();
      stdoutConsummer.start();
      if (input != null && process.isAlive()) {
        try (var stdin = process.getOutputStream(); var osw = new OutputStreamWriter(stdin, StandardCharsets.UTF_8)) {
          osw.write(input);
        }
      }
      var exitCode = process.waitFor();
      stdoutConsummer.join();
      stdErrConsummer.join();

      if (stdErrConsummer.hasUnsupportedClassVersionError()) {
        LOG.error(JRE_VERSION_ERROR);
      }

      if (exitCode != 0) {
        LOG.debug("Java command exited with code {}", process.exitValue());
        return false;
      }
      return true;
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Failed to run the Java command", e);
    }
  }

  public Path getJavaExecutable() {
    return javaExecutable;
  }

  private static class StreamGobbler extends Thread {
    private final InputStream inputStream;
    private final Consumer<String> consumer;
    private boolean unsupportedClassVersionError = false;

    public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
      this.inputStream = inputStream;
      this.consumer = consumer;
    }

    @Override
    public void run() {
      new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines()
        .forEach(line -> {
          if (line.contains("UnsupportedClassVersionError")) {
            unsupportedClassVersionError = true;
          }
          consumer.accept(line);
        });
    }

    public boolean hasUnsupportedClassVersionError() {
      return unsupportedClassVersionError;
    }

  }
}
