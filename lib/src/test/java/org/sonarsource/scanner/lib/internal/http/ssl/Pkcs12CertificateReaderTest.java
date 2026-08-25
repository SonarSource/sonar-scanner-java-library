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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * These tests exercise {@link Pkcs12CertificateReader}, which delegates to BouncyCastle's
 * {@code PKCS12KeyStoreSpi.DefPKCS12KeyStore} directly (bypassing {@code BouncyCastleProvider}
 * registration entirely). BC's own alias bookkeeping only exposes a certificate-only bag when it
 * carries a {@code friendlyName} or {@code localKeyId} attribute — a narrower form of leniency than
 * the JDK's, and narrower than a from-scratch reader that indexes every bag unconditionally. Several
 * tests below (marked accordingly) exist specifically to document that boundary.
 */
class Pkcs12CertificateReaderTest {

  private static final String OID_DATA = "1.2.840.113549.1.7.1";
  private static final String OID_ENCRYPTED_DATA = "1.2.840.113549.1.7.6";
  private static final String OID_CERT_BAG = "1.2.840.113549.1.12.10.1.3";
  private static final String OID_X509_CERTIFICATE = "1.2.840.113549.1.9.22.1";
  private static final String OID_FRIENDLY_NAME = "1.2.840.113549.1.9.20";
  private static final String OID_PBES2 = "1.2.840.113549.1.5.13";
  private static final String OID_PBKDF2 = "1.2.840.113549.1.5.12";
  private static final String OID_PBE_SHA1_RC2_40 = "1.2.840.113549.1.12.1.6";
  private static final String OID_PBE_SHA1_3DES = "1.2.840.113549.1.12.1.3";
  private static final String OID_AES128_CBC = "2.16.840.1.101.3.4.1.2";
  private static final String OID_AES192_CBC = "2.16.840.1.101.3.4.1.22";
  private static final String OID_AES256_CBC = "2.16.840.1.101.3.4.1.42";

  @TempDir
  private Path tempDir;

  @Test
  void extracts_certificate_from_openssl_cert_only_pkcs12() throws Exception {
    KeyStore keyStore = Pkcs12CertificateReader.readCertificates(resource("truststore-openssl-cert-only.p12"), "pwdOpenssl12".toCharArray());

    assertThat(keyStore.size()).isOne();
    X509Certificate certificate = (X509Certificate) keyStore.getCertificate("localhost");
    assertThat(certificate.getSubjectX500Principal().getName()).contains("CN=localhost");
    assertThat(certificate.getSerialNumber().toString(16)).isEqualToIgnoringCase("2eba3116e3d6c8d3a242d0153d2fd7145317a7aa");
  }

  @Test
  void known_limitation_unnamed_cert_bags_are_not_exposed_by_bc() throws Exception {
    // truststore-openssl-multi.p12 was generated without -caname: neither cert bag carries a
    // friendlyName/localKeyId, so BC's own PKCS12KeyStoreSpi never adds them to its alias map
    // (certs.put(alias, cert) only runs when an alias was found). A from-scratch reader that
    // indexes bags positionally would have returned both certificates here.
    KeyStore keyStore = Pkcs12CertificateReader.readCertificates(resource("truststore-openssl-multi.p12"), "pwdMulti12".toCharArray());

    assertThat(keyStore.size()).isZero();
  }

  @Test
  void supports_multiple_named_cert_bags_in_one_pkcs12_file() throws Exception {
    var ca = loadCertificate("ca.crt");
    var server = loadCertificate("server.pem");
    Path path = writePfx(buildPbes2PfxWithNamedCerts("test-password".toCharArray(), namedCertBag(ca, "ca-alias"), namedCertBag(server, "server-alias")));

    KeyStore keyStore = Pkcs12CertificateReader.readCertificates(path, "test-password".toCharArray());

    assertThat(keyStore.size()).isEqualTo(2);
    assertThat(((X509Certificate) keyStore.getCertificate("ca-alias")).getSubjectX500Principal().getName()).contains("CN=localhost");
    assertThat(((X509Certificate) keyStore.getCertificate("server-alias")).getSubjectX500Principal().getName()).contains("CN=localhost");
  }

