/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.ServiceName;

/**
 * The target of ServiceBuilder for capability installations.
 * CapabilityServiceBuilder to be installed on a target should be retrieved by calling {@link CapabilityServiceTarget#addService()}.
 * Notice that installation will only take place after {@link CapabilityServiceBuilder#install()} is invoked.
 * CapabilityServiceBuilder that are not installed are ignored.
 *
 * @author Tomaz Cerar (c) 2017 Red Hat Inc.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author Paul Ferraro
 */
public interface CapabilityServiceTarget extends RequirementServiceTarget {

    /**
     * Returns a builder for installing a service that provides a capability.
     *
     * @param capability the capability to be installed
     * @return new capability builder instance
     * @throws IllegalArgumentException if capability does not provide a service
     * @deprecated Use {@link #addService()} instead.
     */
    @Deprecated
    CapabilityServiceBuilder<?> addCapability(final RuntimeCapability<?> capability) throws IllegalArgumentException;

    @Override
    CapabilityServiceBuilder<?> addService();

    @Deprecated
    @Override
    CapabilityServiceBuilder<?> addService(ServiceName name);

    @Override
    CapabilityServiceTarget addListener(LifecycleListener listener);

    @Override
    CapabilityServiceTarget removeListener(LifecycleListener listener);

    @Override
    CapabilityServiceTarget subTarget();
}
