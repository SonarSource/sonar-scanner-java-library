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

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.sonarsource.scanner.api.internal.cache.Logger;

public class JavaRunner {

  private final File javaExecutable;
  private final Logger logger;

  public JavaRunner(File javaExecutable, Logger logger) {
    this.javaExecutable = javaExecutable;
    this.logger = logger;
  }

  public void execute(List<String> args, @Nullable Map<String, String> envVars) {
    try {
      List<String> command = new ArrayList<>(args);
      command.add(0, javaExecutable.getAbsolutePath());
      //TODO possibility to pass custom vm args
      ProcessBuilder builder = new ProcessBuilder(command);
      if (envVars != null) {
        builder.environment().putAll(envVars);
      }
      Process process = builder.start();
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

  private void tryParse(String stdout) {
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
      case "INFO":
        logger.info(msg);
        break;
      case "DEBUG":
        logger.debug(msg);
        break;
      case "TRACE":
        logger.trace(msg);
        break;
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
      new BufferedReader(new InputStreamReader(inputStream)).lines()
        .forEach(consumer);
    }
  }
}
