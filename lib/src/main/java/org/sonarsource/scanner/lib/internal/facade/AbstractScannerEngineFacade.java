/*
 * SonarScanner Java Library
 * Copyright (C) 2011-2025 SonarSource SA
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
package org.sonarsource.scanner.lib.internal.facade;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonarsource.scanner.lib.ScannerEngineFacade;
import org.sonarsource.scanner.lib.internal.facade.forked.JreCacheHit;
import org.sonarsource.scanner.lib.internal.util.VersionUtils;

public abstract class AbstractScannerEngineFacade implements ScannerEngineFacade {

  private final Map<String, String> bootstrapProperties;
  private final boolean isSonarCloud;
  private final String serverVersion;
  private final boolean wasEngineCacheHit;
  private final JreCacheHit wasJreCacheHit;

  protected AbstractScannerEngineFacade(Map<String, String> bootstrapProperties, boolean isSonarCloud, @Nullable String serverVersion,
    boolean wasEngineCacheHit, @Nullable JreCacheHit wasJreCacheHit) {
    this.bootstrapProperties = bootstrapProperties;
    this.isSonarCloud = isSonarCloud;
    this.serverVersion = serverVersion;
    this.wasEngineCacheHit = wasEngineCacheHit;
    this.wasJreCacheHit = wasJreCacheHit;
  }

  @Override
  public String getServerVersion() {
    if (isSonarCloud) {
      throw new UnsupportedOperationException("Server version is not available for SonarCloud.");
    }
    return serverVersion;
  }

  @Override
  public boolean isSonarQubeCloud() {
    return isSonarCloud;
  }

  @Override
  public boolean analyze(Map<String, String> analysisProps) {
    Map<String, String> allProps = new HashMap<>();
    allProps.putAll(bootstrapProperties);
    allProps.putAll(analysisProps);
    initAnalysisProperties(allProps);
    addStatsProperties(allProps);
    return doAnalyze(allProps);
  }

  private void addStatsProperties(Map<String, String> allProps) {
    if (wasJreCacheHit != null) {
      allProps.put("sonar.scanner.wasJreCacheHit", wasJreCacheHit.name());
    }
    allProps.put("sonar.scanner.wasEngineCacheHit", String.valueOf(wasEngineCacheHit));
  }

  protected abstract boolean doAnalyze(Map<String, String> allProps);

  private static void initAnalysisProperties(Map<String, String> p) {
    new Dirs().init(p);
  }

  @Override
  public Map<String, String> getBootstrapProperties() {
    return bootstrapProperties;
  }

  @Override
  public String getServerLabel() {
    if (isSonarQubeCloud()) {
      return "SonarQube Cloud";
    }

    String version = getServerVersion();
    if (VersionUtils.compareMajor(version, 10) <= 0 || VersionUtils.compareMajor(version, 2025) >= 0) {
      return "SonarQube Server";
    }
    return "SonarQube Community Build";
  }
}
