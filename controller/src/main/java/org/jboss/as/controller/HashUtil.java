/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

/**
 * Utilities related to deployment content hashes.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class HashUtil {

    private HashUtil() {
    }

    private static char[] table = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    /**
     * Convert a byte array into a hex string.
     *
     * @param bytes the bytes
     * @return the string
     */
    public static String bytesToHexString(final byte[] bytes) {
        final StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(table[b >> 4 & 0x0f]).append(table[b & 0x0f]);
        }
        return builder.toString();
    }

    /**
     * Convert a hex string into a byte[].
     *
     * @param s the string
     * @return the bytes
     */
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len >> 1];
        for (int i = 0, j = 0; j < len; i++) {
            int x = Character.digit(s.charAt(j), 16) << 4;
            j++;
            x = x | Character.digit(s.charAt(j), 16);
            j++;
            data[i] = (byte) (x & 0xFF);
        }
        return data;
    }

}
