/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem;

import org.jboss.as.controller.SubsystemModel;
import org.wildfly.subsystem.resource.SubsystemResourceDefinitionRegistrar;

/**
 * Encapsulates the configuration of a subsystem.
 * @author Paul Ferraro
 */
public interface SubsystemConfiguration {

    /**
     * Returns the subsystem name.
     * @return the subsystem name.
     */
    String getName();

    /**
     * Returns the subsystem model.
     * @return the subsystem model.
     */
    SubsystemModel getModel();

    /**
     * Returns the registrar of the subsystem.
     * @return the registrar of the subsystem.
     */
    SubsystemResourceDefinitionRegistrar getRegistrar();
}
