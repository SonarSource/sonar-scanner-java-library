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
package org.sonarsource.scanner.lib.internal.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.IOUtils;

public final class CompressionUtils {

  private static final String ERROR_CREATING_DIRECTORY = "Error creating directory: ";

  private CompressionUtils() {
    // utility class
  }

  /**
   * Unzip a file into a directory. The directory is created if it does not exist.
   *
   * @return the target directory
   */
  public static File unzip(File zip, File toDir) throws IOException {
    return unzip(zip, toDir, ze -> true);
  }

  /**
   * Unzip a file to a directory.
   *
   * @param zip    the zip file. It must exist.
   * @param toDir  the target directory. It is created if needed.
   * @param filter filter zip entries so that only a subset of directories/files can be
   *               extracted to target directory.
   * @return the parameter {@code toDir}
   */
  public static File unzip(File zip, File toDir, Predicate<ZipEntry> filter) throws IOException {
    Path targetDirNormalizedPath = toDir.toPath().normalize();
    try (ZipFile zipFile = new ZipFile(zip)) {
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (filter.test(entry)) {
          File target = new File(toDir, entry.getName());

          verifyInsideTargetDirectory(entry, target.toPath(), targetDirNormalizedPath);

          if (entry.isDirectory()) {
            throwExceptionIfDirectoryIsNotCreatable(target);
          } else {
            File parent = target.getParentFile();
            throwExceptionIfDirectoryIsNotCreatable(parent);
            copy(zipFile, entry, target);
          }
        }
      }
      return toDir;
    }
  }

  private static void verifyInsideTargetDirectory(ZipEntry entry, Path entryPath, Path targetDirNormalizedPath) {
    if (!entryPath.normalize().startsWith(targetDirNormalizedPath)) {
      // vulnerability - trying to create a file outside the target directory
      throw new IllegalStateException("Unzipping an entry outside the target directory is not allowed: " + entry.getName());
    }
  }

  private static void throwExceptionIfDirectoryIsNotCreatable(File to) throws IOException {
    if (!to.exists() && !to.mkdirs()) {
      throw new IOException(ERROR_CREATING_DIRECTORY + to);
    }
  }

  private static void copy(ZipFile zipFile, ZipEntry entry, File to) throws IOException {
    try (InputStream input = zipFile.getInputStream(entry);
         OutputStream fos = Files.newOutputStream(to.toPath())) {
      IOUtils.copy(input, fos);
    }
  }

  public static void extractTarGz(File compressedFile, File targetDir) throws IOException {
    try (InputStream fis = new FileInputStream(compressedFile);
         InputStream bis = new BufferedInputStream(fis);
         InputStream gzis = new GzipCompressorInputStream(bis);
         TarArchiveInputStream archive = new TarArchiveInputStream(gzis)) {
      ArchiveEntry entry;
      while ((entry = archive.getNextEntry()) != null) {
        if (!archive.canReadEntryData(entry)) {
          continue;
        }
        File f = new File(targetDir, entry.getName());
        if (entry.isDirectory()) {
          if (!f.isDirectory() && !f.mkdirs()) {
            throw new IOException("failed to create directory " + f);
          }
        } else {
          File parent = f.getParentFile();
          if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("failed to create directory " + parent);
          }
          try (OutputStream o = Files.newOutputStream(f.toPath())) {
            IOUtils.copy(archive, o);
          }
        }
      }
    }
  }
}
