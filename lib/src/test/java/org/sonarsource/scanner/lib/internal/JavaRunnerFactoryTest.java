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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.scanner.lib.System2;
import org.sonarsource.scanner.lib.internal.cache.CachedFile;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.cache.Logger;
import org.sonarsource.scanner.lib.internal.http.ServerConnection;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.scanner.lib.ScannerProperties.JAVA_EXECUTABLE_PATH;
import static org.sonarsource.scanner.lib.ScannerProperties.SCANNER_ARCH;
import static org.sonarsource.scanner.lib.ScannerProperties.SCANNER_OS;
import static org.sonarsource.scanner.lib.ScannerProperties.SKIP_JRE_PROVISIONING;
import static org.sonarsource.scanner.lib.internal.JavaRunnerFactory.API_PATH_JRE;

class JavaRunnerFactoryTest {

  private final ServerConnection serverConnection = mock(ServerConnection.class);
  private final FileCache fileCache = mock(FileCache.class);
  private final System2 system = mock(System2.class);
  private final Logger logger = mock(Logger.class);

  private final JavaRunnerFactory underTest = new JavaRunnerFactory(logger, system);

  @TempDir
  private Path temp;

  @Test
  void createRunner_jreProvisioning() throws IOException {
    var jre = temp.resolve("fake-jre.zip");
    FileUtils.copyFile(new File("src/test/resources/fake-jre.zip"), jre.toFile());

    when(serverConnection.callRestApi(matches(API_PATH_JRE + ".*"))).thenReturn(
      IOUtils.toString(requireNonNull(getClass().getResourceAsStream("createRunner_jreProvisioning.json")), StandardCharsets.UTF_8));
    when(fileCache.getOrDownload(eq("fake-jre.zip"), eq("123456"), eq("SHA-256"), any(JavaRunnerFactory.JreDownloader.class))).thenReturn(new CachedFile(jre, true));

    JavaRunner runner = underTest.createRunner(serverConnection, fileCache, new HashMap<>());

    assertThat(runner.getJavaExecutable()).isNotNull();
    assertThat(new File(runner.getJavaExecutable())).exists();
  }

  @Test
  void createRunner_jreProvisioning_noMatch() throws IOException {
    when(serverConnection.callRestApi(matches(API_PATH_JRE + ".*"))).thenReturn("[]");

    Map<String, String> props = Map.of(SCANNER_OS, "linux", SCANNER_ARCH, "x64");
    assertThatThrownBy(() -> underTest.createRunner(serverConnection, fileCache, props))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("No JRE metadata found for os[linux] and arch[x64]");
  }

  @Test
  void createRunner_jreExeProperty() {
    var javaExe = temp.resolve("bin/java");
    Map<String, String> properties = Map.of(JAVA_EXECUTABLE_PATH, javaExe.toAbsolutePath().toString());

    JavaRunner runner = underTest.createRunner(serverConnection, fileCache, properties);

    assertThat(runner.getJavaExecutable()).isEqualTo(javaExe.toAbsolutePath().toString());
    assertThat(runner.getJreCacheHit()).isEqualTo(JreCacheHit.DISABLED);
  }

  @Test
  void createRunner_jreProvisioningSkipAndJavaHome() throws IOException {
    var javaHome = temp.toAbsolutePath();
    Files.createDirectories(javaHome.resolve("bin"));
    Files.createFile(javaHome.resolve("bin/java" + (SystemUtils.IS_OS_WINDOWS ? ".exe" : "")));

    when(system.getEnvironmentVariable("JAVA_HOME")).thenReturn(javaHome.toString());
    Map<String, String> properties = Map.of(SKIP_JRE_PROVISIONING, "true");

    JavaRunner runner = underTest.createRunner(serverConnection, fileCache, properties);

    assertThat(runner.getJavaExecutable()).isEqualTo(
      temp.resolve("bin/java" + (SystemUtils.IS_OS_WINDOWS ? ".exe" : "")).toAbsolutePath().toString());
    assertThat(runner.getJreCacheHit()).isEqualTo(JreCacheHit.DISABLED);
  }

  @Test
  void createRunner_jreProvisioningSkipAndNoJavaHome() {
    Map<String, String> properties = Map.of(SKIP_JRE_PROVISIONING, "true");

    JavaRunner runner = underTest.createRunner(serverConnection, fileCache, properties);

    assertThat(runner.getJavaExecutable()).isEqualTo("java");
    assertThat(runner.getJreCacheHit()).isEqualTo(JreCacheHit.DISABLED);
  }

  @Test
  void jreDownloader_download() throws IOException {
    String filename = "jre.zip";
    var output = temp.resolve(filename);
    new JavaRunnerFactory.JreDownloader(serverConnection,
      new JavaRunnerFactory.JreMetadata(filename, "123456", null, "uuid", "bin/java"))
      .download(filename, output);
    verify(serverConnection).downloadFromRestApi(API_PATH_JRE + "/uuid", output);
  }

  @Test
  void jreDownloader_download_withDownloadUrl() throws IOException {
    String filename = "jre.zip";
    var output = temp.resolve(filename);
    new JavaRunnerFactory.JreDownloader(serverConnection,
      new JavaRunnerFactory.JreMetadata(filename, "123456", "https://localhost/jre.zip", "uuid", "bin/java"))
      .download(filename, output);
    verify(serverConnection).downloadFromExternalUrl("https://localhost/jre.zip", output);
  }
}
