/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.persistence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * TODO class javadoc.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class ExposedByteArrayOutputStream extends ByteArrayOutputStream {

    ExposedByteArrayOutputStream(int size) {
        super(size);
    }

    ByteArrayInputStream getInputStream() {
        return new ByteArrayInputStream(buf, 0, count);
    }

}
