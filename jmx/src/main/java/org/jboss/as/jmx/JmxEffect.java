/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx;

import java.util.Collections;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.access.HostEffect;
import org.jboss.as.controller.access.ServerGroupEffect;

/**
 * Encapsulates information about the relationship of a JMX object to hosts and server groups in a domain.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
class JmxEffect implements HostEffect, ServerGroupEffect {
    private final Set<String> hostNames;
    private final Set<String> serverGroups;

    JmxEffect(String hostName, String serverGroups) {
        this.hostNames = hostName == null ? Collections.emptySet() : Collections.singleton(hostName);
        this.serverGroups = serverGroups== null ? Collections.emptySet() : Collections.singleton(serverGroups);
    }

    @Override
    public PathAddress getResourceAddress() {
        return PathAddress.EMPTY_ADDRESS;
    }

    @Override
    public boolean isHostEffectGlobal() {
       return false;
    }

    @Override
    public boolean isServerEffect() {
        return true;
    }

    @Override
    public Set<String> getAffectedHosts() {
        return hostNames;
    }

    @Override
    public boolean isServerGroupEffectGlobal() {
        return false;
    }

    @Override
    public boolean isServerGroupEffectUnassigned() {
        return serverGroups.isEmpty();
    }

    @Override
    public Set<String> getAffectedServerGroups() {
        return serverGroups;
    }

    @Override
    public boolean isServerGroupAdd() {
        return false;
    }

    @Override
    public boolean isServerGroupRemove() {
        return false;
    }

}
