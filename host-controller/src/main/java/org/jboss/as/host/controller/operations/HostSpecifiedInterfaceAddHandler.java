/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.server.services.net.SpecifiedInterfaceAddHandler;

/**
 * Handler for adding a fully specified interface to a host.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class HostSpecifiedInterfaceAddHandler extends SpecifiedInterfaceAddHandler {

    public HostSpecifiedInterfaceAddHandler() {
        super();
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return context.getProcessType().isHostController();
    }
}
