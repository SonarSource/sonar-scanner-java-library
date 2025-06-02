/*
 * SonarQube Scanner API
 * Copyright (C) 2011-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.scanner.api;


import java.io.PrintWriter;
import java.io.StringWriter;
import org.sonarsource.scanner.api.internal.cache.Logger;

class LoggerAdapter implements Logger {
  private LogOutput logOutput;

  LoggerAdapter(LogOutput logOutput) {
    this.logOutput = logOutput;
  }

  @Override
  public void warn(String msg) {
    logOutput.log(msg, LogOutput.Level.WARN);
  }

  @Override
  public void info(String msg) {
    logOutput.log(msg, LogOutput.Level.INFO);
  }

  @Override
  public void error(String msg, Throwable t) {
    StringWriter errors = new StringWriter();
    t.printStackTrace(new PrintWriter(errors));
    logOutput.log(msg + "\n" + errors.toString(), LogOutput.Level.ERROR);
  }

  @Override
  public void error(String msg) {
    logOutput.log(msg, LogOutput.Level.ERROR);
  }

  @Override
  public void debug(String msg) {
    logOutput.log(msg, LogOutput.Level.DEBUG);
  }
}