  @Test
  void supports_legacy_pbes1_rc2_encryption() throws Exception {
    KeyStore keyStore = Pkcs12CertificateReader.readCertificates(resource("truststore-legacy-rc2.p12"), "pwdLegacy12".toCharArray());

    assertThat(keyStore.size()).isOne();
    X509Certificate certificate = (X509Certificate) keyStore.getCertificate("ca");
    assertThat(certificate.getSerialNumber().toString(16)).isEqualToIgnoringCase("43897e8ee04b232c24694caf77ede17605a5343e");
  }

  @Test
  void supports_unencrypted_data_contentInfo() throws Exception {
    KeyStore keyStore = Pkcs12CertificateReader.readCertificates(resource("keystore_emptypwd.p12"), new char[0]);

    assertThat(keyStore.size()).isGreaterThan(100);
  }

  @Test
  void ignores_private_key_bags_and_extracts_only_certificates() throws Exception {
    KeyStore keyStore = Pkcs12CertificateReader.readCertificates(resource("client.p12"), "pwdClientCertP12".toCharArray());

    assertThat(keyStore.size()).isOne();
    X509Certificate certificate = (X509Certificate) keyStore.getCertificate("julienhenry");
    assertThat(certificate.getSubjectX500Principal().getName()).contains("CN=Julien Henry");
  }

  @Test
  void throws_clear_exception_on_wrong_password() {
    Path path = resource("truststore-openssl-cert-only.p12");
    var wrongPassword = "wrongpassword".toCharArray();

    assertThatThrownBy(() -> Pkcs12CertificateReader.readCertificates(path, wrongPassword))
      .isInstanceOf(IOException.class)
      .hasMessageContaining("wrong password");
  }

  @Test
  void throws_clear_exception_on_corrupt_file() {
    Path path = resource("truststore-corrupt.p12");
    var password = "anything".toCharArray();

    assertThatThrownBy(() -> Pkcs12CertificateReader.readCertificates(path, password))
      .isInstanceOf(IOException.class);
  }

  @Test
  void throws_when_pfx_is_missing_the_authSafe_field() throws Exception {
    Path path = writePfx(DerBuilder.sequence(DerBuilder.integer(3)));
    var password = "pwd".toCharArray();

    assertThatThrownBy(() -> Pkcs12CertificateReader.readCertificates(path, password))
      .isInstanceOf(IOException.class);
  }

  @Test
  void throws_when_authSafe_content_type_is_not_data() throws Exception {
    var authSafeContentInfo = DerBuilder.sequence(DerBuilder.oid(OID_ENCRYPTED_DATA), DerBuilder.explicit(0, DerBuilder.octetString(new byte[0])));
    Path path = writePfx(pfxWithAuthSafeContentInfo(authSafeContentInfo));
    var password = "pwd".toCharArray();

    assertThatThrownBy(() -> Pkcs12CertificateReader.readCertificates(path, password))
      .isInstanceOf(IOException.class);
  }

  @Test
  void throws_when_authSafe_content_info_is_missing_the_content_field() throws Exception {
    var authSafeContentInfo = DerBuilder.sequence(DerBuilder.oid(OID_DATA));
    Path path = writePfx(pfxWithAuthSafeContentInfo(authSafeContentInfo));
    var password = "pwd".toCharArray();

    assertThatThrownBy(() -> Pkcs12CertificateReader.readCertificates(path, password))
      .isInstanceOf(IOException.class)
      .hasMessageContaining("ContentInfo content missing");
  }

  @Test
  void throws_when_authenticatedSafe_content_info_has_unsupported_content_type() throws Exception {
    var bogusInnerContentInfo = DerBuilder.sequence(DerBuilder.oid("1.2.3.4"), DerBuilder.explicit(0, DerBuilder.octetString(new byte[0])));
    Path path = writePfx(pfxWithAuthenticatedSafe(bogusInnerContentInfo));
    var password = "pwd".toCharArray();

    // BC logs and ignores the unrecognized ContentInfo rather than rejecting the structure outright;
    // the file still fails to load because it then contains no usable safe contents at all.
    assertThatThrownBy(() -> Pkcs12CertificateReader.readCertificates(path, password))
      .isInstanceOf(IOException.class);
  }

