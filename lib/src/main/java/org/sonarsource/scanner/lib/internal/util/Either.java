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

import java.util.Objects;
import java.util.function.Function;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class Either<L, R> {

  public static <L, R> Either<L, R> forLeft(L left) {
    return new Either<>(Objects.requireNonNull(left), null);
  }

  public static <L, R> Either<L, R> forRight(R right) {
    return new Either<>(null, Objects.requireNonNull(right));
  }

  private final L left;
  private final R right;

  protected Either(@Nullable L left, @Nullable R right) {
    this.left = left;
    this.right = right;
  }

  @CheckForNull
  public L getLeft() {
    return left;
  }

  @CheckForNull
  public R getRight() {
    return right;
  }

  public boolean isLeft() {
    return left != null;
  }

  public boolean isRight() {
    return right != null;
  }

  public <T> T map(
    Function<? super L, ? extends T> mapLeft,
    Function<? super R, ? extends T> mapRight) {
    if (isLeft()) {
      return mapLeft.apply(getLeft());
    }
    return mapRight.apply(getRight());
  }

}
