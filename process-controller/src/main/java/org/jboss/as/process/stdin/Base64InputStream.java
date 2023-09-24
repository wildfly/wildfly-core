/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.process.stdin;

import java.io.InputStream;

/**
 * Variant of the <a href="http://commons.apache.org/proper/commons-codec">Commons Codec</a> project's class
 * of the same name. See {@link org.jboss.as.process.stdin.Base64} for an explanation of the rationale
 * for creating the variants in this package.
 * <p>
 * Provides Base64 encoding and decoding in a streaming fashion (unlimited size).
 * </p>
 * <p>
 * The behaviour of the Base64OutputStream is to ENCODE, whereas the  behaviour of the Base64InputStream
 * is to DECODE.
 * </p>
 * <p>
 * This class implements section <cite>6.8. Base64 Content-Transfer-Encoding</cite> from RFC 2045 <cite>Multipurpose
 * Internet Mail Extensions (MIME) Part One: Format of Internet Message Bodies</cite> by Freed and Borenstein.
 * </p>
 * <p>
 * Since this class operates directly on byte streams, and not character streams, it is hard-coded to only encode/decode
 * character encodings which are compatible with the lower 127 ASCII chart (ISO-8859-1, Windows-1252, UTF-8, etc).
 * </p>
 *
 * @see <a href="http://www.ietf.org/rfc/rfc2045.txt">RFC 2045</a>
 */
public class Base64InputStream extends BaseNCodecInputStream {

    /**
     * Creates a Base64InputStream such that all data read is Base64-decoded from the original
     * provided InputStream.
     *
     * @param in
     *            InputStream to wrap.
     *
     */
    public Base64InputStream(final InputStream in) {
        super(in, new Base64(), false);
    }
}
