/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.server.services.net.SpecifiedInterfaceRemoveHandler;

/**
 * Handler for removing a fully-specified interface.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class HostSpecifiedInterfaceRemoveHandler extends SpecifiedInterfaceRemoveHandler {

    public HostSpecifiedInterfaceRemoveHandler() {
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return true;
    }
}
