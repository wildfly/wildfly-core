/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform;

/**
 * @author Tomaz Cerar (c) 2016 Red Hat Inc.
 */
public interface ExtensionTransformerRegistration {

    /** Return name of subsystem this transformer registration is for.
     * @return non null name of the subsystem
     */
    String getSubsystemName();

    /**
     * Registers subsystem tranformers against the SubsystemTransformerRegistration
     *
     * @param subsystemRegistration contains data about the subsystem registration
     */
    void registerTransformers(SubsystemTransformerRegistration subsystemRegistration);

}
