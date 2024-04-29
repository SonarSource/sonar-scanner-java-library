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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonarsource.scanner.lib.internal.ClassloadRules;
import org.sonarsource.scanner.lib.internal.InternalProperties;
import org.sonarsource.scanner.lib.internal.IsolatedLauncherFactory;
import org.sonarsource.scanner.lib.internal.batch.IsolatedLauncher;
import org.sonarsource.scanner.lib.internal.cache.Logger;

/**
 * Entry point to run a Sonar analysis programmatically.
 *
 * @since 2.2
 */
public class EmbeddedScanner {
  private static final String SONAR_HOST_URL_ENV_VAR = "SONAR_HOST_URL";
  private static final String SONARCLOUD_HOST = "https://sonarcloud.io";
  private final IsolatedLauncherFactory launcherFactory;
  private IsolatedLauncher launcher;
  private final LogOutput logOutput;
  private final Map<String, String> bootstrapProperties = new HashMap<>();
  private final Logger logger;
  private final System2 system;

  EmbeddedScanner(IsolatedLauncherFactory bl, Logger logger, LogOutput logOutput, System2 system) {
    this.logger = logger;
    this.launcherFactory = bl;
    this.logOutput = logOutput;
    this.system = system;
  }

  public static EmbeddedScanner create(String app, String version, final LogOutput logOutput, System2 system2) {
    Logger logger = new LoggerAdapter(logOutput);
    return new EmbeddedScanner(new IsolatedLauncherFactory(logger), logger, logOutput, system2)
      .setBootstrapProperty(InternalProperties.SCANNER_APP, app)
      .setBootstrapProperty(InternalProperties.SCANNER_APP_VERSION, version);
  }

  public static EmbeddedScanner create(String app, String version, final LogOutput logOutput) {
    return create(app, version, logOutput, new System2());
  }

  public Map<String, String> getBootstrapProperties() {
    return bootstrapProperties;
  }

  /**
   * Declare technical properties needed to bootstrap (sonar.host.url, credentials, proxy, ...).
   */
  public EmbeddedScanner addBootstrapProperties(Map<String, String> p) {
    bootstrapProperties.putAll(p);
    return this;
  }

  /**
   * Declare a technical property needed to bootstrap (sonar.host.url, credentials, proxy, ...).
   */
  public EmbeddedScanner setBootstrapProperty(String key, String value) {
    bootstrapProperties.put(key, value);
    return this;
  }

  public String getBootstrapProperty(String key, @Nullable String defaultValue) {
    return bootstrapProperties.getOrDefault(key, defaultValue);
  }

  public String app() {
    return getBootstrapProperty(InternalProperties.SCANNER_APP, null);
  }

  public String appVersion() {
    return getBootstrapProperty(InternalProperties.SCANNER_APP_VERSION, null);
  }

  /**
   * Bootstrap the scanner-engine. After that it is possible to call {@link #serverVersion()}
   */
  public void start() {
    initBootstrapDefaultValues();
    doStart();
  }

  public String serverVersion() {
    checkLauncherExists();
    return launcher.getVersion();
  }

  public void execute(Map<String, String> analysisProps) {
    checkLauncherExists();
    try (IsolatedLauncherFactory launcherFactoryToBeClosed = launcherFactory) {
      Map<String, String> allProps = new HashMap<>();
      allProps.putAll(bootstrapProperties);
      allProps.putAll(analysisProps);
      initAnalysisProperties(allProps);
      doExecute(allProps);
    } catch (IOException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  private void initBootstrapDefaultValues() {
    String sonarHostUrl = system.getEnvironmentVariable(SONAR_HOST_URL_ENV_VAR);
    if (sonarHostUrl != null) {
      setBootstrapPropertyIfNotAlreadySet(ScannerProperties.HOST_URL, sonarHostUrl);
    } else {
      setBootstrapPropertyIfNotAlreadySet(ScannerProperties.HOST_URL, getSonarCloudUrl());
    }
  }

  private String getSonarCloudUrl() {
    return bootstrapProperties.getOrDefault("sonar.scanner.sonarcloudUrl", SONARCLOUD_HOST);
  }

  private void initAnalysisProperties(Map<String, String> p) {
    initSourceEncoding(p);
    new Dirs(logger).init(p);
  }

  void initSourceEncoding(Map<String, String> p) {
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

  private void setBootstrapPropertyIfNotAlreadySet(String key, String value) {
    if (!bootstrapProperties.containsKey(key)) {
      setBootstrapProperty(key, value);
    }
  }

  protected void doStart() {
    checkLauncherDoesntExist();
    Set<String> unmaskRules = new HashSet<>();
    unmaskRules.add("org.sonarsource.scanner.lib.internal.batch.");
    ClassloadRules rules = new ClassloadRules(Collections.emptySet(), unmaskRules);
    launcher = launcherFactory.createLauncher(getBootstrapProperties(), rules);
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
