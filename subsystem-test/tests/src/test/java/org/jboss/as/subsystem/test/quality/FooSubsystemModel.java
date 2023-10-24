/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.quality;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.SubsystemModel;

/**
 * @author Paul Ferraro
 */
public enum FooSubsystemModel implements SubsystemModel {
    VERSION_1_0(1),
    ;
    static final FooSubsystemModel CURRENT = VERSION_1_0;

    private final ModelVersion version;

    FooSubsystemModel(int major) {
        this.version = ModelVersion.create(major);
    }

    @Override
    public ModelVersion getVersion() {
        return this.version;
    }
}
