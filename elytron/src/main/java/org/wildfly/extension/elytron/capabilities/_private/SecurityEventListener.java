/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron.capabilities._private;

import java.util.function.Consumer;

import org.wildfly.common.Assert;
import org.wildfly.security.auth.server.event.SecurityEvent;

/**
 * A {@link Consumer} that consumes {@link SecurityEvent} instances emmitted from domains.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface SecurityEventListener extends Consumer<SecurityEvent> {

    static SecurityEventListener from(Consumer<SecurityEvent> consumer) {
        return consumer::accept;
    }

    static SecurityEventListener aggregate(SecurityEventListener... listeners) {
        Assert.checkNotNullParam("listeners", listeners);
        final SecurityEventListener[] clone = listeners.clone();
        for (int i = 0; i < clone.length; i++) {
            Assert.checkNotNullArrayParam("listener", i, clone[i]);
        }
        return e -> {
            for (SecurityEventListener sel : clone) {
                sel.accept(e);
            }
        };
    }


}
