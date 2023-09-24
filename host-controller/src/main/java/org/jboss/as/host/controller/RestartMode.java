/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public enum RestartMode {

    /**
     * Only restart the host controller
     */
    HC_ONLY,

    /**
     * Restart HC and servers
     */
    SERVERS;

}
