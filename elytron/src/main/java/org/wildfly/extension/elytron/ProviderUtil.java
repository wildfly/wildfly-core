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
import java.util.Arrays;
import java.util.Set;

import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

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
                    if (ROOT_LOGGER.isTraceEnabled()) {
                        ROOT_LOGGER.tracef("Identified provider [%s] for name [%s] and algorithm [%s] between %s",
                                current, name, algorithm, Arrays.toString(candidates));
                    }
                    return current;
                }
            }
        }

        if (ROOT_LOGGER.isTraceEnabled()) {
            ROOT_LOGGER.tracef("No provider identified for name [%s] and algorithm [%s] between %s",
                    name, algorithm, Arrays.toString(candidates));
        }
        return null;
    }

    static boolean isServiceTypeProvided(Provider[] candidates, Class<?> type) {
        String serviceType = type.getSimpleName();
        for (Provider current : candidates) {
            Set<Provider.Service> services = current.getServices();
            if (services != null) {
                for (Provider.Service currentService : services) {
                    if (serviceType.equals(currentService.getType())) {
                        if (ROOT_LOGGER.isTraceEnabled()) {
                            ROOT_LOGGER.tracef("Service [%s] provided for type [%s] between %s",
                                    currentService, serviceType, Arrays.toString(candidates));
                        }
                        return true;
                    }
                }
            }
        }

        if (ROOT_LOGGER.isTraceEnabled()) {
            ROOT_LOGGER.tracef("No service provided for type [%s] between %s",
                    serviceType, Arrays.toString(candidates));
        }
        return false;
    }

}
