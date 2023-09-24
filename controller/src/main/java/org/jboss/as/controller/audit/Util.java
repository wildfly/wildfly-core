/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.audit;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author John Bailey
 */
final class Util {

    static final byte[] RECORD_HEADER = new byte[]{(byte) 0xEE, (byte) 0xFF, (byte) 0xEE, (byte) 0xFF};
    static final byte[] EMPTY_BYTES = new byte[0];

    static OutputStream NULL_OUTPUT_STREAM = new OutputStream() {
        public void write(int i) throws IOException {
        }
    };

}
