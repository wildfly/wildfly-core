/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.SubsystemModel;

/**
 * Enumerates the supported model versions of this subsystem.
 */
public enum IOSubsystemModel implements SubsystemModel {

    VERSION_4_0_0(4), // WildFly 12
    VERSION_5_0_0(5), // WildFly 13-31, EAP 7.2-8.0
    VERSION_6_0_0(6), // WildFly 32-present
    ;
    static final IOSubsystemModel CURRENT = VERSION_6_0_0;

    private final ModelVersion version;

    IOSubsystemModel(int major) {
        this.version = ModelVersion.create(major);
    }

    @Override
    public ModelVersion getVersion() {
        return this.version;
    }
}
