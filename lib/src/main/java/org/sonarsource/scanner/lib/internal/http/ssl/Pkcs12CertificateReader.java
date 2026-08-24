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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Reads the X.509 certificate entries out of a PKCS#12 file, tolerating certificate-only bags
 * that the JDK's own {@code PKCS12} {@link KeyStore} provider silently discards because they
 * lack the proprietary "trusted key usage" attribute that only {@code keytool} or OpenSSL 3.3+'s
 * {@code -jdktrust} flag add. Private key bags are ignored: this reader is only used for
 * truststores, never for client identity keystores.
 * <p>
 * Only what is needed to reach {@code CertBag} entries is parsed: {@code PFX} &rarr; {@code authSafe}
 * {@code ContentInfo} &rarr; {@code AuthenticatedSafe} &rarr; each inner {@code ContentInfo}
 * (plain or PBES1/PBES2-encrypted) &rarr; {@code SafeContents} &rarr; {@code SafeBag}. The
 * top-level {@code MacData} integrity check is intentionally not verified, matching the tolerant
 * behavior this reader replaces: a truststore file is already a path the user explicitly configured.
 */
public final class Pkcs12CertificateReader {

  private static final String OID_DATA = "1.2.840.113549.1.7.1";
  private static final String OID_ENCRYPTED_DATA = "1.2.840.113549.1.7.6";
  private static final String OID_CERT_BAG = "1.2.840.113549.1.12.10.1.3";
  private static final String OID_X509_CERTIFICATE = "1.2.840.113549.1.9.22.1";

  private static final String OID_PBES2 = "1.2.840.113549.1.5.13";
  private static final String OID_PBKDF2 = "1.2.840.113549.1.5.12";
  private static final String OID_PBE_SHA1_RC2_40 = "1.2.840.113549.1.12.1.6";
  private static final String OID_PBE_SHA1_3DES = "1.2.840.113549.1.12.1.3";

  private static final String OID_AES128_CBC = "2.16.840.1.101.3.4.1.2";
  private static final String OID_AES192_CBC = "2.16.840.1.101.3.4.1.22";
  private static final String OID_AES256_CBC = "2.16.840.1.101.3.4.1.42";

  private static final String OID_HMAC_SHA1 = "1.2.840.113549.2.7";
  private static final String OID_HMAC_SHA224 = "1.2.840.113549.2.8";
  private static final String OID_HMAC_SHA256 = "1.2.840.113549.2.9";
  private static final String OID_HMAC_SHA384 = "1.2.840.113549.2.10";
  private static final String OID_HMAC_SHA512 = "1.2.840.113549.2.11";

  private Pkcs12CertificateReader() {
  }

  public static KeyStore readCertificates(Path path, char[] password) throws IOException, GeneralSecurityException {
    var pfxBytes = Files.readAllBytes(path);
    var certificates = parsePfx(pfxBytes, password);
    var keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(null, null);
    var index = 0;
    for (X509Certificate certificate : certificates) {
      keyStore.setCertificateEntry("cert-" + index, certificate);
      index++;
    }
    return keyStore;
  }

  private static List<X509Certificate> parsePfx(byte[] pfxBytes, char[] password) {
    var pfxFields = new DerReader(pfxBytes).readValue().asReader().readAll();
    if (pfxFields.size() < 2) {
      throw new Pkcs12ParsingException("Malformed PFX: expected at least version and authSafe fields");
    }
    var authSafe = readContentInfo(pfxFields.get(1));
    if (!OID_DATA.equals(authSafe.contentType())) {
      throw new Pkcs12ParsingException("Unsupported PFX authSafe content type: " + authSafe.contentType());
    }
    var authenticatedSafe = authSafe.content().asReader().readValue().contentBytes();

    var certificates = new ArrayList<X509Certificate>();
    for (DerValue contentInfoValue : new DerReader(authenticatedSafe).readValue().asReader().readAll()) {
      var safeContentsDer = readSafeContents(contentInfoValue, password);
      certificates.addAll(parseSafeContents(safeContentsDer));
    }
    return certificates;
  }

  private static byte[] readSafeContents(DerValue contentInfoValue, char[] password) {
    var contentInfo = readContentInfo(contentInfoValue);
    if (OID_DATA.equals(contentInfo.contentType())) {
      return contentInfo.content().asReader().readValue().contentBytes();
    }
    if (OID_ENCRYPTED_DATA.equals(contentInfo.contentType())) {
      return readEncryptedSafeContents(contentInfo.content(), password);
    }
    throw new Pkcs12ParsingException("Unsupported AuthenticatedSafe content type: " + contentInfo.contentType());
  }

