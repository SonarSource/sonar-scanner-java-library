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
package org.sonarsource.scanner.lib;

import java.util.Map;
import javax.annotation.Nullable;
import org.sonarsource.scanner.lib.internal.ScannerEngineLauncher;

class NewScannerEngineFacade extends ScannerEngineFacade {
  private final ScannerEngineLauncher launcher;

  NewScannerEngineFacade(Map<String, String> bootstrapProperties, ScannerEngineLauncher launcher,
    boolean isSonarCloud, @Nullable String serverVersion) {
    super(bootstrapProperties, isSonarCloud, serverVersion, launcher.isEngineCacheHit(), launcher.getJreCacheHit());
    this.launcher = launcher;
  }

  @Override
  boolean doAnalyze(Map<String, String> allProps) {
    return launcher.execute(allProps);
  }

  @Override
  public void close() throws Exception {
    // nothing to do
  }
}
