/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
