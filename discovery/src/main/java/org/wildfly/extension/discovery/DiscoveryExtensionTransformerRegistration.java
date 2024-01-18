/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import org.jboss.as.controller.transform.SubsystemExtensionTransformerRegistration;

/**
 * Transformer registration for discovery extension.
 * @author Paul Ferraro
 */
public class DiscoveryExtensionTransformerRegistration extends SubsystemExtensionTransformerRegistration {

    public DiscoveryExtensionTransformerRegistration() {
        super(DiscoverySubsystemRegistrar.NAME, DiscoverySubsystemModel.CURRENT, DiscoverySubsystemTransformationDescriptionFactory.INSTANCE);
    }
}
