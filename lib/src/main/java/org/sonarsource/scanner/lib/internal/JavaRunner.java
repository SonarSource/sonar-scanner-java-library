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

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonarsource.scanner.lib.internal.cache.Logger;

public class JavaRunner {

  private final File javaExecutable;
  private final Logger logger;

  public JavaRunner(@Nullable File javaExecutable, Logger logger) {
    this.javaExecutable = javaExecutable;
    this.logger = logger;
  }

  public void execute(List<String> args, @Nullable String input) {
    try {
      List<String> command = new ArrayList<>(args);
      command.add(0, getJavaExecutable());
      Process process = new ProcessBuilder(command).start();
      if (input != null) {
        try (OutputStream stdin = process.getOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin, StandardCharsets.UTF_8))) {
          writer.write(input);
        }
      }
      new StreamGobbler(process.getInputStream(), this::tryParse).start();
      new StreamGobbler(process.getErrorStream(), stderr -> logger.error("[stderr] " + stderr)).start();
      if (process.waitFor() != 0) {
        throw new IllegalStateException("Error returned by the Java command execution");
      }
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Failed to run the Java command", e);
    }
  }

  String getJavaExecutable() {
    if (javaExecutable != null) {
      return javaExecutable.getAbsolutePath();
    }
    // Will try to use the java executable in the PATH
    return "java";
  }

  void tryParse(String stdout) {
    try {
      var log = new Gson().fromJson(stdout, Log.class);
      StringBuilder sb = new StringBuilder();
      if (log.message != null) {
        sb.append(log.message);
      }
      if (log.message != null && log.throwable != null) {
        sb.append("\n");
      }
      if (log.throwable != null) {
        sb.append(log.throwable);
      }
      log(log.level, sb.toString());
    } catch (Exception e) {
      logger.info("[stdout] " + stdout);
    }
  }

  private void log(String level, String msg) {
    switch (level) {
      case "ERROR":
        logger.error(msg);
        break;
      case "WARN":
        logger.warn(msg);
        break;
      case "DEBUG":
        logger.debug(msg);
        break;
      case "TRACE":
        logger.trace(msg);
        break;
      case "INFO":
      default:
        logger.info(msg);
    }
  }

  private static class Log {
    @SerializedName("level")
    private String level;
    @SerializedName("formattedMessage")
    private String message;
    @SerializedName("throwable")
    private String throwable;
  }

  private static class StreamGobbler extends Thread {
    private final InputStream inputStream;
    private final Consumer<String> consumer;

    public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
      this.inputStream = inputStream;
      this.consumer = consumer;
    }

    @Override
    public void run() {
      new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines()
        .forEach(consumer);
    }
  }
}
