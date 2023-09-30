/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.SubsystemModel;

/**
 * Enumerates the supported model versions of the remoting subsystem.
 */
public enum RemotingSubsystemModel implements SubsystemModel {
    VERSION_4_0_0(4, 0, 0), // WildFly 11, EAP 7.1
    VERSION_5_0_0(5, 0, 0), // WildFly 12 - 26, EAP 7.2 - 7.4
    VERSION_6_0_0(6, 0, 0), // WildFly 27 - present, EAP 8.0
    VERSION_7_0_0(6, 0, 0), // WildFly 30
    ;
    static final RemotingSubsystemModel CURRENT = VERSION_7_0_0;

    private final ModelVersion version;

    RemotingSubsystemModel(int major, int minor, int micro) {
        this.version = ModelVersion.create(major, minor, micro);
    }

    @Override
    public ModelVersion getVersion() {
        return this.version;
    }
}
