/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.services.net;

import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.resource.InterfaceDefinition;
import org.jboss.as.network.NetworkInterfaceBinding;

/**
 * TODO remove this once we can get the superclass out of the controller module to a place
 * where the NetworkInterface class is visible.
 *
 * @author Brian Stansberry
 */
public class InterfaceResourceDefinition extends InterfaceDefinition {

    static final RuntimeCapability<Void> INTERFACE_CAPABILITY = RuntimeCapability.Builder.of(NetworkInterfaceBinding.SERVICE_DESCRIPTOR)
            .setAllowMultipleRegistrations(true) // both /host=master/interface=x and /interface=x are legal and in the same scope
                                                 // In a better world we'd only set this true in an HC process
                                                 // but that's more trouble than I want to take. Adding an
                                                 // interface twice in a server will fail in MODEL due to the dup resource anyway
            .build();

    public InterfaceResourceDefinition(InterfaceAddHandler addHandler, OperationStepHandler removeHandler,
                                       boolean updateRuntime, boolean resolvable) {
        super(addHandler, removeHandler, updateRuntime, resolvable, INTERFACE_CAPABILITY);
    }
}
