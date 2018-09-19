## Let's create TLS certificates

The most common format of certificates are PEM, so let's generate them instead of using Java keytool (that can also generate keys in JKS format).

This README, is a *simplified* version for generating the certificates only for test's purposes. 

**DO NOT USE IT FOR PRODUCTION**

### Generation of a TLS server certificate

In this example the configuration of OpenSSL is entirely in openssl.conf (a stripped version of openssl.cnf that may vary from distribution to distribution)

#### First let's create a Certificate Authority

The Certificate Authority is a private key that is used to sign other X.509 certificates in order to validate the ownership of a website (trusted tier).

```bash
$ openssl genrsa -out ca.key 4096
.....++
................................................................................................................................................++
e is 65537 (0x010001)
```

Now we have our key to sign other certificates : `ca.key` in PEM format.

Let's create our X.509 CA certificate :

```bash
openssl req -key ca.key -new -x509 -days 3650 -sha256 -extensions ca_extensions -out ca.crt -config ./openssl.conf
You are about to be asked to enter information that will be incorporated
into your certificate request.
What you are about to enter is what is called a Distinguished Name or a DN.
There are quite a few fields but you can leave some blank
For some fields there will be a default value,
If you enter '.', the field will be left blank.
-----
Country Name (2 letter code) [AU]:CH
State or Province Name (full name) [Some-State]:Geneva
Locality Name (eg, city) []:Geneva
Organization Name (eg, company) [Internet Widgits Pty Ltd]:SonarSource SA
Organizational Unit Name (eg, section) []:
Common Name (e.g. server FQDN or YOUR name) []:SonarSource
Email Address []:
```

There is no important values here.

#### Let's create a self signed certificate our TLS server using our CA

We want to create a X.509 certificate for our https server. This certificate will be a Certificate Signing Request. A certificate that need to be signed by a trusted tier.
The default configuration is set in `openssl.conf` and it has been configuration for `localhost`.
The most important part is the `Common Name` and `DNS.1` (set in `openssl.conf`).

So just keep using enter with this commande line :

```bash
$ openssl req -new -keyout server.key -out server.csr -nodes -newkey rsa:4096 -config ./openssl.conf
  Generating a 4096 bit RSA private key
  ........................................................................++
  .........................................................................................++
  writing new private key to 'server.key'
  -----
  You are about to be asked to enter information that will be incorporated
  into your certificate request.
  What you are about to enter is what is called a Distinguished Name or a DN.
  There are quite a few fields but you can leave some blank
  For some fields there will be a default value,
  If you enter '.', the field will be left blank.
  -----
  Country Name (2-letter code) [CH]:
  State or Province Name (full name) [Geneva]:
  Locality (e.g. city name) [Geneva]:
  Organization (e.g. company name) [SonarSource SA]:
  Common Name (your.domain.com) [localhost]:
```

