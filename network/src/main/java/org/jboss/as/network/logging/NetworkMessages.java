/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.network.logging;

import java.lang.invoke.MethodHandles;

import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;
import org.jboss.logging.Messages;

/**
 * Date: 24.06.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MessageBundle(projectCode = "WFLYNET", length = 4)
public interface NetworkMessages {
    /**
     * The default messages
     */
    NetworkMessages MESSAGES = Messages.getBundle(MethodHandles.lookup(), NetworkMessages.class);

    /**
     * Creates an exception indicating the value cannot be changed while the socket is bound.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 1, value = "cannot change value while the socket is bound.")
    IllegalStateException cannotChangeWhileBound();

    /**
     * Creates an exception indicating the no multicast binding for the name.
     *
     * @param name the name.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 2, value = "no multicast binding: %s")
    IllegalStateException noMulticastBinding(String name);

    // id = 3; redundant parameter null / empty check message

    // id = 4; redundant parameter null check message

    // id = 5; redundant parameter null check message

    // id = 6; redundant minimum port number check message
}
