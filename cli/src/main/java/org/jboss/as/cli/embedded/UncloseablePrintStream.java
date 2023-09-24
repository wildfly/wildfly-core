/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.embedded;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * A print stream that ignores the {@link #close} method.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
public class UncloseablePrintStream extends PrintStream {

    public UncloseablePrintStream(OutputStream out) {
        super(out);
    }

    @Override
    public void close() {
        // ignore
    }
}
