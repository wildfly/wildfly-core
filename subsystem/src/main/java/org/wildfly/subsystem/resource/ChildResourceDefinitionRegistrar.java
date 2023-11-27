/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource;

import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * Registers a child resource definition of a subsystem.
 * @author Paul Ferraro
 */
public interface ChildResourceDefinitionRegistrar extends ResourceDefinitionRegistrar<ManagementResourceRegistration> {

}
