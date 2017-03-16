/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
