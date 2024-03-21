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

import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonarsource.scanner.api.internal.ClassloadRules;
import org.sonarsource.scanner.api.internal.InternalProperties;
import org.sonarsource.scanner.api.internal.IsolatedLauncherFactory;
import org.sonarsource.scanner.api.internal.JarDownloader;
import org.sonarsource.scanner.api.internal.JarDownloaderFactory;
import org.sonarsource.scanner.api.internal.JavaRunner;
import org.sonarsource.scanner.api.internal.JreDownloader;
import org.sonarsource.scanner.api.internal.OsArchProvider;
import org.sonarsource.scanner.api.internal.ScannerEngineLauncher;
import org.sonarsource.scanner.api.internal.ServerConnection;
import org.sonarsource.scanner.api.internal.batch.IsolatedLauncher;
import org.sonarsource.scanner.api.internal.cache.FileCache;
import org.sonarsource.scanner.api.internal.cache.FileCacheBuilder;
import org.sonarsource.scanner.api.internal.cache.Logger;

import static org.sonarsource.scanner.api.ScannerProperties.SCANNER_JAVA_EXECUTABLE;
import static org.sonarsource.scanner.api.ScannerProperties.USER_HOME;

/**
 * Entry point to run SonarQube analysis programmatically.
 *
 * @since 2.2
 */
public class EmbeddedScanner {
  private static final String BITBUCKET_CLOUD_ENV_VAR = "BITBUCKET_BUILD_NUMBER";
  private static final String SONAR_HOST_URL_ENV_VAR = "SONAR_HOST_URL";
  private static final String SONARCLOUD_HOST = "https://sonarcloud.io";
  private static final List<String> SENSITIVE_PROPERTIES = Arrays.asList(ScanProperties.TOKEN, ScanProperties.LOGIN);
  private final IsolatedLauncherFactory launcherFactory;
  private IsolatedLauncher launcher;
  private ScannerEngineLauncher scannerEngineLauncher;
  private final LogOutput logOutput;
  private final Map<String, String> globalProperties = new HashMap<>();
  private final Logger logger;
  private final Set<String> classloaderMask = new HashSet<>();
  private final Set<String> classloaderUnmask = new HashSet<>();
  private final System2 system;

  EmbeddedScanner(IsolatedLauncherFactory bl, Logger logger, LogOutput logOutput, System2 system) {
    this.logger = logger;
    this.launcherFactory = bl;
    this.logOutput = logOutput;
    this.classloaderUnmask.add("org.sonarsource.scanner.api.internal.batch.");
    this.system = system;
  }

  public static EmbeddedScanner create(String app, String version, final LogOutput logOutput, System2 system2) {
    Logger logger = new LoggerAdapter(logOutput);
    return new EmbeddedScanner(new IsolatedLauncherFactory(logger), logger, logOutput, system2)
      .setGlobalProperty(InternalProperties.SCANNER_APP, app)
      .setGlobalProperty(InternalProperties.SCANNER_APP_VERSION, version);
  }

  public static EmbeddedScanner create(String app, String version, final LogOutput logOutput) {
    return create(app, version, logOutput, new System2());
  }

  public Map<String, String> globalProperties() {
    return globalProperties;
  }

  public EmbeddedScanner unmask(String fqcnPrefix) {
    checkLauncherDoesntExist();
    classloaderUnmask.add(fqcnPrefix);
    return this;
  }

  public EmbeddedScanner mask(String fqcnPrefix) {
    checkLauncherDoesntExist();
    classloaderMask.add(fqcnPrefix);
    return this;
  }

  /**
   * Declare SonarQube properties needed to download the scanner-engine from the server (sonar.host.url, credentials, proxy, ...).
   */
  public EmbeddedScanner addGlobalProperties(Map<String, String> p) {
    globalProperties.putAll(p);
    return this;
  }

  /**
   * Declare a SonarQube property needed to download the scanner-engine from the server (sonar.host.url, credentials, proxy, ...).
   */
  public EmbeddedScanner setGlobalProperty(String key, String value) {
    globalProperties.put(key, value);
    return this;
  }

  public String globalProperty(String key, @Nullable String defaultValue) {
    return Optional.ofNullable(globalProperties.get(key)).orElse(defaultValue);
  }

  public String app() {
    return globalProperty(InternalProperties.SCANNER_APP, null);
  }

  public String appVersion() {
    return globalProperty(InternalProperties.SCANNER_APP_VERSION, null);
  }

  /**
   * Download scanner-engine JAR and start bootstrapping classloader. After that it is possible to call {@link #serverVersion()}
   */
  public void start() {
    initGlobalDefaultValues();
    doStart();
  }

  public String serverVersion() {
    checkLauncherExists();
    return launcher.getVersion();
  }

  public void execute(Map<String, String> taskProps) {
    Map<String, String> allProps = new HashMap<>();
    allProps.putAll(globalProperties);
    allProps.putAll(taskProps);
    initAnalysisProperties(allProps);

    if (scannerEngineLauncher != null) {
      String token = allProps.get(ScanProperties.TOKEN);
      if (token == null) {
        token = allProps.get(ScanProperties.LOGIN);
      }
      scannerEngineLauncher.execute(buildPropertyFile(allProps), token);
    } else {
      checkLauncherExists();
      try (IsolatedLauncherFactory launcherFactoryToBeClosed = launcherFactory) {
        doExecute(allProps);
      } catch (IOException e) {
        throw new IllegalStateException(e.getMessage(), e);
      }
    }
  }

