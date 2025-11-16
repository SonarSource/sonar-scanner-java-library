/*
 * SonarScanner Java Library
 * Copyright (C) 2011-2025 SonarSource Sàrl
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

public abstract class AbstractScannerEngineFacade implements ScannerEngineFacade {

  private final Map<String, String> bootstrapProperties;
  private final boolean isSonarQubeCloud;
  private final String serverVersion;
  private final Boolean didEngineCacheHit;
  private final JreCacheHit jreCacheHit;

  protected AbstractScannerEngineFacade(Map<String, String> bootstrapProperties, boolean isSonarQubeCloud, @Nullable String serverVersion,
    @Nullable Boolean didEngineCacheHit, @Nullable JreCacheHit jreCacheHit) {
    this.bootstrapProperties = bootstrapProperties;
    this.isSonarQubeCloud = isSonarQubeCloud;
    this.serverVersion = serverVersion;
    this.didEngineCacheHit = didEngineCacheHit;
    this.jreCacheHit = jreCacheHit;
  }

  @Override
  public String getServerVersion() {
    if (isSonarQubeCloud) {
      throw new UnsupportedOperationException("Server version is not available for SonarQube Cloud.");
    }
    return serverVersion;
  }

  @Override
  public boolean isSonarQubeCloud() {
    return isSonarQubeCloud;
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
    if (jreCacheHit != null) {
      allProps.put("sonar.scanner.wasJreCacheHit", jreCacheHit.name());
    }
    if (didEngineCacheHit != null) {
      allProps.put("sonar.scanner.wasEngineCacheHit", String.valueOf(didEngineCacheHit));
    }
  }

  protected abstract boolean doAnalyze(Map<String, String> allProps);

  private static void initAnalysisProperties(Map<String, String> p) {
    new Dirs().init(p);
  }

  @Override
  public Map<String, String> getBootstrapProperties() {
    return bootstrapProperties;
  }

}
