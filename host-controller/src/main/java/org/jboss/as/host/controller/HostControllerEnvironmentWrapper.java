/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller;

import org.jboss.as.version.ProductConfig;

/**
 * @author wangc
 *
 */
public final class HostControllerEnvironmentWrapper {

    public enum HostControllerEnvironmentStatus {
        NORMAL, // environment created or an expected abort
        ERROR // problematic abort
    }

    private final HostControllerEnvironment hostControllerEnvironment;
    private final HostControllerEnvironmentStatus hostControllerEnvironmentStatus;
    private final ProductConfig productConfig;

    private HostControllerEnvironmentWrapper(HostControllerEnvironment hostControllerEnvironment, HostControllerEnvironmentStatus hostControllerEnvironmentStatus, ProductConfig productConfig) {
        this.hostControllerEnvironment = hostControllerEnvironment;
        this.hostControllerEnvironmentStatus = hostControllerEnvironmentStatus;
        this.productConfig = productConfig;
    }

    HostControllerEnvironmentWrapper(HostControllerEnvironment hostControllerEnvironment) {
        this(hostControllerEnvironment, HostControllerEnvironmentStatus.NORMAL, hostControllerEnvironment.getProductConfig());
    }

    HostControllerEnvironmentWrapper(HostControllerEnvironmentStatus hostControllerEnvironmentStatus, ProductConfig productConfig) {
        this(null, hostControllerEnvironmentStatus, productConfig);
    }

    public HostControllerEnvironment getHostControllerEnvironment() {
        return hostControllerEnvironment;
    }

    public HostControllerEnvironmentStatus getHostControllerEnvironmentStatus() {
        return hostControllerEnvironmentStatus;
    }

    public ProductConfig getProductConfig() {
        return this.productConfig;
    }
}
