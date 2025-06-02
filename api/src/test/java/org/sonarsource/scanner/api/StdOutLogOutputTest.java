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

import org.junit.Test;
import org.sonarsource.scanner.api.StdOutLogOutput;
import org.sonarsource.scanner.api.LogOutput.Level;
import java.io.PrintStream;

import static org.mockito.Mockito.verify;

import static org.mockito.Mockito.mock;

public class StdOutLogOutputTest {
  private PrintStream stdOut = mock(PrintStream.class);
  private StdOutLogOutput logOutput = new StdOutLogOutput(stdOut);

  @Test
  public void test() {
    logOutput.log("msg", Level.INFO);
    verify(stdOut).println("INFO: msg");
  }
}
