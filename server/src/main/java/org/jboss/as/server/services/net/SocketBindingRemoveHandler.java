/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.services.net;


import org.jboss.as.controller.AbstractRemoveStepHandler;

/**
 * Handler for the socket-binding resource's remove operation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class SocketBindingRemoveHandler extends AbstractRemoveStepHandler {

    public static final SocketBindingRemoveHandler INSTANCE = new SocketBindingRemoveHandler();

    /**
     * Create the SocketBindingRemoveHandler
     */
    protected SocketBindingRemoveHandler() {
    }
}
