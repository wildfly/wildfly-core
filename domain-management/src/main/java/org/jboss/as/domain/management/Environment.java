/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management;

/**
 * A simple enumeration used by domain management resources to identify the environment running.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public enum Environment {
    DOMAIN, DOMAIN_SERVER, HOST_CONTROLLER, STANDALONE_SERVER;
}