No we have `server.csr` file valid for 10 years.
Let's see what's in this certificate :
```bash
$ openssl req -verify -in server.csr -text -noout
verify OK
Certificate Request:
    Data:
        Version: 1 (0x0)
        Subject: C = CH, ST = Geneva, L = Geneva, O = SonarSource SA, CN = localhost
        Subject Public Key Info:
            Public Key Algorithm: rsaEncryption
                Public-Key: (4096 bit)
                Modulus:
                    00:b2:8d:7e:74:e3:42:ec:24:bf:09:14:9b:18:9c:
                    3b:06:01:5e:54:30:31:40:bb:a3:64:f6:3a:99:ea:
                    07:db:d6:7d:a6:c9:6c:1e:73:c7:83:76:b2:0e:bd:
                    e8:c2:92:f8:fc:0e:a4:95:7d:1c:a3:69:fc:03:b6:
                    41:dd:2b:89:eb:51:5a:32:2d:4f:09:1f:55:a9:bb:
                    b0:82:e2:36:a0:85:5d:d9:40:62:bf:a8:ae:23:1d:
                    ad:c0:ae:75:d2:e6:42:5d:92:06:61:fc:05:b1:12:
                    8a:b2:40:b5:aa:29:82:05:ee:3b:67:23:ac:5b:1c:
                    73:b5:15:d5:e7:e5:7f:51:ce:b0:55:37:6e:bc:57:
                    44:12:e2:14:9f:46:9c:90:b2:1f:94:b9:5d:72:2a:
                    f6:1e:c0:2e:94:3f:11:a0:c5:b7:68:b9:ba:d5:6d:
                    b5:47:3e:9b:2f:ea:57:a3:03:7e:9c:8c:dd:56:70:
                    ae:a7:9e:7b:93:73:f7:68:f0:81:94:24:db:de:8b:
                    49:5c:0e:57:c6:4f:32:fd:b1:32:6d:34:44:a1:16:
                    1a:ba:f0:c3:37:e9:e1:08:84:37:44:ce:48:fb:e6:
                    6f:3f:6a:7a:4c:ba:69:38:5e:38:0c:83:f5:c0:a3:
                    0b:17:01:45:cf:53:f7:e9:fe:29:29:fb:e4:a8:73:
                    1c:37:41:af:d0:d9:53:1f:c1:bb:a1:40:71:43:12:
                    a3:7d:b8:31:0b:53:4e:4e:89:b6:36:78:bc:a7:c4:
                    ce:50:53:35:87:27:7a:7b:33:d0:3d:86:4b:29:ff:
                    8e:73:62:81:6b:f4:c8:67:30:58:fa:fd:0e:ac:7f:
                    c4:63:0f:4d:41:c8:a8:b7:6f:88:9c:3c:d5:a8:dc:
                    20:40:ae:19:8c:a4:a9:0c:22:b1:7c:af:30:56:be:
                    63:30:50:35:d3:82:f1:a6:5d:4a:c8:9d:5f:73:94:
                    6f:2c:98:00:dc:df:51:88:d5:d7:e9:4b:36:71:f2:
                    61:aa:30:58:f7:01:12:2d:7d:24:45:70:ef:63:f1:
                    cb:4a:ab:4c:b1:f2:59:97:02:ca:68:98:68:d4:0f:
                    7e:bd:fe:a3:16:f4:43:f0:45:2e:7e:2f:6a:37:7d:
                    e6:e9:a6:9b:d1:1b:02:5d:43:da:a0:49:12:6d:94:
                    6e:b5:e0:b8:8e:51:88:4b:1b:3f:38:7e:be:9e:86:
                    06:09:19:c7:d1:4e:2f:d9:9e:50:ed:5a:b5:0a:27:
                    48:28:da:ce:34:9e:df:38:31:c9:b7:6c:80:df:18:
                    1b:ba:76:14:ff:75:2d:0f:0c:2f:c3:7f:6d:2f:51:
                    6a:42:bb:59:c5:de:81:f1:04:22:8a:fa:ed:94:f4:
                    bb:ab:b1
                Exponent: 65537 (0x10001)
        Attributes:
        Requested Extensions:
            X509v3 Subject Alternative Name: 
                DNS:localhost
    Signature Algorithm: sha256WithRSAEncryption
         10:cc:1d:6b:13:54:81:64:b4:1f:1e:73:ec:fd:78:af:b9:e6:
         39:e1:8c:32:74:b1:f9:2b:87:d6:9e:30:d5:bb:db:72:d0:4d:
         d8:92:b8:9b:c5:d5:46:a0:6b:f7:a9:bc:da:81:f5:19:04:0f:
         69:4b:20:79:bf:ac:f3:92:4d:2f:28:76:d8:f6:22:de:be:0c:
         02:51:c8:7d:11:d2:64:94:fe:4f:5e:5f:a0:c9:4f:4d:3c:4e:
         0a:02:42:a7:8d:e9:fe:79:7e:e3:c0:1f:56:0e:33:ca:0a:0c:
         d8:19:29:52:44:f5:47:a2:f0:28:7b:0c:cc:dd:68:b9:c2:d4:
         1b:b9:03:58:d1:d5:75:9f:b5:3d:de:1e:8c:54:9e:2f:e7:71:
         ec:09:9e:bc:f0:d6:1e:e1:2d:b0:ef:03:26:2c:7c:7e:bb:5c:
         4f:2f:35:40:e2:2b:46:7e:99:4e:fe:6a:9a:ff:2b:f9:ff:06:
         a3:94:b6:57:92:f2:b3:8b:b2:4b:de:7c:bc:16:dd:39:91:05:
         79:27:1d:c3:a2:ba:c3:c7:4c:c4:39:c9:f3:4d:a1:a3:7f:37:
         b2:e4:f6:29:40:cb:8a:1a:bc:74:4c:ed:e5:6b:cc:73:f7:65:
         59:df:6a:23:52:9a:0b:71:7c:84:11:d7:1f:d8:ad:d0:5e:49:
         8a:b3:f4:e6:eb:2e:b2:d8:1e:3b:0e:12:04:7b:c4:3e:2b:cb:
         c8:3e:7b:fc:55:ba:53:da:50:fc:c6:aa:1c:8d:7a:3f:91:d9:
         ab:63:99:b3:45:82:c2:8a:fc:35:13:a4:91:ec:2f:28:d1:2b:
         01:e9:93:a3:a1:4a:ad:68:75:42:60:87:90:63:6f:a6:a2:bb:
         ef:39:f5:49:93:4b:26:74:01:15:e6:4d:7a:fb:af:a6:15:76:
         c8:f3:58:67:99:29:96:bb:bd:3f:c0:a8:a2:1c:26:31:d7:55:
         7f:17:c6:8f:b9:b1:f0:92:bf:da:a8:fd:ab:d3:66:d9:30:25:
         d4:ce:bb:74:ea:e0:fe:6f:64:74:72:cd:23:a9:22:63:d6:ae:
         45:06:66:4d:26:1d:77:4c:25:4c:98:99:6f:9c:32:a3:c2:43:
         7e:e4:21:d5:cb:67:60:a4:1e:67:b7:90:80:38:2a:02:f1:84:
         29:12:8d:f6:3c:e5:58:d1:bd:50:69:cc:af:e8:c4:6c:1b:87:
         70:ae:8a:a6:cb:25:06:e9:5d:b2:ca:23:75:f3:ae:57:74:a8:
         1b:66:90:1c:81:5e:a6:97:a5:96:89:88:3f:c6:ff:d0:63:5d:
         16:7b:53:51:cd:25:7a:71:a0:11:74:ed:e6:9b:0f:ac:e7:10:
         4e:2c:a9:ac:86:97:cf:83
```

