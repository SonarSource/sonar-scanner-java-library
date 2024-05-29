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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.Level;
import org.sonarsource.scanner.lib.ScannerProperties;
import org.sonarsource.scanner.lib.internal.cache.CachedFile;
import testutils.LogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ScannerEngineLauncherTest {

  @RegisterExtension
  LogTester logTester = new LogTester().setLevel(Level.TRACE);

  @TempDir
  private Path temp;

  private final JavaRunner javaRunner = mock(JavaRunner.class);

  @Test
  void execute() {
    var scannerEngine = temp.resolve("scanner-engine.jar");

    ScannerEngineLauncher launcher = new ScannerEngineLauncher(javaRunner, new CachedFile(scannerEngine, true));

    Map<String, String> properties = Map.of(
      ScannerProperties.SCANNER_JAVA_OPTS, "-Xmx4g -Xms1g",
      ScannerProperties.HOST_URL, "http://localhost:9000");
    launcher.execute(properties);

    verify(javaRunner).execute(
      eq(List.of("-Xmx4g", "-Xms1g", "-jar", scannerEngine.toAbsolutePath().toString())),
      eq("{\"scannerProperties\":[{\"key\":\"sonar.host.url\",\"value\":\"http://localhost:9000\"},{\"key\":\"sonar.scanner.javaOpts\",\"value\":\"-Xmx4g -Xms1g\"}]}"),
      any());
  }

  @Test
  void replace_null_values_by_empty_in_json_and_ignore_null_key() {
    var scannerEngine = temp.resolve("scanner-engine.jar");

    ScannerEngineLauncher launcher = new ScannerEngineLauncher(javaRunner, new CachedFile(scannerEngine, true));

    Map<String, String> properties = new HashMap<>();
    properties.put("sonar.myProp", null);
    properties.put(null, "someValue");
    launcher.execute(properties);

    verify(javaRunner).execute(
      eq(List.of("-jar", scannerEngine.toAbsolutePath().toString())),
      eq("{\"scannerProperties\":[{\"key\":\"sonar.myProp\",\"value\":\"\"}]}"),
      any());
  }

  @Test
  void tryParse_shouldParseLogMessages() {
    ScannerEngineLauncher.tryParse("{\n" +
      "    \"level\": \"ERROR\",\n" +
      "    \"message\": \"Some error message\",\n" +
      "    \"stacktrace\": \"exception\"\n" +
      "}");
    ScannerEngineLauncher.tryParse("{\"level\": \"WARN\", \"message\": \"Some warn message\"}");
    ScannerEngineLauncher.tryParse("{\"level\": \"DEBUG\", \"message\": \"Some debug message\"}");
    ScannerEngineLauncher.tryParse("{\"level\": \"TRACE\", \"message\": \"Some trace message\"}");
    ScannerEngineLauncher.tryParse("{\"level\": \"INFO\", \"message\": \"Some info message\"}");
    ScannerEngineLauncher.tryParse("{\"level\": \"UNKNOWN-LEVEL\", \"message\": \"Some unknown level message\"}");

    assertThat(logTester.logs(Level.ERROR)).containsOnly("Some error message\nexception");
    assertThat(logTester.logs(Level.WARN)).containsOnly("Some warn message");
    assertThat(logTester.logs(Level.DEBUG)).containsOnly("Some debug message");
    assertThat(logTester.logs(Level.TRACE)).containsOnly("Some trace message");
    assertThat(logTester.logs(Level.INFO)).containsOnly("Some info message", "Some unknown level message");
  }

  @Test
  void tryParse_whenCannotParse_shouldLogInfo() {
    ScannerEngineLauncher.tryParse("INFO: test");
    assertThat(logTester.logs(Level.INFO)).containsOnly("[stdout] INFO: test");
  }
}
