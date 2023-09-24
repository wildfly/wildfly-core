/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.discovery;

import java.util.List;

/**
 * Allow the discovery of a remote domain controller's host and port.
 *
 * @author Farah Juma
 */
public interface DiscoveryOption {

    /**
     *  Allow a domain controller's host name and port number to be discovered.
     *  This method is intended to be called by the domain controller.
     *
     *  @param interfaces the interfaces of the domain controller.
     */
    void allowDiscovery(List<DomainControllerManagementInterface> interfaces);

    /**
     *  Determine the host name and port of the remote domain controller.
     *  This method is intended to be called by a slave host controller.
     *  @return the list of connection parameters to the remote domain controller.
     */
    List<RemoteDomainControllerConnectionConfiguration> discover();

    /**
     * Clean up anything that was created for domain controller discovery.
     */
    void cleanUp();
}
