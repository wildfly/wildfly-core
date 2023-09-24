/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import java.util.EnumSet;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.transform.ExtensionTransformerRegistration;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;
import org.jboss.as.controller.transform.description.TransformationDescription;

/**
 * Transformer registration for discovery extension.
 * @author Paul Ferraro
 */
public class DiscoveryExtensionTransformerRegistration implements ExtensionTransformerRegistration {

    @Override
    public String getSubsystemName() {
        return DiscoveryExtension.SUBSYSTEM_NAME;
    }

    @Override
    public void registerTransformers(SubsystemTransformerRegistration registration) {
        for (DiscoverySubsystemModel model : EnumSet.complementOf(EnumSet.of(DiscoverySubsystemModel.CURRENT))) {
            ModelVersion version = model.getVersion();
            TransformationDescription.Tools.register(DiscoverySubsystemDefinition.buildTransformers(version), registration, version);
        }
    }
}
