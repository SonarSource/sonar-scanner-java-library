/*
 * SonarScanner Java Library
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
package org.sonarsource.scanner.lib.internal.http.ssl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import org.sonarsource.scanner.lib.internal.impldep.Pkcs12KeyStoreFactory;

/**
 * Reads the X.509 certificate entries out of a PKCS#12 file, tolerating certificate-only bags
 * that the JDK's own {@code PKCS12} {@link KeyStore} provider silently discards because they
 * lack the proprietary "trusted key usage" attribute that only {@code keytool} or OpenSSL 3.3+'s
 * {@code -jdktrust} flag add.
 * <p>
 * The actual {@link KeyStore} is built by {@link Pkcs12KeyStoreFactory}, in the relocated/minimized
 * {@code sonar-scanner-bouncycastle-shaded} module — see its javadoc for why and how BouncyCastle is
 * used here without ever touching {@code BouncyCastleProvider}.
 * <p>
 * Known limitation inherited from BC's own implementation: a certificate bag with neither a
 * {@code friendlyName} nor a {@code localKeyId} attribute is parsed but never exposed as a KeyStore
 * alias, so such files require the truststore to have been generated with an explicit name, e.g.
 * {@code openssl pkcs12 -export -nokeys -caname <name> ...}.
 */
public final class Pkcs12CertificateReader {

  private Pkcs12CertificateReader() {
  }

  public static KeyStore readCertificates(Path path, char[] password) throws IOException, GeneralSecurityException {
    var keyStore = Pkcs12KeyStoreFactory.newKeyStore();
    try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ)) {
      keyStore.load(in, password);
    }
    return keyStore;
  }

}
