/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.management;

import java.util.concurrent.Executor;

import org.wildfly.service.descriptor.NullaryServiceDescriptor;

/**
 * Class to hold capabilities provided by and required by resources within this package.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public final class Capabilities {
    public static final NullaryServiceDescriptor<Executor> MANAGEMENT_EXECUTOR = NullaryServiceDescriptor.of("org.wildfly.management.executor", Executor.class);

    public static final String HTTP_MANAGEMENT_CAPABILITY = "org.wildfly.management.http-interface";

    public static final String HTTP_AUTHENTICATION_FACTORY_CAPABILITY = "org.wildfly.security.http-authentication-factory";

    public static final String NATIVE_MANAGEMENT_CAPABILITY = "org.wildfly.management.native-interface";

    public static final String SASL_AUTHENTICATION_FACTORY_CAPABILITY = "org.wildfly.security.sasl-authentication-factory";

    public static final String SSL_CONTEXT_CAPABILITY = "org.wildfly.security.ssl-context";

    public static final String MANAGEMENT_SECURITY_REALM_CAPABILITY = "org.wildfly.core.management.security.realm";

}
