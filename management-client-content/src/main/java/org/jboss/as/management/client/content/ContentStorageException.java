/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.management.client.content;

import java.io.IOException;

/**
 * {@link RuntimeException} to wrap IOExceptions thrown when manipulating managed DMR content.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ContentStorageException extends RuntimeException {

    public ContentStorageException(final IOException cause) {
        super(cause);
    }
}
