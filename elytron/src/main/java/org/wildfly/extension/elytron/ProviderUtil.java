/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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

package org.wildfly.extension.elytron;

import java.security.Provider;

/**
 * A simple utility to search an array of {@link Provider} instances to identify the one to use.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ProviderUtil {

    /**
     * Utility method to search an array of candidate providers and identify the first provider that matches the required
     * criteria.
     *
     * @param candidates the array of candidate {@link Provider} instances to search.
     * @param name the expected name of the provider, can be {@code null} if the name is not important.
     * @param type the service type being searched for.
     * @param algorithm the algorithm the service needs to support.
     * @return the selected {@link Provider}
     * @throws NullPointerException if candidates, type or algorithm are {@code null}.
     */
    static Provider identifyProvider(Provider[] candidates, String name, Class<?> type, String algorithm) {
        String typeName = type.getSimpleName();
        for (Provider current : candidates) {
            if (name == null || name.equals(current.getName())) {
                if (current.getService(typeName, algorithm) != null) {
                    return current;
                }
            }
        }

        return null;
    }

}
