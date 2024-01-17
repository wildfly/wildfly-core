/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

/**
 * Class to hold capabilities provided by and required by resources within this package.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
final class Capabilities {
    static final String HTTP_LISTENER_REGISTRY_CAPABILITY_NAME = "org.wildfly.remoting.http-listener-registry";

    static final String REMOTING_ENDPOINT_CAPABILITY_NAME = "org.wildfly.remoting.endpoint";

    static final String AUTHENTICATION_CONTEXT_CAPABILITY = "org.wildfly.security.authentication-context";

    static final String SASL_AUTHENTICATION_FACTORY_CAPABILITY = "org.wildfly.security.sasl-authentication-factory";

    static final String SSL_CONTEXT_CAPABILITY = "org.wildfly.security.ssl-context";
}
