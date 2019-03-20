#Command used to generate id_ec_test
$ ssh-keygen -t ecdsa
Generating public/private ecdsa key pair.
Enter file in which to save the key (/home/user/.ssh/id_ecdsa):
Enter passphrase (empty for no passphrase):
Enter same passphrase again:
Your identification has been saved in /home/user/.ssh/id_ecdsa.
Your public key has been saved in /home/user/.ssh/id_ecdsa.pub.

#Command used to generate RSA key
$ ssh-keygen -t rsa
Generating public/private rsa key pair.
Enter file in which to save the key (/home/user/.ssh/id_rsa):
Enter passphrase (empty for no passphrase):
Enter same passphrase again:
Your identification has been saved in /home/user/.ssh/id_rsa.
Your public key has been saved in /home/user/.ssh/id_rsa.pub.

#Command used to generate ecdsa key in pkcs format:
$ ssh-keygen -t ecdsa -m pkcs8
Generating public/private ecdsa key pair.
Enter file in which to save the key (/home/user/.ssh/id_ecdsa): id_ecdsa_pkcs
Enter passphrase (empty for no passphrase):
Enter same passphrase again:
Your identification has been saved in id_ecdsa_pkcs.
Your public key has been saved in id_ecdsa_pkcs.pub.

#Command to convert public key to pkcs8 format:
$ ssh-keygen -f id_ecdsa_pkcs.pub -e -m pkcs8
-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAET5mdxyyOin5CLI2Qoqs16AwYvffz
7kSn0ffu4YWucVo+TT0sHJsSvFQrAs3tQw6csJKm4dCE0Pqb087n19HvfQ==
-----END PUBLIC KEY-----

