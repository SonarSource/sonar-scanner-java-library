/*
 * SonarQube Scanner API
 * Copyright (C) 2011-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonarsource.scanner.api.internal.ClassloadRules;
import org.sonarsource.scanner.api.internal.InternalProperties;
import org.sonarsource.scanner.api.internal.IsolatedLauncherFactory;
import org.sonarsource.scanner.api.internal.VersionUtils;
import org.sonarsource.scanner.api.internal.batch.IsolatedLauncher;
import org.sonarsource.scanner.api.internal.cache.Logger;

/**
 * Entry point to run SonarQube analysis programmatically.
 * @since 2.2
 */
public class EmbeddedScanner {
  private final IsolatedLauncherFactory launcherFactory;
  private IsolatedLauncher launcher;
  private final LogOutput logOutput;
  private final Properties globalProperties = new Properties();
  private final List<Object> extensions = new ArrayList<>();
  private final Logger logger;
  private final Set<String> classloaderMask = new HashSet<>();
  private final Set<String> classloaderUnmask = new HashSet<>();

  EmbeddedScanner(IsolatedLauncherFactory bl, Logger logger, LogOutput logOutput) {
    this.logger = logger;
    this.launcherFactory = bl;
    this.logOutput = logOutput;
    this.classloaderUnmask.add("org.sonarsource.scanner.api.internal.batch.");
  }

  public static EmbeddedScanner create(final LogOutput logOutput) {
    Logger logger = new LoggerAdapter(logOutput);
    return new EmbeddedScanner(new IsolatedLauncherFactory(logger), logger, logOutput);
  }

  public Properties globalProperties() {
    Properties clone = new Properties();
    clone.putAll(globalProperties);
    return clone;
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
   * Declare Sonar properties, for example sonar.projectKey=foo.
   * These might be used at different stages (on {@link #start() or #runAnalysis(Properties)}, depending on the 
   * property and SQ version.
   *
   */
  public EmbeddedScanner addGlobalProperties(Properties p) {
    globalProperties.putAll(p);
    return this;
  }

  /**
   * Declare a SonarQube property.
   * These might be used at different stages (on {@link #start() or #runAnalysis(Properties)}, depending on the 
   * property and SQ version.
   *
   * @see ScannerProperties
   * @see ScanProperties
   */
  public EmbeddedScanner setGlobalProperty(String key, String value) {
    globalProperties.setProperty(key, value);
    return this;
  }

  public String globalProperty(String key, @Nullable String defaultValue) {
    return globalProperties.getProperty(key, defaultValue);
  }

  /**
   * User-agent used in the HTTP requests to the SonarQube server
   */
  public EmbeddedScanner setApp(String app, String version) {
    setGlobalProperty(InternalProperties.RUNNER_APP, app);
    setGlobalProperty(InternalProperties.RUNNER_APP_VERSION, version);
    return this;
  }

  public String app() {
    return globalProperty(InternalProperties.RUNNER_APP, null);
  }

  /**
   * Add extensions to the batch's object container.
   * Only supported until SQ 5.1. For more recent versions, an exception is thrown 
   * @param objs
   */
  public EmbeddedScanner addExtensions(Object... objs) {
    checkLauncherExists();
    if (VersionUtils.isAtLeast52(launcher.getVersion())) {
      throw new IllegalStateException("not supported in current SonarQube version: " + launcher.getVersion());
    }

    extensions.addAll(Arrays.asList(objs));
    return this;
  }

  public String appVersion() {
    return globalProperty(InternalProperties.RUNNER_APP_VERSION, null);
  }

  /**
   * Launch an analysis.
   * Runner must have been started - see {@link #start()}.
   */
  public void runAnalysis(Properties analysisProperties) {
    checkLauncherExists();
    if (isSkip(analysisProperties)) {
      return;
    }
    Properties copy = new Properties();
    copy.putAll(analysisProperties);
    initAnalysisProperties(copy);
    doExecute(copy);
  }

  public void start() {
    initGlobalDefaultValues();
    doStart();
  }

  /**
   * Stops the batch.
   * Only supported starting in SQ 5.2. For older versions, this is a no-op.
   */
  public void stop() {
    checkLauncherExists();
    doStop();
  }

  public String serverVersion() {
    checkLauncherExists();
    return launcher.getVersion();
  }

  /**
   * @deprecated since 2.5 use {@link #start()}, {@link #runAnalysis(Properties)} and then {@link #stop()}
   */
  @Deprecated
  public final void execute() {
    start();
    runAnalysis(new Properties());
    stop();
  }

  private void initGlobalDefaultValues() {
    setGlobalDefaultValue(ScannerProperties.HOST_URL, "http://localhost:9000");
    setGlobalDefaultValue(InternalProperties.RUNNER_APP, "SonarQubeRunner");
    setGlobalDefaultValue(InternalProperties.RUNNER_APP_VERSION, ScannerApiVersion.version());
  }

  private void initAnalysisProperties(Properties p) {
    initSourceEncoding(p);
    new Dirs(logger).init(p);
  }

  void initSourceEncoding(Properties p) {
    boolean onProject = Utils.taskRequiresProject(p);
    if (onProject) {
      String sourceEncoding = p.getProperty(ScanProperties.PROJECT_SOURCE_ENCODING, "");
      boolean platformDependent = false;
      if ("".equals(sourceEncoding)) {
        sourceEncoding = Charset.defaultCharset().name();
        platformDependent = true;
        p.setProperty(ScanProperties.PROJECT_SOURCE_ENCODING, sourceEncoding);
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

  protected void doStart() {
    checkLauncherDoesntExist();
    ClassloadRules rules = new ClassloadRules(classloaderMask, classloaderUnmask);
    launcher = launcherFactory.createLauncher(globalProperties(), rules);
    if (VersionUtils.isAtLeast52(launcher.getVersion())) {
      launcher.start(globalProperties(), (formattedMessage, level) -> logOutput.log(formattedMessage, LogOutput.Level.valueOf(level.name())));
    }
  }

  protected void doStop() {
    if (VersionUtils.isAtLeast52(launcher.getVersion())) {
      launcher.stop();
      launcher = null;
    }
  }

  private boolean isSkip(Properties analysisProperties) {
    if ("true".equalsIgnoreCase(analysisProperties.getProperty(ScanProperties.SKIP))) {
      logger.info("SonarQube Scanner analysis skipped");
      return true;
    }
    return false;
  }

  protected void doExecute(Properties analysisProperties) {
    if (VersionUtils.isAtLeast52(launcher.getVersion())) {
      launcher.execute(analysisProperties);
    } else {
      Properties prop = new Properties();
      prop.putAll(globalProperties());
      prop.putAll(analysisProperties);
      launcher.executeOldVersion(prop, extensions);
    }
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
