/*
 * SonarScanner Java Library :: Shaded BouncyCastle
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
package org.sonarsource.scanner.lib.internal.impldep;

import java.security.KeyStore;
import java.security.KeyStoreSpi;
import org.bouncycastle.jcajce.provider.keystore.pkcs12.PKCS12KeyStoreSpi;

/**
 * The sole reason this module (and its BouncyCastle dependency) exists: an unloaded, PKCS12-typed
 * {@link KeyStore} backed by BC's own ASN.1 parser, for reading certificate-only PKCS#12 bags that
 * the JDK's own provider silently discards (see the {@code Pkcs12CertificateReader} javadoc in the
 * {@code lib} module for the full rationale).
 * <p>
 * {@code DefPKCS12KeyStore} is instantiated directly rather than via
 * {@code KeyStore.getInstance("PKCS12", new BouncyCastleProvider())} — it is never registered as a
 * {@code java.security.Provider} via {@code Security.addProvider}, so it never conflicts with (or is
 * discoverable by) any other BouncyCastle version on the classpath. It still internally constructs an
 * unregistered {@code BouncyCastleProvider} instance for its own {@code Cipher}/{@code Mac}/
 * {@code SecretKeyFactory} lookups (only its X.509 {@code CertificateFactory} construction is actually
 * configurable) — see {@link SupportedAlgorithms} for why that matters for minimization.
 */
public final class Pkcs12KeyStoreFactory {

  // Forces retention of the BC algorithm classes PKCS12KeyStoreSpi needs at runtime through
  // minimizeJar - see SupportedAlgorithms' javadoc. Never read otherwise.
  @SuppressWarnings("unused")
  private static final Class<?>[] REQUIRED_ALGORITHMS = SupportedAlgorithms.REQUIRED_FOR_PKCS12;

  private Pkcs12KeyStoreFactory() {
  }

  public static KeyStore newKeyStore() {
    return new DirectKeyStore(new PKCS12KeyStoreSpi.DefPKCS12KeyStore());
  }

  private static final class DirectKeyStore extends KeyStore {
    private DirectKeyStore(KeyStoreSpi keyStoreSpi) {
      super(keyStoreSpi, null, "PKCS12");
    }
  }

}
