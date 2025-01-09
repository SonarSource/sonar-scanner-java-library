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
package org.sonarsource.scanner.lib.internal.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class Utils {

  private Utils() {
    // only util static methods
  }

  public static void deleteQuietly(Path f) {
    try {
      Files.walkFileTree(f, new DeleteQuietlyFileVisitor());
    } catch (IOException e) {
      // ignore
    }
  }

  private static class DeleteQuietlyFileVisitor extends SimpleFileVisitor<Path> {
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
      return deleteAndContinue(file);
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
      return deleteAndContinue(file);
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
      return deleteAndContinue(dir);
    }

    private static FileVisitResult deleteAndContinue(Path path) {
      try {
        Files.delete(path);
      } catch (IOException e) {
        // ignore
      }
      return FileVisitResult.CONTINUE;
    }
  }
}
