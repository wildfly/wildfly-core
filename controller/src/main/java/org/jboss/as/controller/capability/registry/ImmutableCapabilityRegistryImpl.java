/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller.capability.registry;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.msc.service.ServiceName;

/**
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public class ImmutableCapabilityRegistryImpl implements ImmutableCapabilityRegistry {
    private final Map<CapabilityId, RuntimeCapabilityRegistration> capabilities;
    private final Map<CapabilityId, CapabilityRegistration> possibleCapabilities;
    private final boolean forServer;
    private final Set<CapabilityContext> knownContexts;
    private final CapabilityResolutionContext resolutionContext;

    public ImmutableCapabilityRegistryImpl(Map<CapabilityId, RuntimeCapabilityRegistration> capabilities,
                                           Map<CapabilityId, CapabilityRegistration> possibleCapabilities,
                                           boolean forServer, Set<CapabilityContext> knownContexts,
                                           CapabilityResolutionContext resolutionContext) {
        this.capabilities = capabilities;
        this.possibleCapabilities = possibleCapabilities;
        this.forServer = forServer;
        this.knownContexts = knownContexts;
        this.resolutionContext = resolutionContext;
    }

    @Override
    public synchronized boolean hasCapability(String capabilityName, String dependentName, CapabilityContext capabilityContext) {
        return findSatisfactoryCapability(capabilityName, capabilityContext, dependentName, !forServer) != null;
    }

    @Override
    public synchronized <T> T getCapabilityRuntimeAPI(String capabilityName, CapabilityContext capabilityContext, Class<T> apiType) {
        RuntimeCapabilityRegistration reg = getCapabilityRegistration(capabilityName, capabilityContext);
        Object api = reg.getCapability().getRuntimeAPI();
        if (api == null) {
            throw ControllerLogger.MGMT_OP_LOGGER.capabilityDoesNotExposeRuntimeAPI(capabilityName);
        }
        return apiType.cast(api);
    }

    @Override
    public ServiceName getCapabilityServiceName(String capabilityName, CapabilityContext context, Class<?> serviceType) {
        RuntimeCapabilityRegistration reg = getCapabilityRegistration(capabilityName, context);
        RuntimeCapability<?> cap = reg.getCapability();
        return cap.getCapabilityServiceName(serviceType);
    }

    @Override
    public Set<CapabilityRegistration> getCapabilities() {
        return Collections.unmodifiableSet(new HashSet<>(capabilities.values()));
    }

    public Set<RegistrationPoint> getPossibleProviderPoints(CapabilityId capabilityId) {
        CapabilityRegistration registration = possibleCapabilities.get(capabilityId);
        return registration.getRegistrationPoints();
    }

    @Override
    public Set<CapabilityRegistration> getPossibleCapabilities() {
        return Collections.unmodifiableSet(new HashSet<>(possibleCapabilities.values()));
    }


    private RuntimeCapabilityRegistration getCapabilityRegistration(String capabilityName, CapabilityContext capabilityContext) {
        SatisfactoryCapability satisfactoryCapability = findSatisfactoryCapability(capabilityName, capabilityContext, null, false);
        if (satisfactoryCapability == null) {
            if (forServer) {
                throw ControllerLogger.MGMT_OP_LOGGER.unknownCapability(capabilityName);
            } else {
                throw ControllerLogger.MGMT_OP_LOGGER.unknownCapabilityInContext(capabilityName, capabilityContext.getName());
            }
        }
        return capabilities.get(satisfactoryCapability.singleCapability);
    }

    private SatisfactoryCapability findSatisfactoryCapability(String capabilityName, CapabilityContext capabilityContext,
                                                              String dependentName, boolean requireConsistency) {

        // Check for a simple match
        CapabilityId requestedId = new CapabilityId(capabilityName, capabilityContext);
        if (capabilities.containsKey(requestedId)) {
            return new SatisfactoryCapability(requestedId);
        }

        if (!forServer) {
            // Try other contexts that satisfy the requested one
            Set<CapabilityContext> multiple = null;
            for (CapabilityContext satisfies : knownContexts) {
                if (satisfies.equals(capabilityContext)) {
                    // We already know this one doesn't exist
                    continue;
                }
                CapabilityId satisfiesId = new CapabilityId(capabilityName, satisfies);
                if (capabilities.containsKey(satisfiesId) && satisfies.canSatisfyRequirement(requestedId, dependentName, resolutionContext)) {
                    if (!requireConsistency || !satisfies.requiresConsistencyCheck()) {
                        return new SatisfactoryCapability(satisfiesId);
                    } else {
                        if (multiple == null) {
                            multiple = new HashSet<>();
                        }
                        multiple.add(satisfies);
                        multiple.addAll(satisfies.getIncludingContexts(resolutionContext));
                    }
                }
            }
            if (multiple != null) {
                return new SatisfactoryCapability(multiple);
            }
        }
        return null;
    }

    private static class SatisfactoryCapability {
        private final CapabilityId singleCapability;
        private final Set<CapabilityContext> multipleCapabilities;

        SatisfactoryCapability(CapabilityId singleCapability) {
            this.singleCapability = singleCapability;
            this.multipleCapabilities = null;
        }

        SatisfactoryCapability(Set<CapabilityContext> multipleCapabilities) {
            this.singleCapability = null;
            this.multipleCapabilities = multipleCapabilities;
        }
    }
}