  private static byte[] readEncryptedSafeContents(DerValue explicitContent, char[] password) {
    var encryptedDataFields = explicitContent.asReader().readValue().asReader().readAll();
    var encryptedContentInfoFields = encryptedDataFields.get(1).asReader().readAll();
    var contentEncryptionAlgorithm = encryptedContentInfoFields.get(1).asReader().readAll();
    var algorithmOid = contentEncryptionAlgorithm.get(0).asObjectIdentifier();
    var algorithmParameters = contentEncryptionAlgorithm.size() > 1 ? contentEncryptionAlgorithm.get(1) : null;
    var encryptedContent = encryptedContentInfoFields.get(2).contentBytes();
    return decrypt(algorithmOid, algorithmParameters, encryptedContent, password);
  }

  private static byte[] decrypt(String algorithmOid, @Nullable DerValue algorithmParameters, byte[] encryptedContent, char[] password) {
    if (algorithmParameters == null) {
      throw new Pkcs12ParsingException("Missing encryption parameters for algorithm: " + algorithmOid);
    }
    try {
      switch (algorithmOid) {
        case OID_PBES2:
          return decryptPbes2(algorithmParameters, encryptedContent, password);
        case OID_PBE_SHA1_RC2_40:
          return decryptPbes1(algorithmParameters, encryptedContent, password, "PBEWithSHA1AndRC2_40");
        case OID_PBE_SHA1_3DES:
          return decryptPbes1(algorithmParameters, encryptedContent, password, "PBEWithSHA1AndDESede");
        default:
          throw new Pkcs12ParsingException("Unsupported PKCS12 encryption algorithm: " + algorithmOid);
      }

    } catch (GeneralSecurityException e) {
      throw new Pkcs12ParsingException("Unable to decrypt PKCS12 content with algorithm " + algorithmOid + " (wrong password?)", e);
    }
  }

  private static byte[] decryptPbes1(DerValue parameters, byte[] encryptedContent, char[] password, String algorithmName) throws GeneralSecurityException {
    var fields = parameters.asReader().readAll();
    var salt = fields.get(0).contentBytes();
    var iterationCount = fields.get(1).asInteger().intValueExact();
    var pbeParameterSpec = new PBEParameterSpec(salt, iterationCount);
    var keyFactory = SecretKeyFactory.getInstance(algorithmName);
    var key = keyFactory.generateSecret(new PBEKeySpec(password));
    var cipher = Cipher.getInstance(algorithmName);
    cipher.init(Cipher.DECRYPT_MODE, key, pbeParameterSpec);
    return cipher.doFinal(encryptedContent);
  }

  private static byte[] decryptPbes2(DerValue parameters, byte[] encryptedContent, char[] password) throws GeneralSecurityException {
    var pbes2Fields = parameters.asReader().readAll();
    var keyDerivationFunc = pbes2Fields.get(0).asReader().readAll();
    var encryptionScheme = pbes2Fields.get(1).asReader().readAll();

    var kdfOid = keyDerivationFunc.get(0).asObjectIdentifier();
    if (!OID_PBKDF2.equals(kdfOid)) {
      throw new Pkcs12ParsingException("Unsupported PBES2 key derivation function: " + kdfOid);
    }
    var pbkdf2Params = keyDerivationFunc.get(1).asReader().readAll();
    var salt = pbkdf2Params.get(0).contentBytes();
    var iterationCount = pbkdf2Params.get(1).asInteger().intValueExact();
    var pbkdf2Extras = readPbkdf2Extras(pbkdf2Params);

    var cipherOid = encryptionScheme.get(0).asObjectIdentifier();
    var iv = encryptionScheme.get(1).contentBytes();
    var cipherKeyLengthBits = pbkdf2Extras.keyLengthBits() != null ? pbkdf2Extras.keyLengthBits() : aesKeyLengthBitsForOid(cipherOid);

    var keyFactory = SecretKeyFactory.getInstance("PBKDF2With" + pbkdf2Extras.hmacAlgorithm());
    var derivedKey = keyFactory.generateSecret(new PBEKeySpec(password, salt, iterationCount, cipherKeyLengthBits));
    var secretKey = new SecretKeySpec(derivedKey.getEncoded(), "AES");

    var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
    return cipher.doFinal(encryptedContent);
  }

