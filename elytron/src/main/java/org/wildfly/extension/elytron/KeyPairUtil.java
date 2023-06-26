/*
 * Copyright 2022 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.wildfly.common.iteration.CodePointIterator;
import org.wildfly.security.auth.util.ElytronFilePasswordProvider;
import org.wildfly.security.pem.Pem;
import org.wildfly.security.ssh.util.SshUtil;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

class KeyPairUtil {

    public static final String RSA_ALGORITHM = "RSA";
    public static final String DSA_ALGORITHM = "DSA";
    public static final String EC_ALGORITHM = "EC";

    public static KeyPair generateKeyPair(String algorithm, int size) throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator;

        switch (algorithm) {
            case RSA_ALGORITHM: {
                /* Size must range from 512 to 16384. Default size: 2048
                 * see: https://docs.oracle.com/en/java/javase/11/security/oracle-providers.html#GUID-7093246A-31A3-4304-AC5F-5FB6400405E2
                 */
                size = (512 <= size && size <= 16384) ? size : 2048;
                break;
            }
            case DSA_ALGORITHM: {
                /* Size must be multiple of 64 ranging from 512 to 1024, plus 2048 and 3072. Default size: 2048
                 * see: https://docs.oracle.com/en/java/javase/11/security/oracle-providers.html#GUID-3A80CC46-91E1-4E47-AC51-CB7B782CEA7D
                 */
                size = (512 <= size && size <= 1024 && (size % 64) == 0) || size == 2048  || size == 3072 ? size : 2048;
                break;
            }
            case EC_ALGORITHM: {
                /* Size must range from 112 to 571. Default size: 256
                 * see: https://docs.oracle.com/en/java/javase/11/security/oracle-providers.html#GUID-091BF58C-82AB-4C9C-850F-1660824D5254
                 */
                size = (112 <= size && size <= 571) ? size : 256;
                break;
            }
            default: {
                algorithm = RSA_ALGORITHM;
                size = 2048;
                break;
            }
        }

        try {
            keyPairGenerator = KeyPairGenerator.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw ROOT_LOGGER.unknownKeyPairAlgorithm(algorithm);
        }
        try {
            keyPairGenerator.initialize(size, new SecureRandom());
        } catch (InvalidParameterException e) {
            throw ROOT_LOGGER.invalidKeyPairKeySize(e.getMessage());
        }
        return keyPairGenerator.generateKeyPair();
    }

    public static KeyPair parseKeyPair(String privateKeyContent, String publicKeyContent) {
        if (publicKeyContent == null || publicKeyContent.isEmpty()) {
            throw ROOT_LOGGER.noPublicKeySpecified();
        }
        PrivateKey privateKey = Pem.parsePemContent(CodePointIterator.ofString(privateKeyContent)).next().tryCast(PrivateKey.class);
        if (privateKey == null) throw ROOT_LOGGER.xmlNoPemContent();
        PublicKey publicKey = Pem.parsePemContent(CodePointIterator.ofString(publicKeyContent)).next().tryCast(PublicKey.class);
        if (publicKey == null) throw ROOT_LOGGER.xmlNoPemContent();
        KeyPair keyPair = new KeyPair(publicKey, privateKey);

        return keyPair;
    }

    public static KeyPair parseOpensshKeyPair(String privateKeyContent, ElytronFilePasswordProvider passwordProvider) {
        KeyPair keyPair = SshUtil.parsePemOpenSSHContent(CodePointIterator.ofString(privateKeyContent), passwordProvider).next().tryCast(KeyPair.class);
        if (keyPair == null) throw ROOT_LOGGER.xmlNoPemContent();

        return keyPair;
    }

    public static String exportPublicKey(PublicKey publicKey) {
        return PublicKeyEntry.toString(publicKey);
    }

    public static String readKeyFromFile(String keyLocation) {
        File keyFile = new File(keyLocation);
        FileInputStream stream = null;
        byte[] keyData = null;
        try {
            stream = new FileInputStream(keyFile);
            keyData = new byte[stream.available()];
            stream.read(keyData, 0, stream.available());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            safeClose(stream);
        }
        return new String(keyData);
    }

    private static void safeClose(Closeable c) {
        if (c != null) try {
            c.close();
        } catch (Throwable ignored) {}
    }
}

