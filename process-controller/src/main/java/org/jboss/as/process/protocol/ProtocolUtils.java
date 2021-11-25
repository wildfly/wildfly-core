/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
