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
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonarsource.scanner.lib.internal.batch.IsolatedLauncher;
import org.sonarsource.scanner.lib.internal.cache.Logger;
import org.sonarsource.scanner.lib.internal.http.ServerConnection;

public class IsolatedLauncherFactory {
  static final String ISOLATED_LAUNCHER_IMPL = "org.sonarsource.scanner.lib.internal.batch.BatchIsolatedLauncher";
  private final TempCleaning tempCleaning;
  private final String launcherImplClassName;
  private final Logger logger;
  private final Path sonarUserHome;

  /**
   * For unit tests
   */
  IsolatedLauncherFactory(String isolatedLauncherClassName, TempCleaning tempCleaning, Logger logger, Path sonarUserHome) {
    this.tempCleaning = tempCleaning;
    this.launcherImplClassName = isolatedLauncherClassName;
    this.logger = logger;
    this.sonarUserHome = sonarUserHome;
  }

  public IsolatedLauncherFactory(Logger logger, Path sonarUserHome) {
    this(ISOLATED_LAUNCHER_IMPL, new TempCleaning(logger), logger, sonarUserHome);
  }

  private IsolatedClassloader createClassLoader(List<File> jarFiles, ClassloadRules maskRules) {
    IsolatedClassloader classloader = new IsolatedClassloader(getClass().getClassLoader(), maskRules);
    classloader.addFiles(jarFiles);

    return classloader;
  }

  public IsolatedLauncherAndClassloader createLauncher(Map<String, String> props, ClassloadRules rules) {
    if (props.containsKey(InternalProperties.SCANNER_DUMP_TO_FILE)) {
      String version = props.get(InternalProperties.SCANNER_VERSION_SIMULATION);
      if (version == null) {
        version = "5.6";
      }
      return new IsolatedLauncherAndClassloader(new SimulatedLauncher(version, logger), null);
    }
    ServerConnection serverConnection = ServerConnection.create(props, logger, sonarUserHome);
    JarDownloader jarDownloader = new JarDownloaderFactory(serverConnection, logger, sonarUserHome).create();

    return createLauncher(jarDownloader, rules);
  }

  IsolatedLauncherAndClassloader createLauncher(final JarDownloader jarDownloader, final ClassloadRules rules) {
    try {
      List<File> jarFiles = jarDownloader.download();
      logger.debug("Create isolated classloader...");
      var cl = createClassLoader(jarFiles, rules);
      IsolatedLauncher objProxy = IsolatedLauncherProxy.create(cl, IsolatedLauncher.class, launcherImplClassName, logger);
      tempCleaning.clean();

      return new IsolatedLauncherAndClassloader(objProxy, cl);
    } catch (Exception e) {
      // Catch all other exceptions, which relates to reflection
      throw new ScannerException("Unable to execute SonarScanner analysis", e);
    }
  }

  public static class IsolatedLauncherAndClassloader implements AutoCloseable {
    private final IsolatedLauncher launcher;
    private final URLClassLoader classloader;

    public IsolatedLauncherAndClassloader(IsolatedLauncher launcher, @Nullable URLClassLoader classloader) {
      this.launcher = launcher;
      this.classloader = classloader;
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
  }

}
