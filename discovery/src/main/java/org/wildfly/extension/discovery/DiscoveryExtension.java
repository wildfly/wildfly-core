/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import org.jboss.as.controller.SubsystemModel;
import org.wildfly.subsystem.SubsystemConfiguration;
import org.wildfly.subsystem.SubsystemExtension;
import org.wildfly.subsystem.SubsystemPersistence;
import org.wildfly.subsystem.resource.SubsystemResourceDefinitionRegistrar;

/**
 * The extension class for the WildFly Discovery extension.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class DiscoveryExtension extends SubsystemExtension<DiscoverySubsystemSchema> {

    public DiscoveryExtension() {
        super(new SubsystemConfiguration() {
            @Override
            public String getName() {
                return DiscoverySubsystemRegistrar.NAME;
            }

            @Override
            public SubsystemModel getModel() {
                return DiscoverySubsystemModel.CURRENT;
            }

            @Override
            public SubsystemResourceDefinitionRegistrar getRegistrar() {
                return new DiscoverySubsystemRegistrar();
            }
        }, SubsystemPersistence.of(DiscoverySubsystemSchema.CURRENT));
    }
}
