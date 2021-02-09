/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.manualmode.logging.module.api;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PropertyResolverFactory {

    private static final PrivilegedAction<PropertyResolver> ACTION = () -> {
        final ServiceLoader<PropertyResolver> loader = ServiceLoader.load(PropertyResolver.class);
        final Iterator<PropertyResolver> iter = loader.iterator();
        return iter.hasNext() ? iter.next() : null;
    };

    public static PropertyResolver newResolver() {
        if (System.getSecurityManager() == null) {
            return ACTION.run();
        }
        return AccessController.doPrivileged(ACTION);
    }
}
