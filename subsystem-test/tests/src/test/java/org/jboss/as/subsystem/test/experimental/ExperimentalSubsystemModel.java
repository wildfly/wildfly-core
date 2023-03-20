/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.experimental;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.SubsystemModel;

/**
 * @author Paul Ferraro
 */
public enum ExperimentalSubsystemModel implements SubsystemModel {
    VERSION_1_0(1),
    ;

    private final ModelVersion version;

    ExperimentalSubsystemModel(int major) {
        this.version = ModelVersion.create(major);
    }

    @Override
    public ModelVersion getVersion() {
        return this.version;
    }
}
