/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import org.jboss.as.controller.transform.SubsystemExtensionTransformerRegistration;

/**
 * Registers model transformations for the IO subsystem.
 */
public class IOExtensionTransformerRegistration extends SubsystemExtensionTransformerRegistration {

    public IOExtensionTransformerRegistration() {
        super(IOSubsystemRegistrar.NAME, IOSubsystemModel.CURRENT, IOSubsystemTransformationDescriptionFactory.INSTANCE);
    }
}
