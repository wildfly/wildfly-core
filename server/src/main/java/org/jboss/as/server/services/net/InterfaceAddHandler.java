/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.services.net;

/**
 * TODO remove this once we can get the superclass out of the controller module to a place
 * where the NetworkInterface class is visible.
 *
 * @author Brian Stansberry
 */
public class InterfaceAddHandler extends org.jboss.as.controller.operations.common.InterfaceAddHandler {

    public static final InterfaceAddHandler NAMED_INSTANCE = new InterfaceAddHandler(false);

    /**
     * Create the InterfaceAddHandler
     *
     * @param specified {@code true} if the interface is expected to have a specified interface selection criteria;
     *                  {@code false} if the interface can simply be a named placeholder
     */
    InterfaceAddHandler(boolean specified) {
        super(specified);
    }

}
