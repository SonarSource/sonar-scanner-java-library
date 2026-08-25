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

import org.bouncycastle.jcajce.provider.asymmetric.RSA;
import org.bouncycastle.jcajce.provider.asymmetric.X509;
import org.bouncycastle.jcajce.provider.digest.SHA1;
import org.bouncycastle.jcajce.provider.digest.SHA224;
import org.bouncycastle.jcajce.provider.digest.SHA256;
import org.bouncycastle.jcajce.provider.digest.SHA384;
import org.bouncycastle.jcajce.provider.digest.SHA512;
import org.bouncycastle.jcajce.provider.kdf.PBKDF2;
import org.bouncycastle.jcajce.provider.symmetric.AES;
import org.bouncycastle.jcajce.provider.symmetric.DESede;
import org.bouncycastle.jcajce.provider.symmetric.PBEPBKDF1;
import org.bouncycastle.jcajce.provider.symmetric.PBEPBKDF2;
import org.bouncycastle.jcajce.provider.symmetric.PBEPKCS12;
import org.bouncycastle.jcajce.provider.symmetric.RC2;

/**
 * {@code PKCS12KeyStoreSpi} always resolves its {@code Cipher}/{@code Mac}/{@code SecretKeyFactory}
 * lookups through BC's own (lazily constructed, never globally registered via
 * {@code Security.addProvider}) {@code BouncyCastleProvider} instance, regardless of which
 * {@code JcaJceHelper} is passed to its constructor — the helper argument only ever configures the
 * X.509 {@code CertificateFactory}. {@code BouncyCastleProvider}'s constructor in turn registers
 * ~150 algorithm implementations by reflectively loading classes named by string concatenation
 * (package name + algorithm name + {@code "$Mappings"}), which {@code maven-shade-plugin}'s
 * {@code minimizeJar} bytecode analysis cannot see: without this class, minimization silently drops
 * every one of them, and {@code PKCS12KeyStoreSpi} then fails at runtime with
 * {@code NoSuchAlgorithmException: no such algorithm ... for provider BC} for real PKCS#12 files.
 * <p>
 * This class exists purely to give the minimizer an ordinary, direct bytecode reference to exactly
 * the {@code Mappings} classes this library's supported PKCS#12 algorithms need — the digests used
 * by PBKDF2's PRF and by MacData integrity checks, the PBES1/PKCS12 PBE combined algorithms, the
 * underlying RC2/DESede/AES block ciphers, and the PBKDF2 key derivation function itself — so they
 * survive minimization instead of only the {@code PKCS12} {@code KeyStoreSpi} classes referenced by
 * {@link Pkcs12KeyStoreFactory}.
 */
final class SupportedAlgorithms {

  /**
   * Never read: each element exists solely for the bytecode reference its class literal produces,
   * see the class javadoc above. Includes the digests used by PBKDF2's PRF and by MacData integrity
   * checks, the PBES1/PKCS12 PBE combined algorithms, the underlying RC2/DESede/AES block ciphers,
   * the PBKDF2 key derivation function itself, and RSA/X.509 (needed to unwrap a
   * PKCS8ShroudedKeyBag's private key, even though this library only ever extracts certificates:
   * {@code PKCS12KeyStoreSpi.engineLoad} still processes every bag in the file).
   */
  @SuppressWarnings("unused")
  static final Class<?>[] REQUIRED_FOR_PKCS12 = {
    SHA1.Mappings.class,
    SHA224.Mappings.class,
    SHA256.Mappings.class,
    SHA384.Mappings.class,
    SHA512.Mappings.class,
    PBEPBKDF1.Mappings.class,
    PBEPBKDF2.Mappings.class,
    PBEPKCS12.Mappings.class,
    RC2.Mappings.class,
    DESede.Mappings.class,
    AES.Mappings.class,
    PBKDF2.Mappings.class,
    X509.Mappings.class,
    RSA.Mappings.class,
  };

  private SupportedAlgorithms() {
  }

}
