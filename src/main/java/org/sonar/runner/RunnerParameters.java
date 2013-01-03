/*
 * Sonar Runner
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
package org.sonar.runner;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Julien Henry
 * @since 2.1
 */
public final class RunnerParameters {

  @Parameter(names = {"--debug", "-X"}, description = "Debug mode")
  private boolean debugMode = false;

  @Parameter(names = {"--help", "-h"}, help = true)
  private boolean help;

  @DynamicParameter(names = {"-D", "--define"}, description = "Other properties to pass to Sonar")
  private Map<String, String> properties = new HashMap<String, String>();

  public static RunnerParameters parseArguments(String[] args) {
    RunnerParameters params = new RunnerParameters();
    JCommander jcom = new JCommander(params, args);

    if (params.help) {
      jcom.usage("sonar-runner");
      System.exit(0);
    }

    return params;
  }

  public boolean isDebugMode() {
    return debugMode;
  }

  public Map<String, String> getProperties() {
    return properties;
  }
}
