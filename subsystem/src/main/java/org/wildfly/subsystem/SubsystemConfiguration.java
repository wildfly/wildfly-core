/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem;

import java.util.function.Supplier;

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

    /**
     * Factory method creating a basic SubsystemConfiguration.
     * @param subsystemName the subsystem name
     * @param currentModel the current subsystem model version
     * @param registrarFactory a supplier of the resource definition registrar for this subsystem
     * @return a new subsystem configuration
     */
    static SubsystemConfiguration of(String name, SubsystemModel model, Supplier<SubsystemResourceDefinitionRegistrar> registrarFactory) {
        return new SubsystemConfiguration() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public SubsystemModel getModel() {
                return model;
            }

            @Override
            public SubsystemResourceDefinitionRegistrar getRegistrar() {
                return registrarFactory.get();
            }
        };
    }
}