  @Test
  void throws_when_encryption_algorithm_is_missing_parameters() throws Exception {
    var algorithmWithoutParams = DerBuilder.sequence(DerBuilder.oid(OID_PBES2));
    Path path = writePfx(pfxWithEncryptedContentInfo(algorithmWithoutParams, new byte[]{1, 2, 3}));
    var password = "pwd".toCharArray();

    assertThatThrownBy(() -> Pkcs12CertificateReader.readCertificates(path, password))
      .isInstanceOf(IOException.class);
  }

  @Test
  void throws_when_encryption_algorithm_is_unsupported() throws Exception {
    var dummyParams = DerBuilder.sequence(DerBuilder.octetString(new byte[]{1}), DerBuilder.integer(1));
    var unsupportedAlgorithm = DerBuilder.sequence(DerBuilder.oid("1.2.840.113549.1.12.1.1"), dummyParams);
    Path path = writePfx(pfxWithEncryptedContentInfo(unsupportedAlgorithm, new byte[]{1, 2, 3, 4}));
    var password = "pwd".toCharArray();

    assertThatThrownBy(() -> Pkcs12CertificateReader.readCertificates(path, password))
      .isInstanceOf(IOException.class);
  }

  @Test
  void throws_when_pbes2_key_derivation_function_is_not_pbkdf2() throws Exception {
    var bogusKdfAlgId = DerBuilder.sequence(DerBuilder.oid(OID_PBE_SHA1_RC2_40), DerBuilder.sequence(DerBuilder.octetString(new byte[]{1}), DerBuilder.integer(1)));
    var dummyEncSchemeAlgId = DerBuilder.sequence(DerBuilder.oid(OID_AES256_CBC), DerBuilder.octetString(new byte[16]));
    var pbes2Params = DerBuilder.sequence(bogusKdfAlgId, dummyEncSchemeAlgId);
    var contentEncryptionAlgorithm = DerBuilder.sequence(DerBuilder.oid(OID_PBES2), pbes2Params);
    Path path = writePfx(pfxWithEncryptedContentInfo(contentEncryptionAlgorithm, new byte[]{1, 2, 3, 4}));
    var password = "pwd".toCharArray();

    assertThatThrownBy(() -> Pkcs12CertificateReader.readCertificates(path, password))
      .isInstanceOf(IOException.class);
  }

  @Test
  void throws_when_pbes2_pseudorandom_function_is_unsupported() throws Exception {
    var pbkdf2Params = DerBuilder.sequence(DerBuilder.octetString(new byte[8]), DerBuilder.integer(2048), DerBuilder.sequence(DerBuilder.oid("1.2.840.113549.2.99")));
    var kdfAlgId = DerBuilder.sequence(DerBuilder.oid(OID_PBKDF2), pbkdf2Params);
    var encSchemeAlgId = DerBuilder.sequence(DerBuilder.oid(OID_AES256_CBC), DerBuilder.octetString(new byte[16]));
    var pbes2Params = DerBuilder.sequence(kdfAlgId, encSchemeAlgId);
    var contentEncryptionAlgorithm = DerBuilder.sequence(DerBuilder.oid(OID_PBES2), pbes2Params);
    Path path = writePfx(pfxWithEncryptedContentInfo(contentEncryptionAlgorithm, new byte[]{1, 2, 3, 4}));
    var password = "pwd".toCharArray();

    assertThatThrownBy(() -> Pkcs12CertificateReader.readCertificates(path, password))
      .isInstanceOf(IOException.class)
      .hasMessageContaining("unknown PRF algorithm");
  }

