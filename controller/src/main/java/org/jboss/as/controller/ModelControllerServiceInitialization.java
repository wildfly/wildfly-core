/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.msc.service.ServiceTarget;

/**
 * Internal extension point for core resource registrations.
 *
 * @author Emanuel Muckenhuber
 */
public interface ModelControllerServiceInitialization {

    /**
     * Initialize a standalone server.
     *
     * @param target the service target
     * @param managementModel the management model
     * @param processType The ProcessType used to identify what type of server we are running in.
     */
    void initializeStandalone(ServiceTarget target, ManagementModel managementModel, ProcessType processType);

    /**
     * Initialize the domain controller.
     *
     * @param target the service target
     * @param managementModel the management model
     */
    void initializeDomain(ServiceTarget target, ManagementModel managementModel);

    /**
     * Initialize a host controller.
     * @param target the service target
     * @param managementModel the management model
     * @param hostName the name of the host
     * @param processType The ProcessType that to identify what type of server we are running in.
     */
    void initializeHost(ServiceTarget target, ManagementModel managementModel, String hostName, ProcessType processType);

}
