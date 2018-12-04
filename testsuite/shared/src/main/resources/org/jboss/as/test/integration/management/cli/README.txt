======= account.keystore =======
password: elytron
To read this keystore use following command:

$JAVA_HOME/bin/keytool -list -v -keystore account.keystore -storepass elytron

Keystore type: JKS
Keystore provider: SUN

Your keystore contains 1 entry

Alias name: account6
Creation date: Apr 27, 2018
Entry type: PrivateKeyEntry
Certificate chain length: 1
Certificate[1]:
Owner: CN=account6.key
Issuer: CN=account6.key
Serial number: 3c6fb23
Valid from: Fri Apr 27 20:27:24 CEST 2018 until: Mon Apr 24 20:27:24 CEST 2028
Certificate fingerprints:
	 SHA1: DA:1D:B7:63:C8:4B:B1:91:FE:85:60:0C:3F:D0:BB:A1:9E:74:E7:68
	 SHA256: D0:41:B9:DD:25:53:12:4A:24:BC:ED:07:38:91:45:B7:6F:AA:09:EC:5A:FA:02:B4:32:82:1F:07:B9:F8:0D:76
Signature algorithm name: SHA256withRSA
Subject Public Key Algorithm: 2048-bit RSA key
Version: 3

Extensions:

#1: ObjectId: 2.5.29.14 Criticality=false
SubjectKeyIdentifier [
KeyIdentifier [
0000: 95 64 E7 FB 03 34 E1 17   99 17 9D E8 5A D4 8C 35  .d...4......Z..5
0010: 04 AB 65 61                                        ..ea
]
]



*******************************************
*******************************************



======= test.keystore =======
password: secret

To read this keystore use following command:
$JAVA_HOME/bin/keytool -list -v -keystore test.keystore -storepass secret


Keystore type: JKS
Keystore provider: SUN

Your keystore contains 2 entries

Alias name: ca
Creation date: Nov 22, 2018
Entry type: PrivateKeyEntry
Certificate chain length: 1
Certificate[1]:
Owner: O=Root Certificate Authority, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA
Issuer: O=Root Certificate Authority, EMAILADDRESS=elytron@wildfly.org, C=UK, ST=Elytron, CN=Elytron CA
Serial number: aa35b786d5b23e3e
Valid from: Thu Nov 22 10:50:13 CET 2018 until: Sat Jan 01 00:59:59 CET 10000
Certificate fingerprints:
	 SHA1: 11:9F:97:F2:68:36:45:02:53:A7:1A:C0:4F:8F:CF:6D:AF:8D:D5:8E
	 SHA256: 61:12:96:5C:84:06:8A:BB:08:01:E9:6F:66:BA:EB:DF:58:E6:DF:66:94:DB:44:23:1B:76:4C:B6:2D:F1:78:88
Signature algorithm name: SHA1withDSA
Subject Public Key Algorithm: 1024-bit DSA key
Version: 3

Extensions:

#1: ObjectId: 2.5.29.19 Criticality=false
BasicConstraints:[
  CA:true
  PathLen:2147483647
]

#2: ObjectId: 2.5.29.14 Criticality=false
SubjectKeyIdentifier [
KeyIdentifier [
0000: E2 6B 49 5B BD 6D 9F 63   28 3D A3 28 A0 F9 6D FF  .kI[.m.c(=.(..m.
0010: 6F C7 A4 70                                        o..p
]
]



*******************************************
*******************************************


Alias name: ssmith
Creation date: Nov 22, 2018
Entry type: PrivateKeyEntry
Certificate chain length: 1
Certificate[1]:
Owner: CN=sally smith, OU=jboss, O=red hat, L=raleigh, ST=north carolina, C=us
Issuer: CN=sally smith, OU=jboss, O=red hat, L=raleigh, ST=north carolina, C=us
Serial number: 479110920912ae4f
Valid from: Thu Nov 22 10:50:13 CET 2018 until: Sat Jan 01 00:59:59 CET 10000
Certificate fingerprints:
	 SHA1: D0:4F:00:35:30:0A:85:F7:38:FD:96:3F:E8:44:A5:1B:18:1F:75:9A
	 SHA256: 27:76:F6:73:B4:04:A7:DD:97:67:BD:BD:34:EA:C5:8B:B0:50:E9:E7:5B:BD:46:94:77:20:9C:9F:87:9B:D7:E8
Signature algorithm name: SHA256withRSA
Subject Public Key Algorithm: 1024-bit RSA key
Version: 3

Extensions:

#1: ObjectId: 2.5.29.37 Criticality=false
ExtendedKeyUsages [
  clientAuth
]

#2: ObjectId: 2.5.29.15 Criticality=true
KeyUsage [
  DigitalSignature
]

#3: ObjectId: 2.5.29.17 Criticality=false
SubjectAlternativeName [
  RFC822Name: sallysmith@example.com
  DNSName: sallysmith.example.com
]

#4: ObjectId: 2.5.29.14 Criticality=false
SubjectKeyIdentifier [
KeyIdentifier [
0000: 0E 14 85 8B E2 F3 A9 12   E4 BB 44 AC FE DD 81 93  ..........D.....
0010: 5E 7D 3C 37                                        ^.<7
]
]



*******************************************
*******************************************
