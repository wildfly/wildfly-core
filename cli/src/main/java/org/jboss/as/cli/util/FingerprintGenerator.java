/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.util;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.cli.CommandLineException;
import org.wildfly.common.iteration.ByteIterator;

public class FingerprintGenerator {

    private static final String[] FINGERPRINT_ALGORITHMS = new String[] { "MD5", "SHA1" };

    public static Map<String, String> generateFingerprints(final X509Certificate cert) throws CommandLineException  {
        Map<String, String> fingerprints = new HashMap<String, String>(FINGERPRINT_ALGORITHMS.length);
        for (String current : FINGERPRINT_ALGORITHMS) {
            try {
                fingerprints.put(current, generateFingerPrint(current, cert.getEncoded()));
            } catch (GeneralSecurityException e) {
                throw new CommandLineException("Unable to generate fingerprint", e);
            }
        }

        return fingerprints;
    }

    private static String generateFingerPrint(final String algorithm, final byte[] cert) throws GeneralSecurityException {
        StringBuilder sb = new StringBuilder();

        MessageDigest md = MessageDigest.getInstance(algorithm);
        byte[] digested = md.digest(cert);
        String hex = ByteIterator.ofBytes(digested).hexEncode().drainToString();
        boolean started = false;
        for (int i = 0; i < hex.length() - 1; i += 2) {
            if (started) {
                sb.append(":");
            } else {
                started = true;
            }
            sb.append(hex.substring(i, i + 2));
        }

        return sb.toString();
    }

}
