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
package org.sonarsource.scanner.lib.internal.facade.inprocess;

import java.util.ArrayList;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.scanner.lib.internal.http.ScannerHttpClient;

class BootstrapIndexDownloader {

  private static final Logger LOG = LoggerFactory.getLogger(BootstrapIndexDownloader.class);

  private final ScannerHttpClient conn;

  BootstrapIndexDownloader(ScannerHttpClient conn) {
    this.conn = conn;
  }

  Collection<JarEntry> getIndex() {
    String index;
    try {
      LOG.debug("Get bootstrap index...");
      index = conn.callWebApi("/batch/index");
      LOG.debug("Get bootstrap completed");
    } catch (Exception e) {
      throw new IllegalStateException("Fail to get bootstrap index from server", e);
    }
    return parse(index);
  }

  private Collection<JarEntry> parse(String index) {
    final Collection<JarEntry> entries = new ArrayList<>();

    String[] lines = index.split("[\r\n]+");
    for (String line : lines) {
      try {
        line = line.trim();
        String[] libAndHash = line.split("\\|");
        String filename = libAndHash[0];
        String hash = libAndHash[1];
        entries.add(new JarEntry(filename, hash));
      } catch (Exception e) {
        LOG.error("Failed bootstrap index response: {}", index);
        throw new IllegalStateException("Fail to parse entry in bootstrap index: " + line);
      }
    }

    return entries;
  }

  static class JarEntry {
    private String filename;
    private String hash;

    JarEntry(String filename, String hash) {
      this.filename = filename;
      this.hash = hash;
    }

    public String getFilename() {
      return filename;
    }

    public String getHash() {
      return hash;
    }
  }
}
