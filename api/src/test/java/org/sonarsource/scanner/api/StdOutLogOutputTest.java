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
