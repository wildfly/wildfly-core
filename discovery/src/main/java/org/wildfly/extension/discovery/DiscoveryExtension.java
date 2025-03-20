/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import org.wildfly.subsystem.SubsystemConfiguration;
import org.wildfly.subsystem.SubsystemExtension;
import org.wildfly.subsystem.SubsystemPersistence;

/**
 * The extension class for the WildFly Discovery extension.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class DiscoveryExtension extends SubsystemExtension<DiscoverySubsystemSchema> {

    public DiscoveryExtension() {
        super(SubsystemConfiguration.of(DiscoverySubsystemResourceDefinitionRegistrar.REGISTRATION, DiscoverySubsystemModel.CURRENT, DiscoverySubsystemResourceDefinitionRegistrar::new), SubsystemPersistence.of(DiscoverySubsystemSchema.CURRENT));
    }
}