  @Test
  void throws_when_pbes2_encryption_scheme_is_unsupported() throws Exception {
    var pbkdf2Params = DerBuilder.sequence(DerBuilder.octetString(new byte[8]), DerBuilder.integer(2048));
    var kdfAlgId = DerBuilder.sequence(DerBuilder.oid(OID_PBKDF2), pbkdf2Params);
    var encSchemeAlgId = DerBuilder.sequence(DerBuilder.oid("1.3.14.3.2.7"), DerBuilder.octetString(new byte[8]));
    var pbes2Params = DerBuilder.sequence(kdfAlgId, encSchemeAlgId);
    var contentEncryptionAlgorithm = DerBuilder.sequence(DerBuilder.oid(OID_PBES2), pbes2Params);
    Path path = writePfx(pfxWithEncryptedContentInfo(contentEncryptionAlgorithm, new byte[]{1, 2, 3, 4}));
    var password = "pwd".toCharArray();

    assertThatThrownBy(() -> Pkcs12CertificateReader.readCertificates(path, password))
      .isInstanceOf(IOException.class);
  }

  @Test
  void throws_when_certBag_has_non_x509_cert_type() throws Exception {
    // Unlike a from-scratch reader that could choose to silently skip a non-X.509 CertBag,
    // BC's own PKCS12KeyStoreSpi rejects the whole file outright.
    var certBag = DerBuilder.sequence(DerBuilder.oid("1.2.840.113549.1.9.22.2"), DerBuilder.explicit(0, DerBuilder.octetString(new byte[]{9, 9, 9})));
    var safeBag = DerBuilder.sequence(DerBuilder.oid(OID_CERT_BAG), DerBuilder.explicit(0, certBag), friendlyNameAttrSet("named"));
    var safeContents = DerBuilder.sequence(safeBag);
    Path path = writePfx(pfxWithAuthenticatedSafe(dataContentInfo(safeContents)));
    var password = "pwd".toCharArray();

    assertThatThrownBy(() -> Pkcs12CertificateReader.readCertificates(path, password))
      .isInstanceOf(IOException.class)
      .hasMessageContaining("Unsupported certificate type");
  }

  @Test
  void throws_when_certBag_contains_invalid_certificate_bytes() throws Exception {
    var certBag = DerBuilder.sequence(DerBuilder.oid(OID_X509_CERTIFICATE), DerBuilder.explicit(0, DerBuilder.octetString(new byte[]{0x00, 0x01, 0x02})));
    var safeBag = DerBuilder.sequence(DerBuilder.oid(OID_CERT_BAG), DerBuilder.explicit(0, certBag), friendlyNameAttrSet("named"));
    var safeContents = DerBuilder.sequence(safeBag);
    Path path = writePfx(pfxWithAuthenticatedSafe(dataContentInfo(safeContents)));
    var password = "pwd".toCharArray();

    assertThatThrownBy(() -> Pkcs12CertificateReader.readCertificates(path, password))
      .isInstanceOf(IOException.class)
      .hasMessageContaining("Could not parse certificate");
  }

  @Test
  void supports_pbes1_triple_des_encryption() throws Exception {
    var cert = loadCertificate("ca.crt");
    Path path = writePfx(buildPbes1Pfx(cert, "test-password".toCharArray(), OID_PBE_SHA1_3DES, "PBEWithSHA1AndDESede"));

    KeyStore keyStore = Pkcs12CertificateReader.readCertificates(path, "test-password".toCharArray());

    assertThat(keyStore.size()).isOne();
  }

  @Test
  void supports_pbes2_with_explicit_key_length() throws Exception {
    var cert = loadCertificate("ca.crt");
    Path path = writePfx(buildPbes2Pfx(cert, "test-password".toCharArray(), "HmacSHA256", "1.2.840.113549.2.9", 256, OID_AES256_CBC, true));

    KeyStore keyStore = Pkcs12CertificateReader.readCertificates(path, "test-password".toCharArray());

    assertThat(keyStore.size()).isOne();
  }

  @ParameterizedTest
  @CsvSource({
    "HmacSHA1,   1.2.840.113549.2.7",
    "HmacSHA224, 1.2.840.113549.2.8",
    "HmacSHA256, 1.2.840.113549.2.9",
    "HmacSHA384, 1.2.840.113549.2.10",
    "HmacSHA512, 1.2.840.113549.2.11"})
  void supports_all_pbkdf2_pseudorandom_functions(String hmacJavaName, String prfOid) throws Exception {
    var cert = loadCertificate("ca.crt");
    Path path = writePfx(buildPbes2Pfx(cert, "test-password".toCharArray(), hmacJavaName, prfOid, 256, OID_AES256_CBC, false));

    KeyStore keyStore = Pkcs12CertificateReader.readCertificates(path, "test-password".toCharArray());

    assertThat(keyStore.size()).isOne();
  }

