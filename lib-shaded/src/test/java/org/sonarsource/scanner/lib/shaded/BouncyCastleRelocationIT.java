/*
 * SonarScanner Java Library :: Shaded
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.scanner.lib.shaded;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the org.bouncycastle relocation excludes in pom.xml against a BouncyCastle upgrade introducing new
 * system-property names: bcprov reads many of these as plain String literals (not through
 * org.bouncycastle.util.Properties alone), and the shade plugin rewrites any string constant that looks like a
 * class name, silently breaking such a property in the shaded jar while it keeps working in the plain lib jar.
 */
class BouncyCastleRelocationIT {

  private static final String SHADED_BOUNCYCASTLE_PACKAGE = "org/sonarsource/scanner/lib/internal/shaded/org/bouncycastle/";
  private static final Pattern DOTTED_BOUNCYCASTLE_LITERAL = Pattern.compile("org\\.bouncycastle\\.[a-zA-Z0-9_.-]+");
  private static final Pattern POM_EXCLUDE = Pattern.compile("<exclude>(org\\.bouncycastle\\.[^<]+)</exclude>");

  @Test
  void shaded_jar_does_not_rename_bouncycastle_property_names() throws IOException {
    Set<String> excludedProperties = readBouncyCastleExcludesFromPom();

    Path bcprovJar = Path.of(System.getProperty("bcprovJarPath"));
    Set<String> realNames = collectRealNames(bcprovJar);
    Set<String> propertyLikeLiterals = collectDottedBouncyCastleLiterals(bcprovJar, "org/bouncycastle/").stream()
      .filter(literal -> !isRealReference(realNames, literal))
      .collect(Collectors.toSet());

    assertThat(propertyLikeLiterals)
      .as("bcprov-jdk18on has org.bouncycastle.* string constants that are not real class/resource references, "
        + "meaning they are BouncyCastle system-property names read as literals. Each one must be added as an "
        + "<exclude> under the org.bouncycastle relocation in lib-shaded/pom.xml, otherwise it silently gets "
        + "renamed in the shaded jar and users setting it via -D no longer have any effect there.")
      .isSubsetOf(excludedProperties);

    Path shadedJar = Path.of(System.getProperty("shadedJarPath"));
    Set<String> shadedJarLiterals = collectDottedBouncyCastleLiterals(shadedJar, SHADED_BOUNCYCASTLE_PACKAGE);

    assertThat(shadedJarLiterals)
      .as("Properties excluded from relocation in lib-shaded/pom.xml must survive unrenamed in the shaded jar")
      .containsAll(excludedProperties);
  }

  private static Set<String> readBouncyCastleExcludesFromPom() throws IOException {
    String pom = Files.readString(Path.of("pom.xml"));
    int relocationStart = pom.indexOf("<pattern>org.bouncycastle</pattern>");
    int excludesEnd = pom.indexOf("</excludes>", relocationStart);
    String excludesBlock = pom.substring(relocationStart, excludesEnd);

    Set<String> excludes = new HashSet<>();
    Matcher matcher = POM_EXCLUDE.matcher(excludesBlock);
    while (matcher.find()) {
      excludes.add(matcher.group(1));
    }
    return excludes;
  }

  private static boolean isRealReference(Set<String> realNames, String literal) {
    String normalized = literal.endsWith(".") ? literal.substring(0, literal.length() - 1) : literal;
    return realNames.contains(normalized);
  }

  /**
   * Every class/resource path in the jar, and each of its ancestor packages, in dotted form - i.e. every name
   * that a relocatable class or resource reference could legitimately look like.
   */
  private static Set<String> collectRealNames(Path jar) throws IOException {
    Set<String> realNames = new HashSet<>();
    try (JarFile jarFile = new JarFile(jar.toFile())) {
      for (JarEntry entry : jarFile.stream().collect(Collectors.toList())) {
        if (entry.isDirectory()) {
          continue;
        }
        String path = entry.getName();
        int lastSlash = path.lastIndexOf('/');
        int lastDotInFileName = path.substring(lastSlash + 1).lastIndexOf('.');
        String withoutExtension = lastDotInFileName < 0 ? path : path.substring(0, lastSlash + 1 + lastDotInFileName);
        String dotted = withoutExtension.replace('/', '.');
        for (int i = dotted.length(); i > 0; i = dotted.lastIndexOf('.', i - 1)) {
          realNames.add(dotted.substring(0, i));
        }
      }
    }
    return realNames;
  }

  private static Set<String> collectDottedBouncyCastleLiterals(Path jar, String classEntryPrefix) throws IOException {
    Set<String> literals = new HashSet<>();
    try (JarFile jarFile = new JarFile(jar.toFile())) {
      for (JarEntry entry : jarFile.stream().collect(Collectors.toList())) {
        if (!entry.getName().startsWith(classEntryPrefix) || !entry.getName().endsWith(".class")) {
          continue;
        }
        try (InputStream in = jarFile.getInputStream(entry)) {
          for (String constant : readUtf8Constants(in.readAllBytes())) {
            if (DOTTED_BOUNCYCASTLE_LITERAL.matcher(constant).matches()) {
              literals.add(constant);
            }
          }
        }
      }
    }
    return literals;
  }

  /**
   * Reads every CONSTANT_Utf8 entry of a class file's constant pool (JVM spec 4.4), which is where both class/
   * member names and String literals (via LDC or a field's ConstantValue attribute) ultimately live.
   */
  private static Set<String> readUtf8Constants(byte[] classBytes) throws IOException {
    Set<String> utf8Constants = new HashSet<>();
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(classBytes))) {
      in.readInt(); // magic
      in.readUnsignedShort(); // minor version
      in.readUnsignedShort(); // major version
      int constantPoolCount = in.readUnsignedShort();
      for (int i = 1; i < constantPoolCount; i++) {
        int tag = in.readUnsignedByte();
        switch (tag) {
          case 1:
            utf8Constants.add(in.readUTF());
            break;
          case 7:
          case 8:
          case 16:
          case 19:
          case 20:
            in.skipBytes(2);
            break;
          case 15:
            in.skipBytes(3);
            break;
          case 3:
          case 4:
          case 9:
          case 10:
          case 11:
          case 12:
          case 17:
          case 18:
            in.skipBytes(4);
            break;
          case 5:
          case 6:
            in.skipBytes(8);
            i++; // long/double constants occupy two constant pool entries
            break;
          default:
            throw new IllegalStateException("Unexpected constant pool tag: " + tag);
        }
      }
    }
    return utf8Constants;
  }
}
