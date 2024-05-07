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

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.scanner.lib.internal.batch.IsolatedLauncher;
import org.sonarsource.scanner.lib.internal.cache.CachedFile;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.http.ServerConnection;

public class IsolatedLauncherFactory {

  private static final Logger LOG = LoggerFactory.getLogger(IsolatedLauncherFactory.class);

  static final String ISOLATED_LAUNCHER_IMPL = "org.sonarsource.scanner.lib.internal.batch.BatchIsolatedLauncher";
  private final TempCleaning tempCleaning;
  private final String launcherImplClassName;

  /**
   * For unit tests
   */
  IsolatedLauncherFactory(String isolatedLauncherClassName, TempCleaning tempCleaning) {
    this.tempCleaning = tempCleaning;
    this.launcherImplClassName = isolatedLauncherClassName;
  }

  public IsolatedLauncherFactory() {
    this(ISOLATED_LAUNCHER_IMPL, new TempCleaning());
  }

  private IsolatedClassloader createClassLoader(List<Path> jarFiles, ClassloadRules maskRules) {
    IsolatedClassloader classloader = new IsolatedClassloader(getClass().getClassLoader(), maskRules);
    classloader.addFiles(jarFiles);

    return classloader;
  }

  public IsolatedLauncherAndClassloader createLauncher(ServerConnection serverConnection, FileCache fileCache) {
    Set<String> unmaskRules = new HashSet<>();
    unmaskRules.add("org.sonarsource.scanner.lib.internal.batch.");
    ClassloadRules rules = new ClassloadRules(Collections.emptySet(), unmaskRules);
    LegacyScannerEngineDownloader legacyScannerEngineDownloader = new LegacyScannerEngineDownloaderFactory(serverConnection, fileCache).create();
    return createLauncher(legacyScannerEngineDownloader, rules);
  }

  IsolatedLauncherAndClassloader createLauncher(final LegacyScannerEngineDownloader legacyScannerEngineDownloader, final ClassloadRules rules) {
    try {
      List<CachedFile> jarFiles = legacyScannerEngineDownloader.getOrDownload();
      LOG.debug("Create isolated classloader...");
      var cl = createClassLoader(jarFiles.stream().map(CachedFile::getPathInCache).collect(Collectors.toList()), rules);
      IsolatedLauncher objProxy = IsolatedLauncherProxy.create(cl, IsolatedLauncher.class, launcherImplClassName);
      tempCleaning.clean();

      return new IsolatedLauncherAndClassloader(objProxy, cl, jarFiles.stream().allMatch(CachedFile::isCacheHit));
    } catch (Exception e) {
      // Catch all other exceptions, which relates to reflection
      throw new ScannerException("Unable to execute SonarScanner analysis", e);
    }
  }

  public static class IsolatedLauncherAndClassloader implements AutoCloseable {
    private final IsolatedLauncher launcher;
    private final URLClassLoader classloader;
    private final boolean engineCacheHit;

    public IsolatedLauncherAndClassloader(IsolatedLauncher launcher, @Nullable URLClassLoader classloader, boolean engineCacheHit) {
      this.launcher = launcher;
      this.classloader = classloader;
      this.engineCacheHit = engineCacheHit;
    }

    public IsolatedLauncher getLauncher() {
      return launcher;
    }

    @Override
    public void close() throws Exception {
      if (classloader != null) {
        classloader.close();
      }
    }

    public boolean wasEngineCacheHit() {
      return this.engineCacheHit;
    }
  }

}
