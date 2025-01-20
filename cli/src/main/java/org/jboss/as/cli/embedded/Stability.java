/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.embedded;

/**
 * Counterpart to the server-side {@code org.jboss.as.version.Stability}.
 * We cannot depend on the server-side type in the strictly client-side CLI.
 * <p/>
 * <strong>This must only be used for tab completion suggestions. The CLI does not
 * control the valid stability levels for a server, so if this class is out of sync
 * with what a server allows, the only acceptable effect is incorrect suggestions.</strong>
 * The alternative to this restriction is to not suggest values at all and remove this type.
 */
enum Stability {

    DEFAULT("default"),
    COMMUNITY("community"),
    PREVIEW("preview"),
    EXPERIMENTAL("experimental"),
    ;
    private final String value;

    Stability(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }
}
