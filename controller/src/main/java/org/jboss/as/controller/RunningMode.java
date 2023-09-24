/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

/**
* The current running mode of the server.
*
* @author Brian Stansberry (c) 2011 Red Hat Inc.
*/
public enum RunningMode {
    /** Normal operations; server has started all configured services and is capable of handling end user requests. */
    NORMAL,
    /** The server has only started services necessary to accept and handle administrative requests. */
    ADMIN_ONLY
}
