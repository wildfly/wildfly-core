/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.plan;

import java.util.Comparator;

import org.jboss.as.controller.client.helpers.domain.ServerIdentity;

/** Used to order ServerIdentity instances based on host name */
class ServerIdentityComparator implements Comparator<ServerIdentity> {

    static final ServerIdentityComparator INSTANCE = new ServerIdentityComparator();

    @Override
    public int compare(ServerIdentity o1, ServerIdentity o2) {
        int val = o1.getHostName().compareTo(o2.getHostName());
        if (val == 0) {
            val = o1.getServerName().compareTo(o2.getServerName());
        }
        return val;
    }
}
