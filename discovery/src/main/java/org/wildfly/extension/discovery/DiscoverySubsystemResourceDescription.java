/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import org.jboss.as.controller.SubsystemResourceDescription;

/**
 * Describes the discovery subsystem resource.
 */
public enum DiscoverySubsystemResourceDescription implements SubsystemResourceDescription {
    INSTANCE;

    @Override
    public String getName() {
        return "discovery";
    }
}