  @ParameterizedTest
  @ValueSource(ints = {128, 192, 256})
  void supports_all_pbes2_aes_key_lengths(int keyLengthBits) throws Exception {
    var cert = loadCertificate("ca.crt");
    Path path = writePfx(buildPbes2Pfx(cert, "test-password".toCharArray(), "HmacSHA256", "1.2.840.113549.2.9", keyLengthBits, aesOidForKeyLength(keyLengthBits), false));

    KeyStore keyStore = Pkcs12CertificateReader.readCertificates(path, "test-password".toCharArray());

    assertThat(keyStore.size()).isOne();
  }

  private static String aesOidForKeyLength(int bits) {
    if (bits == 128) {
      return OID_AES128_CBC;
    }
    if (bits == 192) {
      return OID_AES192_CBC;
    }
    return OID_AES256_CBC;
  }

  private static X509Certificate loadCertificate(String resourceName) throws Exception {
    try (var in = Files.newInputStream(resource(resourceName))) {
      return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
    }
  }

  private static byte[] friendlyNameAttrSet(String name) {
    var bmpBytes = new byte[name.length() * 2];
    for (var i = 0; i < name.length(); i++) {
      bmpBytes[i * 2] = 0;
      bmpBytes[i * 2 + 1] = (byte) name.charAt(i);
    }
    var bmpString = DerBuilder.tlv(0x1E, bmpBytes);
    var attrValues = DerBuilder.tlv(0x31, bmpString);
    var attribute = DerBuilder.sequence(DerBuilder.oid(OID_FRIENDLY_NAME), attrValues);
    return DerBuilder.tlv(0x31, attribute);
  }

  private static byte[] namedCertBag(X509Certificate cert, String alias) throws Exception {
    var certBag = DerBuilder.sequence(DerBuilder.oid(OID_X509_CERTIFICATE), DerBuilder.explicit(0, DerBuilder.octetString(cert.getEncoded())));
    return DerBuilder.sequence(DerBuilder.oid(OID_CERT_BAG), DerBuilder.explicit(0, certBag), friendlyNameAttrSet(alias));
  }

  private static byte[] certBagSafeContents(X509Certificate cert) throws Exception {
    return DerBuilder.sequence(namedCertBag(cert, "test-cert"));
  }

  private static byte[] dataContentInfo(byte[] rawContentBytes) {
    return DerBuilder.sequence(DerBuilder.oid(OID_DATA), DerBuilder.explicit(0, DerBuilder.octetString(rawContentBytes)));
  }

  private static byte[] authenticatedSafe(byte[]... contentInfos) {
    return DerBuilder.sequence(contentInfos);
  }

  private static byte[] pfxWithAuthSafeContentInfo(byte[] authSafeContentInfo) {
    return DerBuilder.sequence(DerBuilder.integer(3), authSafeContentInfo);
  }

  private static byte[] pfxWithAuthenticatedSafe(byte[]... contentInfos) {
    return pfxWithAuthSafeContentInfo(dataContentInfo(authenticatedSafe(contentInfos)));
  }

  private static byte[] pfxWithEncryptedContentInfo(byte[] contentEncryptionAlgorithm, byte[] encryptedContent) {
    var encryptedContentInfo = DerBuilder.sequence(DerBuilder.oid(OID_DATA), contentEncryptionAlgorithm, DerBuilder.implicitPrimitive(0, encryptedContent));
    var encryptedDataSeq = DerBuilder.sequence(DerBuilder.integer(0), encryptedContentInfo);
    var innerContentInfo = DerBuilder.sequence(DerBuilder.oid(OID_ENCRYPTED_DATA), DerBuilder.explicit(0, encryptedDataSeq));
    return pfxWithAuthenticatedSafe(innerContentInfo);
  }

