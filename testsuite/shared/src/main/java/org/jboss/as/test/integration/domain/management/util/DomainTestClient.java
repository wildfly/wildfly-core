/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.management.util;

import org.jboss.as.controller.client.impl.AbstractModelControllerClient;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;

/**
 * Internal test client helper.
 *
 * @author Emanuel Muckenhuber
 */
abstract class DomainTestClient extends AbstractModelControllerClient {

    /**
     * Get the underlying connection.
     *
     * @return the connection
     */
    abstract Connection getConnection();

    /**
     * Get the underlying channel.
     *
     * @return the channel
     */
    abstract Channel getChannel();

}
