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
package org.sonarsource.scanner.lib.internal.cache;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

  @TempDir
  private Path temp;

  @BeforeEach
  void setUp() {
    fileHashes = mock(FileHashes.class);
    cache = new FileCache(temp, fileHashes);
  }

  @Test
  void not_in_cache() {
    assertThat(cache.get("sonar-foo-plugin-1.5.jar", "ABCDE")).isNull();
  }

  @Test
  void found_in_cache() throws IOException {
    // populate the cache. Assume that hash is correct.
    Path cachedFile = cache.getDir().resolve("ABCDE/sonar-foo-plugin-1.5.jar");
    write(cachedFile, "body");

    assertThat(cache.get("sonar-foo-plugin-1.5.jar", "ABCDE")).isNotNull().exists().isEqualTo(cachedFile);
  }

  @Test
  void fail_to_download() {
    when(fileHashes.of(any(File.class), eq(HASH_ALGO))).thenReturn("ABCDE");

    FileCache.Downloader downloader = new FileCache.Downloader() {
      public void download(String filename, Path toFile) throws IOException {
        throw new IOException("fail");
      }
    };
    assertThatThrownBy(() -> cache.getOrDownload("sonar-foo-plugin-1.5.jar", "ABCDE", HASH_ALGO, downloader))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Fail to download");
  }

  @Test
  void fail_create_temp_file() throws IOException {
    when(fileHashes.of(any(File.class), eq(HASH_ALGO))).thenReturn("ABCDE");
    Files.delete(temp.resolve("_tmp"));
    assertThatThrownBy(() -> cache.getOrDownload("sonar-foo-plugin-1.5.jar", "ABCDE", HASH_ALGO, mock(FileCache.Downloader.class)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Fail to create temp file");
  }

  @Test
  void fail_create_hash_dir() throws IOException {
    Path file = temp.resolve("some-file");
    Files.createFile(file);
    assertThatThrownBy(() -> new FileCache(file, fileHashes))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Unable to create user cache");
  }

  @Test
  void fail_to_create_hash_dir() throws IOException {
    when(fileHashes.of(any(File.class), eq(HASH_ALGO))).thenReturn("ABCDE");

    var hashDir = cache.getDir().resolve("ABCDE");
    Files.createFile(hashDir);
    assertThatThrownBy(() -> cache.getOrDownload("sonar-foo-plugin-1.5.jar", "ABCDE", HASH_ALGO, mock(FileCache.Downloader.class)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Fail to create cache directory");
  }

  @Test
  void download_and_add_to_cache() throws IOException {
    when(fileHashes.of(any(File.class), eq(HASH_ALGO))).thenReturn("ABCDE");

    FileCache.Downloader downloader = new FileCache.Downloader() {
      boolean single = false;

      public void download(String filename, Path toFile) throws IOException {
        if (single) {
          throw new IllegalStateException("Already called");
        }
        write(toFile, "body");
        single = true;
      }
    };
    var cachedFile = cache.getOrDownload("sonar-foo-plugin-1.5.jar", "ABCDE", HASH_ALGO, downloader);
    assertThat(cachedFile.getPathInCache()).isRegularFile()
      .hasFileName("sonar-foo-plugin-1.5.jar");
    assertThat(cachedFile.getPathInCache().getParent()).hasParent(cache.getDir());
    assertThat(read(cachedFile.getPathInCache())).isEqualTo("body");
    assertThat(cachedFile.getCacheHit()).isFalse();

    var againFromCache = cache.getOrDownload("sonar-foo-plugin-1.5.jar", "ABCDE", HASH_ALGO, downloader);
    assertThat(againFromCache.getPathInCache()).isRegularFile()
      .hasFileName("sonar-foo-plugin-1.5.jar");
    assertThat(againFromCache.getPathInCache().getParent()).hasParent(cache.getDir());
    assertThat(read(againFromCache.getPathInCache())).isEqualTo("body");
    assertThat(againFromCache.getCacheHit()).isTrue();
  }

  @Test
  void download_corrupted_file() {
    when(fileHashes.of(any(File.class), eq(HASH_ALGO))).thenReturn("VWXYZ");

    FileCache.Downloader downloader = new FileCache.Downloader() {
      public void download(String filename, Path toFile) throws IOException {
        write(toFile, "corrupted body");
      }
    };
    assertThatThrownBy(() -> cache.getOrDownload("sonar-foo-plugin-1.5.jar", "ABCDE", HASH_ALGO, downloader))
      .isInstanceOf(HashMismatchException.class)
      .hasMessageContaining("INVALID HASH");
  }

  @Test
  void concurrent_download() throws IOException {
    when(fileHashes.of(any(File.class), eq(HASH_ALGO))).thenReturn("ABCDE");

    FileCache.Downloader downloader = new FileCache.Downloader() {
      public void download(String filename, Path toFile) throws IOException {
        // Emulate a concurrent download that adds file to cache before
        var cachedFile = cache.getDir().resolve("ABCDE/sonar-foo-plugin-1.5.jar");
        write(cachedFile, "downloaded by other");

        write(toFile, "downloaded by me");
      }
    };

    // do not fail
    var cachedFile = cache.getOrDownload("sonar-foo-plugin-1.5.jar", "ABCDE", HASH_ALGO, downloader);
    assertThat(cachedFile.getPathInCache()).isRegularFile()
      .hasFileName("sonar-foo-plugin-1.5.jar");
    assertThat(cachedFile.getPathInCache().getParent()).hasParent(cache.getDir());
    assertThat(read(cachedFile.getPathInCache())).contains("downloaded by");
  }

  private static void write(Path f, String txt) throws IOException {
    Files.createDirectories(f.getParent());
    Files.write(f, txt.getBytes(StandardCharsets.UTF_8));
  }

  private static String read(Path f) throws IOException {
    return Files.readString(f);
  }
}
