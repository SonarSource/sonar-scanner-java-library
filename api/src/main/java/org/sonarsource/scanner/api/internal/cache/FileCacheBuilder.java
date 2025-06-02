/*
 * SonarQube Scanner API
 * Copyright (C) 2011-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.scanner.api.internal.cache;

import java.io.File;
import javax.annotation.Nullable;

public class FileCacheBuilder {
  private final Logger logger;
  private File userHome;

  public FileCacheBuilder(Logger logger) {
    this.logger = logger;
  }

  public FileCacheBuilder setUserHome(File d) {
    this.userHome = d;
    return this;
  }

  public FileCacheBuilder setUserHome(@Nullable String path) {
    this.userHome = (path == null) ? null : new File(path);
    return this;
  }

  public FileCache build() {
    if (userHome == null) {
      userHome = findHome();
    }
    File cacheDir = new File(userHome, "cache");
    return FileCache.create(cacheDir.toPath(), logger);
  }

  private static File findHome() {
    String path = System.getenv("SONAR_USER_HOME");
    if (path == null) {
      // Default
      path = System.getProperty("user.home") + File.separator + ".sonar";
    }
    return new File(path);
  }
}
