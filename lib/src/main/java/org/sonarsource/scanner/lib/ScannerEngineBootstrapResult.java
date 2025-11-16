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
package org.sonarsource.scanner.lib;

/**
 * Closing this will automatically close the {@link ScannerEngineFacade} that it contains, if any.
 */
public interface ScannerEngineBootstrapResult extends AutoCloseable {

  /**
   * Allow to test if the bootstrapping has been successful. If not, the {@link ScannerEngineFacade} should not be used.
   * A log message should have been emitted in case of failure.
   *
   * @return true if the bootstrapping has been successful, false otherwise
   */
  boolean isSuccessful();

  /**
   * Get the facade to interact with the engine. Only call this method if {@link #isSuccessful()} returns true.
   *
   * @return the facade to interact with the engine
   */
  ScannerEngineFacade getEngineFacade();
}