  private static byte[] buildPbes1Pfx(X509Certificate cert, char[] password, String algorithmOid, String algorithmName) throws Exception {
    var safeContents = certBagSafeContents(cert);
    var salt = new byte[8];
    new SecureRandom().nextBytes(salt);
    var iterationCount = 2048;

    var keyFactory = SecretKeyFactory.getInstance(algorithmName);
    var key = keyFactory.generateSecret(new PBEKeySpec(password));
    var cipher = Cipher.getInstance(algorithmName);
    cipher.init(Cipher.ENCRYPT_MODE, key, new PBEParameterSpec(salt, iterationCount));
    var ciphertext = cipher.doFinal(safeContents);

    var pbeParams = DerBuilder.sequence(DerBuilder.octetString(salt), DerBuilder.integer(iterationCount));
    var contentEncryptionAlgorithm = DerBuilder.sequence(DerBuilder.oid(algorithmOid), pbeParams);
    return pfxWithEncryptedContentInfo(contentEncryptionAlgorithm, ciphertext);
  }

  private static byte[] buildPbes2Pfx(X509Certificate cert, char[] password, String hmacJavaName, String prfOid, int keyLengthBits, String cipherOid,
    boolean includeExplicitKeyLength) throws Exception {
    return buildPbes2PfxForSafeContents(certBagSafeContents(cert), password, hmacJavaName, prfOid, keyLengthBits, cipherOid, includeExplicitKeyLength);
  }

  private static byte[] buildPbes2PfxWithNamedCerts(char[] password, byte[]... namedCertBags) throws Exception {
    var safeContents = DerBuilder.sequence(namedCertBags);
    return buildPbes2PfxForSafeContents(safeContents, password, "HmacSHA256", "1.2.840.113549.2.9", 256, OID_AES256_CBC, false);
  }

  private static byte[] buildPbes2PfxForSafeContents(byte[] safeContents, char[] password, String hmacJavaName, String prfOid, int keyLengthBits, String cipherOid,
    boolean includeExplicitKeyLength) throws Exception {
    var salt = new byte[8];
    var iv = new byte[16];
    var random = new SecureRandom();
    random.nextBytes(salt);
    random.nextBytes(iv);
    var iterationCount = 2048;

    var keyFactory = SecretKeyFactory.getInstance("PBKDF2With" + hmacJavaName);
    var derivedKey = keyFactory.generateSecret(new PBEKeySpec(password, salt, iterationCount, keyLengthBits));
    var secretKey = new SecretKeySpec(derivedKey.getEncoded(), "AES");
    var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));
    var ciphertext = cipher.doFinal(safeContents);

    List<byte[]> pbkdf2ParamsChildren = new ArrayList<>();
    pbkdf2ParamsChildren.add(DerBuilder.octetString(salt));
    pbkdf2ParamsChildren.add(DerBuilder.integer(iterationCount));
    if (includeExplicitKeyLength) {
      pbkdf2ParamsChildren.add(DerBuilder.integer(keyLengthBits / 8));
    }
    pbkdf2ParamsChildren.add(DerBuilder.sequence(DerBuilder.oid(prfOid)));
    var pbkdf2Params = DerBuilder.sequence(pbkdf2ParamsChildren.toArray(new byte[0][]));
    var kdfAlgId = DerBuilder.sequence(DerBuilder.oid(OID_PBKDF2), pbkdf2Params);
    var encSchemeAlgId = DerBuilder.sequence(DerBuilder.oid(cipherOid), DerBuilder.octetString(iv));
    var pbes2Params = DerBuilder.sequence(kdfAlgId, encSchemeAlgId);
    var contentEncryptionAlgorithm = DerBuilder.sequence(DerBuilder.oid(OID_PBES2), pbes2Params);
    return pfxWithEncryptedContentInfo(contentEncryptionAlgorithm, ciphertext);
  }

  private Path writePfx(byte[] bytes) throws Exception {
    var path = tempDir.resolve("test.p12");
    Files.write(path, bytes);
    return path;
  }

  private static Path resource(String name) {
    URL url = requireNonNull(Pkcs12CertificateReaderTest.class.getResource("/ssl/" + name));
    try {
      return Paths.get(url.toURI());
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

}
