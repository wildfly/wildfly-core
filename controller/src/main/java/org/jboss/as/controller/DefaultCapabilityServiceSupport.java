/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.Optional;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.ImmutableCapabilityRegistry;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.msc.service.ServiceName;

/**
 * Default implementation of {@link CapabilityServiceSupport}.
 */
public final class DefaultCapabilityServiceSupport implements CapabilityServiceSupport {
    private final ImmutableCapabilityRegistry registry;

    /**
     * Creates a new DefaultCapabilityServiceSupport.
     * @param registry a capability registry. Cannot be {@code null}.
     */
    public DefaultCapabilityServiceSupport(ImmutableCapabilityRegistry registry) {
        checkNotNullParam("registry", registry);
        this.registry = registry;
    }

    @Override
    public boolean hasCapability(String capabilityName) {
        return registry.hasCapability(capabilityName, CapabilityScope.GLOBAL);
    }

    @Override
    public <T> T getCapabilityRuntimeAPI(String capabilityName, Class<T> apiType) throws NoSuchCapabilityException {
        try {
            return registry.getCapabilityRuntimeAPI(capabilityName, CapabilityScope.GLOBAL, apiType);
        } catch (IllegalStateException e) {
            throw new NoSuchCapabilityException(capabilityName);
        }
    }

    @Override
    public <T> T getCapabilityRuntimeAPI(String capabilityBaseName, String dynamicPart, Class<T> apiType) throws NoSuchCapabilityException {
        String fullName = RuntimeCapability.buildDynamicCapabilityName(capabilityBaseName, dynamicPart);
        return getCapabilityRuntimeAPI(fullName, apiType);
    }

    @Override
    public <T> Optional<T> getOptionalCapabilityRuntimeAPI(String capabilityName, Class<T> apiType) {
        try {
            return Optional.of(getCapabilityRuntimeAPI(capabilityName, apiType));
        } catch (NoSuchCapabilityException e) {
            return Optional.empty();
        }
    }

    @Override
    public <T> Optional<T> getOptionalCapabilityRuntimeAPI(String capabilityBaseName, String dynamicPart, Class<T> apiType) {
        try {
            return Optional.of(getCapabilityRuntimeAPI(capabilityBaseName, dynamicPart, apiType));
        } catch (NoSuchCapabilityException e) {
            return Optional.empty();
        }
    }

    @Override
    public ServiceName getCapabilityServiceName(String capabilityName) {
        try {
            return registry.getCapabilityServiceName(capabilityName, CapabilityScope.GLOBAL, null);
        } catch (IllegalStateException | IllegalArgumentException ignore) {
            // ignore
        }
        ControllerLogger.ROOT_LOGGER.debugf("CapabilityServiceSupport: Parsing ServiceName for %s", capabilityName);
        return ServiceNameFactory.parseServiceName(capabilityName);
    }

    @Override
    public ServiceName getCapabilityServiceName(String capabilityBaseName, String... dynamicPart) {
        ServiceName name = getCapabilityServiceName(capabilityBaseName);
        return (dynamicPart.length > 0) ? name.append(dynamicPart) : name;
    }
}
