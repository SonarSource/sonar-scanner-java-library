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
package org.sonarsource.scanner.lib.internal.cache;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileCacheTest {
  private static final String HASH_ALGO = "algo";
  private FileHashes fileHashes;
  private FileCache cache;
  private Map<String, String> properties;

  @TempDir
  private Path temp;

  @BeforeEach
  public void setUp() {
    fileHashes = mock(FileHashes.class);
    properties = new HashMap<>();
    cache = new FileCache(temp, fileHashes, mock(Logger.class), properties);
  }

  @Test
  void not_in_cache() {
    assertThat(cache.get("sonar-foo-plugin-1.5.jar", "ABCDE")).isNull();
  }

  @Test
  void found_in_cache() throws IOException {
    // populate the cache. Assume that hash is correct.
    File cachedFile = new File(new File(cache.getDir(), "ABCDE"), "sonar-foo-plugin-1.5.jar");
    write(cachedFile, "body");

    assertThat(cache.get("sonar-foo-plugin-1.5.jar", "ABCDE")).isNotNull().exists().isEqualTo(cachedFile);
  }

  @Test
  void fail_to_download() {
    when(fileHashes.of(any(File.class), eq(HASH_ALGO))).thenReturn("ABCDE");

    FileCache.Downloader downloader = new FileCache.Downloader() {
      public void download(String filename, File toFile) throws IOException {
        throw new IOException("fail");
      }
    };
    assertThatThrownBy(() -> cache.get("sonar-foo-plugin-1.5.jar", "ABCDE", HASH_ALGO, downloader, null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Fail to download");
  }

  @Test
  void fail_create_temp_file() throws IOException {
    when(fileHashes.of(any(File.class), eq(HASH_ALGO))).thenReturn("ABCDE");
    Files.delete(temp.resolve("_tmp"));
    assertThatThrownBy(() -> cache.get("sonar-foo-plugin-1.5.jar", "ABCDE", HASH_ALGO, mock(FileCache.Downloader.class), null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Fail to create temp file");
  }

  @Test
  void fail_create_hash_dir() throws IOException {
    Path file = temp.resolve("some-file");
    Files.createFile(file);
    assertThatThrownBy(() -> new FileCache(file, fileHashes, mock(Logger.class), null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Unable to create user cache");
  }

  @Test
  void fail_to_create_hash_dir() throws IOException {
    when(fileHashes.of(any(File.class), eq(HASH_ALGO))).thenReturn("ABCDE");

    File hashDir = new File(cache.getDir(), "ABCDE");
    hashDir.createNewFile();
    assertThatThrownBy(() -> cache.get("sonar-foo-plugin-1.5.jar", "ABCDE", HASH_ALGO, mock(FileCache.Downloader.class), null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Fail to create cache directory");
  }

  @Test
  void download_and_add_to_cache() throws IOException {
    when(fileHashes.of(any(File.class), eq(HASH_ALGO))).thenReturn("ABCDE");

    FileCache.Downloader downloader = new FileCache.Downloader() {
      boolean single = false;

      public void download(String filename, File toFile) throws IOException {
        if (single) {
          throw new IllegalStateException("Already called");
        }
        write(toFile, "body");
        single = true;
      }
    };
    String cacheHitProperty = "cache-hit-property";

    File cachedFile = cache.get("sonar-foo-plugin-1.5.jar", "ABCDE", HASH_ALGO, downloader, cacheHitProperty);
    assertThat(cachedFile).isNotNull().exists().isFile();
    assertThat(cachedFile.getName()).isEqualTo("sonar-foo-plugin-1.5.jar");
    assertThat(cachedFile.getParentFile().getParentFile()).isEqualTo(cache.getDir());
    assertThat(read(cachedFile)).isEqualTo("body");
    assertThat(properties).containsEntry(cacheHitProperty, "false");

    File againFromCache = cache.get("sonar-foo-plugin-1.5.jar", "ABCDE", HASH_ALGO, downloader, cacheHitProperty);
    assertThat(againFromCache).isNotNull().exists().isFile();
    assertThat(againFromCache.getName()).isEqualTo("sonar-foo-plugin-1.5.jar");
    assertThat(againFromCache.getParentFile().getParentFile()).isEqualTo(cache.getDir());
    assertThat(read(againFromCache)).isEqualTo("body");
    assertThat(properties).containsEntry(cacheHitProperty, "true");
  }

  @Test
  void download_corrupted_file() {
    when(fileHashes.of(any(File.class), eq(HASH_ALGO))).thenReturn("VWXYZ");

    FileCache.Downloader downloader = new FileCache.Downloader() {
      public void download(String filename, File toFile) throws IOException {
        write(toFile, "corrupted body");
      }
    };
    assertThatThrownBy(() -> cache.get("sonar-foo-plugin-1.5.jar", "ABCDE", HASH_ALGO, downloader, null))
      .isInstanceOf(HashMismatchException.class)
      .hasMessageContaining("INVALID HASH");
  }

  @Test
  void concurrent_download() throws IOException {
    when(fileHashes.of(any(File.class), eq(HASH_ALGO))).thenReturn("ABCDE");

    FileCache.Downloader downloader = new FileCache.Downloader() {
      public void download(String filename, File toFile) throws IOException {
        // Emulate a concurrent download that adds file to cache before
        File cachedFile = new File(new File(cache.getDir(), "ABCDE"), "sonar-foo-plugin-1.5.jar");
        write(cachedFile, "downloaded by other");

        write(toFile, "downloaded by me");
      }
    };

    // do not fail
    File cachedFile = cache.get("sonar-foo-plugin-1.5.jar", "ABCDE", HASH_ALGO, downloader, null);
    assertThat(cachedFile).isNotNull().exists().isFile()
      .hasName("sonar-foo-plugin-1.5.jar");
    assertThat(cachedFile.getParentFile()).hasParent(cache.getDir());
    assertThat(read(cachedFile)).contains("downloaded by");
  }

  private static void write(File f, String txt) throws IOException {
    Files.createDirectories(f.toPath().getParent());
    Files.write(f.toPath(), txt.getBytes(StandardCharsets.UTF_8));
  }

  private static String read(File f) throws IOException {
    return Files.readString(f.toPath());
  }
}
