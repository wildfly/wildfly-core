/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

/**
 * Defines a subsystem management model.
 * @author Paul Ferraro
 */
public interface SubsystemModel {
    /**
     * Returns the version of this subsystem model.
     * @return a model version
     */
    ModelVersion getVersion();

    /**
     * Indicates whether the version of this subsystem model is more recent than the specified version and thus requires transformation
     * @param version a model version
     * @return true if the version of this subsystem model is more recent than the specified version, false otherwise
     */
    default boolean requiresTransformation(ModelVersion version) {
        return this.getVersion().compareTo(version) < 0;
    }
}
