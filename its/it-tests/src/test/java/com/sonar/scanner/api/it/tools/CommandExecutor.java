/*
 * SonarQube Scanner API - ITs
 * Copyright (C) 2011-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package com.sonar.scanner.api.it.tools;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteStreamHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.output.TeeOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CommandExecutor {
  private static final Logger LOG = LoggerFactory.getLogger(CommandExecutor.class);
  private static final int TIMEOUT = 20_000;
  private Path file;

  private ByteArrayOutputStream logs;
  private OutputStream in;
  private ExecuteWatchdog watchdog;

  public CommandExecutor(Path file) {
    this.file = file;
  }

  public int execute(String[] args) throws IOException {
    return execute(args, Collections.emptyMap(), null);
  }

  public int execute(String[] args, Map<String, String> env) throws IOException {
    return execute(args, env, null);
  }

  public int execute(String[] args, Map<String, String> env, @Nullable Path workingDir) throws IOException {
    if (!Files.isExecutable(file)) {
      Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>();
      perms.add(PosixFilePermission.OWNER_READ);
      perms.add(PosixFilePermission.OWNER_EXECUTE);
      Files.setPosixFilePermissions(file, perms);
    }

    watchdog = new ExecuteWatchdog(TIMEOUT);
    CommandLine cmd = new CommandLine(file.toFile());
    cmd.addArguments(args, false);
    DefaultExecutor exec = new DefaultExecutor();
    exec.setWatchdog(watchdog);
    exec.setStreamHandler(createStreamHandler());
    exec.setExitValues(null);
    if (workingDir != null) {
      exec.setWorkingDirectory(workingDir.toFile());
    }
    in.close();
    LOG.info("Executing: {}", cmd.toString());
    return exec.execute(cmd, env);
  }

  public String getLogs() {
    return new String(logs.toByteArray(), Charset.defaultCharset());
  }

  private ExecuteStreamHandler createStreamHandler() throws IOException {
    logs = new ByteArrayOutputStream();
    PipedOutputStream outPiped = new PipedOutputStream();
    InputStream inPiped = new PipedInputStream(outPiped);
    in = outPiped;

    TeeOutputStream teeOut = new TeeOutputStream(logs, System.out);
    TeeOutputStream teeErr = new TeeOutputStream(logs, System.err);

    return new PumpStreamHandler(teeOut, teeErr, inPiped);
  }
}
