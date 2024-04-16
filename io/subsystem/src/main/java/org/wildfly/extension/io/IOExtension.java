/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import org.wildfly.subsystem.SubsystemConfiguration;
import org.wildfly.subsystem.SubsystemExtension;
import org.wildfly.subsystem.SubsystemPersistence;


/**
 * An extension that registers the IO subsystem.
 * @author Paul Ferraro
 */
public class IOExtension extends SubsystemExtension<IOSubsystemSchema> {

    public IOExtension() {
        super(SubsystemConfiguration.of(IOSubsystemRegistrar.NAME, IOSubsystemModel.CURRENT, IOSubsystemRegistrar::new), SubsystemPersistence.of(IOSubsystemSchema.CURRENT));
    }
}