#### Let's sign this certificate with our own CA

The CSR will be signed with our previously created ca.key
We'll sign it to be valid for 10years (3650)

```bash
$ openssl x509 -req -days 3650 -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out server.pem -sha256
Signature ok
subject=C = CH, ST = Geneva, L = Geneva, O = SonarSource SA, CN = localhost
Getting CA Private Key
```

Let's verify what are in this certificate :

```bash
$ openssl x509 -in server.pem -text -noout
Certificate:
    Data:
        Version: 1 (0x0)
        Serial Number:
            d5:c5:2a:c2:c8:f6:43:bb
    Signature Algorithm: sha256WithRSAEncryption
        Issuer: C = CH, ST = Geneva, L = Geneva, O = SonarSource SA, CN = SonarSource
        Validity
            Not Before: Sep 14 13:51:02 2018 GMT
            Not After : Sep 11 13:51:02 2028 GMT
        Subject: C = CH, ST = Geneva, L = Geneva, O = SonarSource SA, CN = localhost
        Subject Public Key Info:
            Public Key Algorithm: rsaEncryption
                Public-Key: (4096 bit)
                Modulus:
                    00:b2:8d:7e:74:e3:42:ec:24:bf:09:14:9b:18:9c:
                    3b:06:01:5e:54:30:31:40:bb:a3:64:f6:3a:99:ea:
                    07:db:d6:7d:a6:c9:6c:1e:73:c7:83:76:b2:0e:bd:
                    e8:c2:92:f8:fc:0e:a4:95:7d:1c:a3:69:fc:03:b6:
                    41:dd:2b:89:eb:51:5a:32:2d:4f:09:1f:55:a9:bb:
                    b0:82:e2:36:a0:85:5d:d9:40:62:bf:a8:ae:23:1d:
                    ad:c0:ae:75:d2:e6:42:5d:92:06:61:fc:05:b1:12:
                    8a:b2:40:b5:aa:29:82:05:ee:3b:67:23:ac:5b:1c:
                    73:b5:15:d5:e7:e5:7f:51:ce:b0:55:37:6e:bc:57:
                    44:12:e2:14:9f:46:9c:90:b2:1f:94:b9:5d:72:2a:
                    f6:1e:c0:2e:94:3f:11:a0:c5:b7:68:b9:ba:d5:6d:
                    b5:47:3e:9b:2f:ea:57:a3:03:7e:9c:8c:dd:56:70:
                    ae:a7:9e:7b:93:73:f7:68:f0:81:94:24:db:de:8b:
                    49:5c:0e:57:c6:4f:32:fd:b1:32:6d:34:44:a1:16:
                    1a:ba:f0:c3:37:e9:e1:08:84:37:44:ce:48:fb:e6:
                    6f:3f:6a:7a:4c:ba:69:38:5e:38:0c:83:f5:c0:a3:
                    0b:17:01:45:cf:53:f7:e9:fe:29:29:fb:e4:a8:73:
                    1c:37:41:af:d0:d9:53:1f:c1:bb:a1:40:71:43:12:
                    a3:7d:b8:31:0b:53:4e:4e:89:b6:36:78:bc:a7:c4:
                    ce:50:53:35:87:27:7a:7b:33:d0:3d:86:4b:29:ff:
                    8e:73:62:81:6b:f4:c8:67:30:58:fa:fd:0e:ac:7f:
                    c4:63:0f:4d:41:c8:a8:b7:6f:88:9c:3c:d5:a8:dc:
                    20:40:ae:19:8c:a4:a9:0c:22:b1:7c:af:30:56:be:
                    63:30:50:35:d3:82:f1:a6:5d:4a:c8:9d:5f:73:94:
                    6f:2c:98:00:dc:df:51:88:d5:d7:e9:4b:36:71:f2:
                    61:aa:30:58:f7:01:12:2d:7d:24:45:70:ef:63:f1:
                    cb:4a:ab:4c:b1:f2:59:97:02:ca:68:98:68:d4:0f:
                    7e:bd:fe:a3:16:f4:43:f0:45:2e:7e:2f:6a:37:7d:
                    e6:e9:a6:9b:d1:1b:02:5d:43:da:a0:49:12:6d:94:
                    6e:b5:e0:b8:8e:51:88:4b:1b:3f:38:7e:be:9e:86:
                    06:09:19:c7:d1:4e:2f:d9:9e:50:ed:5a:b5:0a:27:
                    48:28:da:ce:34:9e:df:38:31:c9:b7:6c:80:df:18:
                    1b:ba:76:14:ff:75:2d:0f:0c:2f:c3:7f:6d:2f:51:
                    6a:42:bb:59:c5:de:81:f1:04:22:8a:fa:ed:94:f4:
                    bb:ab:b1
                Exponent: 65537 (0x10001)
    Signature Algorithm: sha256WithRSAEncryption
         0a:0a:c0:cb:4c:79:b2:13:45:34:e4:61:20:3d:3a:ce:44:31:
         2c:ff:04:f2:42:ac:62:6e:f9:a3:3d:76:6a:5a:16:a8:d9:a2:
         ab:53:fd:b0:44:0b:e5:a2:79:19:b4:8e:3b:c1:bf:c1:84:ab:
         1a:aa:a0:7c:09:a9:88:8f:29:5b:64:14:2c:29:56:5d:2b:59:
         5e:79:5e:d2:69:6e:e2:b0:54:2c:0b:7b:2a:bf:80:46:fe:d8:
         21:51:7e:cf:41:ad:9d:c2:93:6d:61:5e:b6:b3:7e:ed:bf:9b:
         a9:4b:31:f9:fe:ee:1f:a5:4c:07:9e:cc:30:39:de:c9:6e:5d:
         40:10:c2:21:95:23:14:82:d1:7b:fe:36:d0:70:15:88:dc:0f:
         58:17:33:b9:c5:ae:45:8e:73:3d:e7:de:c0:bd:f2:cf:96:15:
         5b:bb:08:d7:6a:33:60:ee:26:03:ad:e5:dd:e7:df:d4:37:b5:
         1f:87:83:a0:d3:5b:49:77:12:f2:52:3e:a0:6b:a1:f6:b1:62:
         76:eb:45:0d:96:28:3c:97:07:1a:21:ce:ff:22:5b:37:19:21:
         06:3a:fb:07:96:67:0e:a2:9f:29:0c:d8:6c:e2:2e:e7:20:11:
         4d:46:ae:33:ff:62:cf:fb:07:2e:a8:69:de:fd:5c:c3:fc:3b:
         a7:ef:4e:ce:bf:be:31:c4:c5:d2:6c:19:f8:52:87:ff:fe:ae:
         3d:20:de:26:e0:00:83:48:eb:e5:30:54:a2:26:1c:ab:09:9f:
         08:45:a6:16:81:35:cb:c9:6f:bc:1a:7c:15:8d:1f:a3:82:4d:
         ee:cf:04:7a:a3:56:3c:82:60:ab:35:3f:80:ed:07:2a:f1:40:
         10:1b:7c:3d:1c:53:a0:8c:33:7f:e4:6e:67:cb:cd:c6:4d:6b:
         11:63:57:b3:00:07:ef:2f:53:d7:81:9f:15:d2:29:e3:29:f7:
         9c:e2:e7:d2:0e:d5:71:a0:48:ef:aa:dd:4b:f2:36:2d:f4:9f:
         39:93:94:38:07:00:5e:61:bf:26:40:a4:78:27:b4:65:6f:e0:
         c5:31:a7:80:65:bc:47:5b:bf:a8:6a:65:0b:c7:9b:9b:ae:36:
         f3:13:ec:a9:3c:c8:2e:71:24:f7:a0:d3:d3:28:dc:5d:e8:c4:
         f7:98:52:c7:c5:ed:35:c0:36:e9:70:95:1a:f1:70:e2:b8:0b:
         22:c7:d7:27:15:10:eb:df:de:f0:7e:f8:ad:55:69:53:03:ed:
         bb:f2:de:4f:e2:47:1f:50:f5:3d:a2:fb:81:5c:7a:f8:77:fc:
         7c:0c:e3:4b:a2:53:99:6b:c9:7e:4f:ba:32:2f:56:8c:21:73:
         d1:39:24:b6:d6:f7:bc:17
```

