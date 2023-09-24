/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.process.protocol;

import org.jboss.as.process.logging.ProcessLogger;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class providing methods for common management tasks.
 *
 * @author John Bailey
 */
public final class ProtocolUtils {

    private ProtocolUtils() {
        // forbidden instantiation
    }

    public static void expectHeader(final InputStream input, final int expected) throws IOException {
        expectHeader(StreamUtils.readByte(input), expected);
    }

    public static void expectHeader(final DataInput input, final int expected) throws IOException {
        expectHeader(input.readByte(), expected);
    }

    public static void expectHeader(final byte actual, final int expected) throws IOException {
        if (actual != (byte) expected) {
            throw ProcessLogger.ROOT_LOGGER.invalidByteToken(expected, actual);
        }
    }

}