  private static Pbkdf2Extras readPbkdf2Extras(List<DerValue> pbkdf2Params) {
    Integer keyLengthBits = null;
    var hmacAlgorithm = "HmacSHA1";
    for (var i = 2; i < pbkdf2Params.size(); i++) {
      var field = pbkdf2Params.get(i);
      if (field.tag() == DerReader.TAG_INTEGER) {
        keyLengthBits = field.asInteger().intValueExact() * 8;
      } else if (field.tag() == DerReader.TAG_SEQUENCE) {
        hmacAlgorithm = hmacAlgorithmForOid(field.asReader().readAll().get(0).asObjectIdentifier());
      }
    }
    return new Pbkdf2Extras(keyLengthBits, hmacAlgorithm);
  }

  private static final class Pbkdf2Extras {
    private final Integer keyLengthBits;
    private final String hmacAlgorithm;

    private Pbkdf2Extras(@Nullable Integer keyLengthBits, String hmacAlgorithm) {
      this.keyLengthBits = keyLengthBits;
      this.hmacAlgorithm = hmacAlgorithm;
    }

    @CheckForNull
    private Integer keyLengthBits() {
      return keyLengthBits;
    }

    private String hmacAlgorithm() {
      return hmacAlgorithm;
    }
  }

  private static String hmacAlgorithmForOid(String oid) {
    switch (oid) {
      case OID_HMAC_SHA1:
        return "HmacSHA1";
      case OID_HMAC_SHA224:
        return "HmacSHA224";
      case OID_HMAC_SHA256:
        return "HmacSHA256";
      case OID_HMAC_SHA384:
        return "HmacSHA384";
      case OID_HMAC_SHA512:
        return "HmacSHA512";
      default:
        throw new Pkcs12ParsingException("Unsupported PBKDF2 pseudorandom function: " + oid);
    }
  }

  private static int aesKeyLengthBitsForOid(String oid) {
    switch (oid) {
      case OID_AES128_CBC:
        return 128;
      case OID_AES192_CBC:
        return 192;
      case OID_AES256_CBC:
        return 256;
      default:
        throw new Pkcs12ParsingException("Unsupported PBES2 encryption scheme: " + oid);
    }
  }

  private static List<X509Certificate> parseSafeContents(byte[] safeContentsDer) {
    var certificates = new ArrayList<X509Certificate>();
    for (DerValue safeBagValue : new DerReader(safeContentsDer).readValue().asReader().readAll()) {
      var safeBagFields = safeBagValue.asReader().readAll();
      var bagId = safeBagFields.get(0).asObjectIdentifier();
      if (OID_CERT_BAG.equals(bagId)) {
        parseCertBag(safeBagFields.get(1)).ifPresent(certificates::add);
      }
    }
    return certificates;
  }

  private static Optional<X509Certificate> parseCertBag(DerValue explicitBagValue) {
    var certBagFields = explicitBagValue.asReader().readValue().asReader().readAll();
    var certType = certBagFields.get(0).asObjectIdentifier();
    if (!OID_X509_CERTIFICATE.equals(certType)) {
      return Optional.empty();
    }
    var certValue = certBagFields.get(1).asReader().readValue();
    return Optional.of(parseCertificate(certValue.contentBytes()));
  }

  private static X509Certificate parseCertificate(byte[] derBytes) {
    try {
      var certificateFactory = CertificateFactory.getInstance("X.509");
      return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(derBytes));
    } catch (CertificateException e) {
      throw new Pkcs12ParsingException("Unable to parse X.509 certificate from PKCS12 CertBag", e);
    }
  }

  private static ContentInfo readContentInfo(DerValue contentInfoValue) {
    var fields = contentInfoValue.asReader().readAll();
    var contentType = fields.get(0).asObjectIdentifier();
    if (fields.size() < 2) {
      throw new Pkcs12ParsingException("Malformed ContentInfo: missing content field");
    }
    return new ContentInfo(contentType, fields.get(1));
  }

  private static final class ContentInfo {
    private final String contentType;
    private final DerValue content;

    private ContentInfo(String contentType, DerValue content) {
      this.contentType = contentType;
      this.content = content;
    }

    private String contentType() {
      return contentType;
    }

    private DerValue content() {
      return content;
    }
  }

}
