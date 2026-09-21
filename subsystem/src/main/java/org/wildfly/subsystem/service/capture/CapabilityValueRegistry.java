/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.service.capture;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RequirementServiceTarget;
import org.wildfly.service.capture.ServiceValueCaptor;
import org.wildfly.service.capture.ValueRegistry;
import org.wildfly.subsystem.service.ServiceInstaller;

/**
 * Registry of values provided by the services for a given capability.
 * @author Paul Ferraro
 * @param <V> the service value type
 */
public interface CapabilityValueRegistry<V> extends ValueRegistry<PathAddress, V>, ServiceValueCaptor<PathAddress, RequirementServiceTarget> {
    @Override
    ServiceInstaller capture(PathAddress address);
}
