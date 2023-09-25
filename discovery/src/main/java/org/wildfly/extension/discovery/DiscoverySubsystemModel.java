/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.SubsystemModel;

/**
 * Enumeration of discovery subsystem model versions.
 * @author Paul Ferraro
 */
enum DiscoverySubsystemModel implements SubsystemModel {
    VERSION_1_0_0(1, 0, 0),
    VERSION_2_0_0(2, 0, 0),
    ;
    static final DiscoverySubsystemModel CURRENT = VERSION_2_0_0;

    private final ModelVersion version;

    DiscoverySubsystemModel(int major, int minor, int micro) {
        this.version = ModelVersion.create(major, minor, micro);
    }

    @Override
    public ModelVersion getVersion() {
        return this.version;
    }
}
