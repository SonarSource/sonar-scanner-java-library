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
package org.sonarsource.scanner.api.internal;

import javax.annotation.concurrent.Immutable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Immutable
public class ClassloadRules {
  private final List<String> mask;
  private final List<String> unmask;

  public ClassloadRules(Set<String> maskRules, Set<String> unmaskRules) {
    this.mask = new ArrayList<>(maskRules);
    this.unmask = new ArrayList<>(unmaskRules);
  }

  public boolean canLoad(String className) {
    // if there is a tie -> block it
    return unmaskSize(className) > maskSize(className);
  }

  private int maskSize(String className) {
    return findBestMatch(mask, className);
  }

  private int unmaskSize(String className) {
    return findBestMatch(unmask, className);
  }

  private static int findBestMatch(List<String> list, String name) {
    // there can be a match of 0 ("")
    int bestMatch = -1;
    for (String s : list) {
      if (name.startsWith(s) && s.length() > bestMatch) {
        bestMatch = s.length();
      }
    }

    return bestMatch;
  }
}
