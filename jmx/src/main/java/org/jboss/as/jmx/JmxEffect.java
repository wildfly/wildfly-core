/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
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