  private void initGlobalDefaultValues() {
    String sonarHostUrl = system.getEnvironmentVariable(SONAR_HOST_URL_ENV_VAR);
    if (sonarHostUrl != null) {
      setGlobalDefaultValue(ScannerProperties.HOST_URL, sonarHostUrl);
    } else if (system.getEnvironmentVariable(BITBUCKET_CLOUD_ENV_VAR) != null) {
      setGlobalDefaultValue(ScannerProperties.HOST_URL, SONARCLOUD_HOST);
      logger.info("Bitbucket Cloud Pipelines detected, no host variable set. Defaulting to sonarcloud.io.");
    } else {
      setGlobalDefaultValue(ScannerProperties.HOST_URL, "http://localhost:9000");
    }
  }

  private void initAnalysisProperties(Map<String, String> p) {
    initSourceEncoding(p);
    new Dirs(logger).init(p);
  }

  void initSourceEncoding(Map<String, String> p) {
    boolean onProject = Utils.taskRequiresProject(p);
    if (onProject) {
      String sourceEncoding = Optional.ofNullable(p.get(ScanProperties.PROJECT_SOURCE_ENCODING)).orElse("");
      boolean platformDependent = false;
      if ("".equals(sourceEncoding)) {
        sourceEncoding = Charset.defaultCharset().name();
        platformDependent = true;
        p.put(ScanProperties.PROJECT_SOURCE_ENCODING, sourceEncoding);
      }
      logger.info("Default locale: \"" + Locale.getDefault() + "\", source code encoding: \"" + sourceEncoding + "\""
        + (platformDependent ? " (analysis is platform dependent)" : ""));
    }
  }

  private void setGlobalDefaultValue(String key, String value) {
    if (!globalProperties.containsKey(key)) {
      setGlobalProperty(key, value);
    }
  }

  private Path buildPropertyFile(Map<String, String> properties) {
    try {
      Path propertyFile = Files.createTempFile("sonar-scanner", ".json");
      try (JsonWriter writer = new JsonWriter(new FileWriter(propertyFile.toFile(), StandardCharsets.UTF_8))) {

        writer.beginArray();
        for (Map.Entry<String, String> prop : properties.entrySet()) {
          if (!SENSITIVE_PROPERTIES.contains(prop.getKey())) {
            writer.beginObject();
            writer.name("key").value(prop.getKey());
            writer.name("value").value(prop.getValue());
            writer.endObject();
          }
        }
        writer.endArray();

      } catch (IOException e) {
        e.printStackTrace();
      }
      return propertyFile;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create property file", e);
    }
  }

  protected void doStart() {
    ServerConnection serverConnection = ServerConnection.create(globalProperties(), logger);
    FileCache fileCache = new FileCacheBuilder(logger)
      .setUserHome(globalProperties.get(USER_HOME))
      .build();
    JarDownloader jarDownloader = new JarDownloaderFactory(serverConnection, logger, fileCache).create();

    //TODO Maybe call with new method and fallback to old one
    if (!isSimulation() && (isSonarCloud(globalProperties) || serverConnection.getServerVersion().startsWith("10.5"))) {
      JavaRunner javaRunner = setUpJre(serverConnection, fileCache, logger);
      File scannerEngine = jarDownloader.getScannerEngineFiles().get(0);
      scannerEngineLauncher = new ScannerEngineLauncher(javaRunner, scannerEngine);
    } else {
      checkLauncherDoesntExist();
      ClassloadRules rules = new ClassloadRules(classloaderMask, classloaderUnmask);
      launcher = launcherFactory.createLauncher(globalProperties(), rules, jarDownloader);
    }
  }

  private boolean isSimulation() {
    return globalProperties.containsKey(InternalProperties.SCANNER_DUMP_TO_FILE);
  }

  static boolean isSonarCloud(Map<String, String> props) {
    String hostUrl = props.get(ScannerProperties.HOST_URL);
    if (hostUrl != null) {
      return hostUrl.toLowerCase(Locale.ENGLISH).contains("sonarcloud");
    }
    return false;
  }

  private JavaRunner setUpJre(ServerConnection serverConnection, FileCache fileCache, Logger logger) {
    File javaExecutable;
    String javaExecutableProp = globalProperties.get(SCANNER_JAVA_EXECUTABLE);
    if (javaExecutableProp != null) {
      javaExecutable = new File(javaExecutableProp);
      this.logger.info(String.format("JRE auto-provisioning is disabled, the java executable %s will be used.", javaExecutableProp));
    } else {
      OsArchProvider.OsArch osArch = new OsArchProvider(system, this.logger).getOsArch(globalProperties);
      this.logger.info("JRE auto-provisioning: " + osArch);

      javaExecutable = new JreDownloader(serverConnection, fileCache).download(osArch);
    }

    JavaRunner javaRunner = new JavaRunner(javaExecutable, logger);
    jreSanityCheck(javaRunner);
    return javaRunner;
  }

  private static void jreSanityCheck(JavaRunner javaRunner) {
    javaRunner.execute(Collections.singletonList("-version"), null);
  }

  protected void doExecute(Map<String, String> properties) {
    launcher.execute(properties, (formattedMessage, level) -> logOutput.log(formattedMessage, LogOutput.Level.valueOf(level.name())));
  }

  private void checkLauncherExists() {
    if (launcher == null) {
      throw new IllegalStateException("not started");
    }
  }

  private void checkLauncherDoesntExist() {
    if (launcher != null) {
      throw new IllegalStateException("already started");
    }
  }
}
