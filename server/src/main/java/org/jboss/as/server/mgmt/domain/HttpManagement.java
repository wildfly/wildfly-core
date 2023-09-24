/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.mgmt.domain;

import org.jboss.as.network.NetworkInterfaceBinding;

/**
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface HttpManagement {

    int getHttpPort();

    NetworkInterfaceBinding getHttpNetworkInterfaceBinding();

    int getHttpsPort();

    NetworkInterfaceBinding getHttpsNetworkInterfaceBinding();

    boolean hasConsole();

}
