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
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sonarsource.scanner.lib.System2;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.cache.Logger;
import org.sonarsource.scanner.lib.internal.http.ServerConnection;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.scanner.lib.ScannerProperties.JAVA_EXECUTABLE_PATH;
import static org.sonarsource.scanner.lib.ScannerProperties.SCANNER_ARCH;
import static org.sonarsource.scanner.lib.ScannerProperties.SCANNER_OS;
import static org.sonarsource.scanner.lib.ScannerProperties.SKIP_JRE_PROVISIONING;
import static org.sonarsource.scanner.lib.internal.JavaRunnerFactory.API_PATH_JRE;

@ExtendWith(MockitoExtension.class)
class JavaRunnerFactoryTest {

  @Mock
  private ServerConnection serverConnection;
  @Mock
  private FileCache fileCache;
  @Mock
  private System2 system;
  @Mock
  private Logger logger;

  @InjectMocks
  private JavaRunnerFactory underTest;

  @TempDir
  private File temp;

  @Test
  void createRunner_jreProvisioning() throws IOException {
    File jre = new File(temp, "fake-jre.zip");
    FileUtils.copyFile(new File("src/test/resources/fake-jre.zip"), jre);

    when(serverConnection.callServerApi(matches(API_PATH_JRE + ".*"))).thenReturn(
      IOUtils.toString(requireNonNull(getClass().getResourceAsStream("createRunner_jreProvisioning.json")), StandardCharsets.UTF_8));
    when(fileCache.get(eq("fake-jre.zip"), eq("123456"), eq("SHA-256"), any(JavaRunnerFactory.JreDownloader.class))).thenReturn(jre);

    JavaRunner runner = underTest.createRunner(serverConnection, fileCache, new HashMap<>());

    assertThat(runner.getJavaExecutable()).isNotNull();
    assertThat(new File(runner.getJavaExecutable())).exists();
  }

  @Test
  void createRunner_jreProvisioning_noMatch() throws IOException {
    when(serverConnection.callServerApi(matches(API_PATH_JRE + ".*"))).thenReturn("[]");

    Map<String, String> props = Map.of(SCANNER_OS, "linux", SCANNER_ARCH, "x64");
    assertThatThrownBy(() -> underTest.createRunner(serverConnection, fileCache, props))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("No JRE metadata found for os[linux] and arch[x64]");
  }

  @Test
  void createRunner_jreExeProperty() {
    File javaExe = new File(temp, "bin/java");
    Map<String, String> properties = Map.of(JAVA_EXECUTABLE_PATH, javaExe.getAbsolutePath());

    JavaRunner runner = underTest.createRunner(serverConnection, fileCache, properties);

    assertThat(runner.getJavaExecutable()).isEqualTo(javaExe.getAbsolutePath());
  }

  @Test
  void createRunner_jreProvisioningSkipAndJavaHome() {
    when(system.getEnvironmentVariable("JAVA_HOME")).thenReturn(temp.getAbsolutePath());
    Map<String, String> properties = Map.of(SKIP_JRE_PROVISIONING, "true");

    JavaRunner runner = underTest.createRunner(serverConnection, fileCache, properties);

    assertThat(runner.getJavaExecutable()).isEqualTo(
      new File(temp, "/bin/java" + (SystemUtils.IS_OS_WINDOWS ? ".exe" : "")).getAbsolutePath());
  }

  @Test
  void createRunner_jreProvisioningSkipAndNoJavaHome() {
    Map<String, String> properties = Map.of(SKIP_JRE_PROVISIONING, "true");

    JavaRunner runner = underTest.createRunner(serverConnection, fileCache, properties);

    assertThat(runner.getJavaExecutable()).isEqualTo("java");
  }

  @Test
  void jreDownloader_download() throws IOException {
    String filename = "jre.zip";
    File output = new File(temp, filename);
    new JavaRunnerFactory.JreDownloader(serverConnection,
      new JavaRunnerFactory.JreMetadata(filename, "123456", null, "uuid", "bin/java"))
      .download(filename, output);
    verify(serverConnection).downloadServerFile(API_PATH_JRE + "/uuid", output.toPath());
  }

  @Test
  void jreDownloader_download_withDownloadUrl() throws IOException {
    String filename = "jre.zip";
    File output = new File(temp, filename);
    new JavaRunnerFactory.JreDownloader(serverConnection,
      new JavaRunnerFactory.JreMetadata(filename, "123456", "https://localhost/jre.zip", "uuid", "bin/java"))
      .download(filename, output);
    verify(serverConnection).downloadFile("https://localhost/jre.zip", output.toPath(), false);
  }
}
