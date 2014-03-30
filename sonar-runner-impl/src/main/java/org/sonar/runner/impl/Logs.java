/*
 * SonarQube Runner - Implementation
 * Copyright (C) 2011 SonarSource
 * dev@sonar.codehaus.org
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.runner.impl;

import static org.sonar.runner.impl.Logs.Level.DEBUG;
import static org.sonar.runner.impl.Logs.Level.ERROR;
import static org.sonar.runner.impl.Logs.Level.INFO;
import static org.sonar.runner.impl.Logs.Level.WARN;

public class Logs {
  public static enum Level { ALL, TRACE, DEBUG, INFO, WARN, ERROR, OFF }

  private Logs() {
  }

  private static Level logLevel = INFO;

  public static void setLogLevel(Level logLevel) {
    Logs.logLevel = logLevel;
  }

  public static void debug(String message) {
    if (logLevel.ordinal() <= DEBUG.ordinal()) {
      System.out.println("DEBUG: " + message);
    }
  }

  public static void info(String message) {
    if (logLevel.ordinal() <= INFO.ordinal()) {
      System.out.println("INFO: " + message);
    }
  }

  public static void warn(String message) {
    if (logLevel.ordinal() <= WARN.ordinal()) {
      System.out.println("WARN: " + message);
    }
  }

  public static void error(String message) {
    if (logLevel.ordinal() <= ERROR.ordinal()) {
      System.err.println("ERROR: " + message);
    }
  }

  public static void error(String message, Throwable t) {
    if (logLevel.ordinal() <= ERROR.ordinal()) {
      System.err.println("ERROR: " + message);
      if (t != null) {
        t.printStackTrace(System.err);
      }
    }
  }
}
