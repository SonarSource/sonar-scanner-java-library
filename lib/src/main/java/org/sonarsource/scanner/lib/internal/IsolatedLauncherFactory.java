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
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.scanner.lib.internal.batch.IsolatedLauncher;
import org.sonarsource.scanner.lib.internal.cache.CachedFile;
import org.sonarsource.scanner.lib.internal.cache.FileCache;
import org.sonarsource.scanner.lib.internal.cache.Logger;
import org.sonarsource.scanner.lib.internal.http.ServerConnection;

public class IsolatedLauncherFactory {
  static final String ISOLATED_LAUNCHER_IMPL = "org.sonarsource.scanner.lib.internal.batch.BatchIsolatedLauncher";
  private final TempCleaning tempCleaning;
  private final String launcherImplClassName;
  private final Logger logger;

  /**
   * For unit tests
   */
  IsolatedLauncherFactory(String isolatedLauncherClassName, TempCleaning tempCleaning, Logger logger) {
    this.tempCleaning = tempCleaning;
    this.launcherImplClassName = isolatedLauncherClassName;
    this.logger = logger;
  }

  public IsolatedLauncherFactory(Logger logger) {
    this(ISOLATED_LAUNCHER_IMPL, new TempCleaning(logger), logger);
  }

  private IsolatedClassloader createClassLoader(List<Path> jarFiles, ClassloadRules maskRules) {
    IsolatedClassloader classloader = new IsolatedClassloader(getClass().getClassLoader(), maskRules);
    classloader.addFiles(jarFiles);

    return classloader;
  }

  public IsolatedLauncherAndClassloader createLauncher(ClassloadRules rules, ServerConnection serverConnection, FileCache fileCache) {
    LegacyScannerEngineDownloader legacyScannerEngineDownloader = new LegacyScannerEngineDownloaderFactory(serverConnection, logger, fileCache).create();
    return createLauncher(legacyScannerEngineDownloader, rules);
  }

  IsolatedLauncherAndClassloader createLauncher(final LegacyScannerEngineDownloader legacyScannerEngineDownloader, final ClassloadRules rules) {
    try {
      List<CachedFile> jarFiles = legacyScannerEngineDownloader.getOrDownload();
      logger.debug("Create isolated classloader...");
      var cl = createClassLoader(jarFiles.stream().map(CachedFile::getPathInCache).collect(Collectors.toList()), rules);
      IsolatedLauncher objProxy = IsolatedLauncherProxy.create(cl, IsolatedLauncher.class, launcherImplClassName, logger);
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
