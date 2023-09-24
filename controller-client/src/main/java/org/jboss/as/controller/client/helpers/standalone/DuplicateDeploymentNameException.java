/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.standalone;

import org.jboss.as.controller.client.logging.ControllerClientLogger;

/**
 * Exception indicating an attempt to add deployment content to a domain or
 * server that has the same name as existing content.
 *
 * @author Brian Stansberry
 */
public class DuplicateDeploymentNameException extends Exception {

    private static final long serialVersionUID = -7207529184499737454L;

    private final String name;

    /**
     * @param name
     * @param fullDomain
     */
    public DuplicateDeploymentNameException(String name, boolean fullDomain) {
        super(fullDomain ? ControllerClientLogger.ROOT_LOGGER.domainDeploymentAlreadyExists(name) : ControllerClientLogger.ROOT_LOGGER.serverDeploymentAlreadyExists(name));
        this.name = name;
    }

    public String getDeploymentName() {
        return name;
    }

}
