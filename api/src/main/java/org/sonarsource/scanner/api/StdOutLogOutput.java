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

import java.io.PrintStream;

public class StdOutLogOutput implements LogOutput {
  private PrintStream stdOut;

  public StdOutLogOutput() {
    this(System.out);
  }

  StdOutLogOutput(PrintStream stdOut) {
    this.stdOut = stdOut;
  }

  @Override
  public void log(String formattedMessage, org.sonarsource.scanner.api.LogOutput.Level level) {
    stdOut.println(level.name() + ": " + formattedMessage);
  }
}
