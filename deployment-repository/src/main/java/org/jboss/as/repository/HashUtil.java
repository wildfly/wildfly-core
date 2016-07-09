/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.repository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Vector;
import java.util.stream.Collectors;
import org.jboss.as.repository.logging.DeploymentRepositoryLogger;

/**
 * Utilities related to deployment content hashes.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class HashUtil {

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

    public static boolean isEachHexHashInTable(String s) {
        // WFLY-6018, check each char is in table, otherwise, there will be StringIndexOutOfBoundsException due to illegal char
        char[] array = s.toCharArray();
        for (char c : array) {
            if (Arrays.binarySearch(table, c) < 0)
                return false;
        }
        return true;
    }

    public static byte[] hashContent(MessageDigest messageDigest, InputStream stream) throws IOException {
        messageDigest.reset();
        try (DigestInputStream dis = new DigestInputStream(stream, messageDigest)) {
            byte[] bytes = new byte[8192];
            while (dis.read(bytes) > -1) {
            }
        }
        return messageDigest.digest();
    }

    /**
     * Hashes a path, if the path points to a directory then hashes the contents recursively.
     * @param messageDigest the digest used to hash.
     * @param path the file/directory we want to hash.
     * @return the resulting hash.
     * @throws IOException
     */
    public static byte[] hashPath(MessageDigest messageDigest, Path path) throws IOException {
        try (InputStream in = getRecursiveContentStream(path)) {
            return hashContent(messageDigest, in);
        }
    }

    private static InputStream getRecursiveContentStream(Path path) {
        if (Files.isRegularFile(path)) {
            try {
                return new SequenceInputStream(new ByteArrayInputStream(path.getFileName().toString().getBytes(StandardCharsets.UTF_8)), Files.newInputStream(path));
            } catch (IOException ex) {
                throw DeploymentRepositoryLogger.ROOT_LOGGER.hashingError(ex, path);
            }
        } else if (Files.isDirectory(path)) {
            try {
                Vector<InputStream> v = new Vector<>();
                v.add(new ByteArrayInputStream(path.getFileName().toString().getBytes(StandardCharsets.UTF_8)));
                v.addAll(Files.list(path).sorted((Path path1, Path path2) -> path1.compareTo(path2)).map(p -> getRecursiveContentStream(p)).collect(Collectors.toList()));
                return new SequenceInputStream(v.elements());
            } catch (IOException ex) {
                throw DeploymentRepositoryLogger.ROOT_LOGGER.hashingError(ex, path);
            }
        }
        return emptyStream();
    }

    /**
     * Create an empty non-null) stream.
     * @return an empty non-null stream.
     */
    public static InputStream emptyStream() {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                return -1;
            }
        };
    }
}
