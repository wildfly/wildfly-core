/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import org.jboss.as.controller.ResourceDescription;
import org.wildfly.discovery.spi.DiscoveryProvider;
import org.wildfly.subsystem.resource.ResourceModelResolver;
import org.wildfly.subsystem.service.ServiceDependency;

/**
 * Describes a discovery provider resource
 */
public interface DiscoveryProviderResourceDescription extends ResourceDescription, ResourceModelResolver<ServiceDependency<DiscoveryProvider>> {
}
