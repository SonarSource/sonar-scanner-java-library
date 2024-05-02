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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.scanner.lib.ScannerProperties;
import org.sonarsource.scanner.lib.internal.cache.CachedFile;
import org.sonarsource.scanner.lib.internal.cache.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ScannerEngineLauncherTest {

  @TempDir
  private Path temp;

  private final JavaRunner javaRunner = mock(JavaRunner.class);

  @Test
  void execute() {
    var scannerEngine = temp.resolve("scanner-engine.jar");
    ScannerEngineLauncher launcher = new ScannerEngineLauncher(javaRunner, new CachedFile(scannerEngine, true), mock(Logger.class));

    Map<String, String> properties = Map.of(ScannerProperties.SCANNER_JAVA_OPTS, "-Xmx4g",
      ScannerProperties.HOST_URL, "http://localhost:9000");
    launcher.execute(properties);

    verify(javaRunner).execute(
      List.of("-Xmx4g", "-jar", scannerEngine.toAbsolutePath().toString()),
      "{\"scannerProperties\":" + new Gson().toJson(properties) + "}");
  }
}