#### Let's create a JKS file to be used for starting a TLS server

Before being able to import, we have to create a pkcs12 file (it's a standard format whereas JKS is proprietary, and we cannot import key and certificate directly with `keytool`).

```bash
$ openssl pkcs12 -export -in server.pem -inkey server.key -passout pass: -name localhost -out server.p12
Enter Export Password:
Verifying - Enter Export Password:
```

Do not enter any password, this is not necessary.

Then we need to convert it to JKS (note that pkcs12 files are the standard since Java 9) :
```bash
$ keytool -importkeystore -srcstorepass "" -deststorepass abcdef -destkeystore server.jks -srckeystore server.p12 -srcstoretype PKCS12
Importing keystore server.p12 to server.jks...
Entry for alias localhost successfully imported.
Import command completed:  1 entries successfully imported, 0 entries failed or cancelled

Warning:
The JKS keystore uses a proprietary format. It is recommended to migrate to PKCS12 which is an industry standard format using "keytool -importkeystore -srckeystore server.jks -destkeystore server.jks -deststoretype pkcs12".
```

The password of the JKS file is `abcdef`

The `server.jks` file can now be used to start a TLS server.

#### Now let's a client to connect to our TLS server

Since we've created a self signed certificate. The client must either have our certificate (without the private key) or must have the CA certificate.

##### Let's create a JKS with the server certificate

This one is more easier :

```bash
$ keytool -import -alias localhost -keystore client-with-certificate.jks -file server.pem 
  Enter keystore password:  
  Re-enter new password: 
  Owner: CN=localhost, O=SonarSource SA, L=Geneva, ST=Geneva, C=CH
  Issuer: CN=SonarSource, O=SonarSource SA, L=Geneva, ST=Geneva, C=CH
  Serial number: d5c52ac2c8f643bb
  Valid from: Fri Sep 14 15:51:02 CEST 2018 until: Mon Sep 11 15:51:02 CEST 2028
  Certificate fingerprints:
  	 MD5:  A5:36:AF:BD:50:26:49:1E:C2:E9:D8:40:FB:29:0D:9E
  	 SHA1: B3:E5:DF:FB:87:D6:42:4B:92:A4:9F:B9:10:A9:5C:26:57:D8:05:28
  	 SHA256: 69:2D:91:68:36:68:8A:35:1D:5E:CA:1C:A8:EE:F1:EA:33:35:5F:50:D8:2F:E0:36:37:9D:24:99:3A:1E:F4:BB
  Signature algorithm name: SHA256withRSA
  Subject Public Key Algorithm: 4096-bit RSA key
  Version: 1
  Trust this certificate? [no]:  yes
  Certificate was added to keystore
``` 

The password is again `abcdef` and you have to `Trust this certificate`.
This JKS file must be used in a TrustedKeyStore.

##### Let's create a JKS with CA certificate

```bash
$ keytool -import -trustcacerts -alias localhost -keystore client-with-ca.jks -file ca.crt
  Enter keystore password:  
  Re-enter new password: 
  Owner: CN=SonarSource, O=SonarSource SA, L=Geneva, ST=Geneva, C=CH
  Issuer: CN=SonarSource, O=SonarSource SA, L=Geneva, ST=Geneva, C=CH
  Serial number: c0196248f8364c3a
  Valid from: Fri Sep 14 15:49:39 CEST 2018 until: Mon Sep 11 15:49:39 CEST 2028
  Certificate fingerprints:
  	 MD5:  7D:4A:DB:BC:EC:FC:05:DD:AD:27:75:03:93:F4:B7:CA
  	 SHA1: 75:32:44:BD:56:97:71:12:46:F2:F6:B5:53:E3:1D:89:D8:9F:1A:C9
  	 SHA256: 2E:A3:1D:AF:62:46:23:96:ED:C9:E3:0E:EF:5D:6F:A9:D4:64:DE:C0:CE:7A:5A:92:31:4E:B8:20:DC:0E:16:2A
  Signature algorithm name: SHA256withRSA
  Subject Public Key Algorithm: 4096-bit RSA key
  Version: 3
  
  Extensions: 
  
  #1: ObjectId: 2.5.29.35 Criticality=false
  AuthorityKeyIdentifier [
  KeyIdentifier [
  0000: D8 26 73 57 2A 7B BA 5F   22 2B DD F7 2C DB 4D AA  .&sW*.._"+..,.M.
  0010: 53 6E 33 91                                        Sn3.
  ]
  ]
  
  #2: ObjectId: 2.5.29.19 Criticality=true
  BasicConstraints:[
    CA:true
    PathLen:2147483647
  ]
  
  #3: ObjectId: 2.5.29.14 Criticality=false
  SubjectKeyIdentifier [
  KeyIdentifier [
  0000: D8 26 73 57 2A 7B BA 5F   22 2B DD F7 2C DB 4D AA  .&sW*.._"+..,.M.
  0010: 53 6E 33 91                                        Sn3.
  ]
  ]
  
  Trust this certificate? [no]:  yes
  Certificate was added to keystore
``` 

This JKS file must be used in a TrustedKeyStore.

### Create a certificate that will be used to authenticate a user

The principle is the same we'll have a CA authority signing certificates that will be send by the user to the server.
In this case the server will have to host the CA authority in its TrustedKeyStore while the client will host his certificate in is KeyStore.
In this use case, the extensions are not the same so we'll use openssl-client-auth.conf

#### Generation of CA

One line to generate both the key `ca-lient-auth.key` and the CA certificate `ca-client-auth.crt`

```bash
openssl req -newkey rsa:4096 -nodes -keyout ca-client-auth.key -new -x509 -days 3650 -sha256 -extensions ca_extensions -out ca-client-auth.crt -subj '/C=CH/ST=Geneva/L=Geneva/O=SonarSource SA/CN=SonarSource/' -config ./openssl-client-auth.conf
Generating a 4096 bit RSA private key
...................................++
............................................................................................................................................................................................................................................................++
writing new private key to 'ca-client-auth.key'
-----

```

For the certificate, the Common Name is used to identify the user
```bash
$ openssl req -new -keyout client.key -out client.csr -nodes -newkey rsa:4096 -subj '/C=CH/ST=Geneva/L=Geneva/O=SonarSource SA/CN=Julien Henry/' -config ./openssl-client-auth.conf
Generating a 4096 bit RSA private key
..............................................++
................++
writing new private key to 'client.key'
-----
```

Let's sign this certificate 
```bash
$ openssl x509 -req -days 3650 -in client.csr -CA ca-client-auth.crt -CAkey ca-client-auth.key -CAcreateserial -out client.pem -sha256
Signature ok
subject=C = CH, ST = Geneva, L = Geneva, O = SonarSource SA, CN = Julien Henry
Getting CA Private Key
```

Let's create the pkcs12 certificate containing the client certificate

```bash
$ openssl pkcs12 -export -in client.pem -inkey client.key -passout pass: -name julienhenry -out client.p12
```

Transform it to JKS :

**It's important to set the destkeypass, as we are expecting in the code OkHttpClientFactory#getDefaultKeyManager to have the key stored with password identical to the password of the keystore.**
 
```bash
$ keytool -importkeystore -srcstorepass "" -deststorepass abcdef -destkeypass abcdef -destkeystore client.jks -srckeystore client.p12 -srcstoretype PKCS12
Importing keystore client.p12 to client.jks...
Entry for alias localhost successfully imported.
Import command completed:  1 entries successfully imported, 0 entries failed or cancelled

Warning:
The JKS keystore uses a proprietary format. It is recommended to migrate to PKCS12 which is an industry standard format using "keytool -importkeystore -srckeystore client.jks -destkeystore client.jks -deststoretype pkcs12".
```

This will go to client keyStore. 
Now we'll generate the `server-with-client-ca.jks` file that will have the CA certificate. Since we don't need to add the key of the certificate (only required to sign, not to verify), we can import it directly with keytool.

```bash
$ keytool -import -trustcacerts -alias client-ca -keystore server-with-client-ca.jks -file ca-client-auth.crt
Owner: CN=SonarSource, O=SonarSource SA, L=Geneva, ST=Geneva, C=CH
Issuer: CN=SonarSource, O=SonarSource SA, L=Geneva, ST=Geneva, C=CH
Serial number: ed8bcadd4888ffac
Valid from: Sat Sep 15 08:10:22 CEST 2018 until: Tue Sep 12 08:10:22 CEST 2028
Certificate fingerprints:
	 MD5:  25:38:06:14:D0:B3:36:81:65:FC:44:CA:E3:BA:57:12
	 SHA1: 77:56:EF:C7:2F:5A:29:D1:A0:54:5F:F8:B4:19:60:91:7B:71:E4:2C
	 SHA256: 1D:2D:E5:52:21:60:75:08:F3:0A:B3:93:CF:38:F6:30:88:56:28:73:20:BA:76:9A:C0:A1:D7:8C:4D:D3:84:AA
Signature algorithm name: SHA256withRSA
Subject Public Key Algorithm: 4096-bit RSA key
Version: 3

Extensions: 

#1: ObjectId: 2.5.29.35 Criticality=false
AuthorityKeyIdentifier [
KeyIdentifier [
0000: 87 B9 C1 23 E2 F1 A3 68   BD D6 44 99 0E AD FC FC  ...#...h..D.....
0010: A5 31 90 D4                                        .1..
]
]

#2: ObjectId: 2.5.29.19 Criticality=true
BasicConstraints:[
  CA:true
  PathLen:2147483647
]

#3: ObjectId: 2.5.29.37 Criticality=false
ExtendedKeyUsages [
  serverAuth
]

#4: ObjectId: 2.5.29.15 Criticality=false
KeyUsage [
  DigitalSignature
  Key_Encipherment
  Data_Encipherment
  Key_CertSign
  Crl_Sign
]

#5: ObjectId: 2.5.29.14 Criticality=false
SubjectKeyIdentifier [
KeyIdentifier [
0000: 87 B9 C1 23 E2 F1 A3 68   BD D6 44 99 0E AD FC FC  ...#...h..D.....
0010: A5 31 90 D4                                        .1..
]
]

Trust this certificate? [no]:  yes
Certificate was added to keystore

$ openssl pkcs12 -export -in client.pem -inkey client.key -passout pass: -name localhost -out client.p12

```
