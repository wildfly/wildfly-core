/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller.security;

import java.security.KeyStore;
import java.security.Provider;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.wildfly.security.auth.login.SecurityDomain;
import org.wildfly.security.auth.spi.SecurityRealm;

/**
 * Security related capabilities as used across the application server.
 *
 * These capabilities are defined centrally as it is expected they will be used across the whole application server, also
 * different subsystems can be installed to provide the capabilities.
 *
 * Note: Once included in a release this class must be considered as public API.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public final class Capabilities {

    private Capabilities() {
    }

    private static final String CAPABILITY_BASE = "org.wildfly.security.";

    public static final String KEYSTORE_CAPABILITY = CAPABILITY_BASE + "keystore";

    public static final RuntimeCapability<Void> KEYSTORE_RUNTIME_CAPABILITY =  RuntimeCapability
        .Builder.of(KEYSTORE_CAPABILITY, true, KeyStore.class)
        .build();

    public static final String PROVIDERS_CAPABILITY = CAPABILITY_BASE + "providers";

    public static final RuntimeCapability<Void> PROVIDERS_RUNTIME_CAPABILITY =  RuntimeCapability
        .Builder.of(PROVIDERS_CAPABILITY, true, Provider[].class)
        .build();

    public static final String SECURITY_DOMAIN_CAPABILITY = CAPABILITY_BASE + "security-domain";

    public static final RuntimeCapability<Void> SECURITY_DOMAIN_RUNTIME_CAPABILITY = RuntimeCapability
        .Builder.of(SECURITY_DOMAIN_CAPABILITY, true, SecurityDomain.class)
        .build();

    public static final String SECURITY_REALM_CAPABILITY = CAPABILITY_BASE + "security-realm";

    public static final RuntimeCapability<Void> SECURITY_REALM_RUNTIME_CAPABILITY = RuntimeCapability
        .Builder.of(SECURITY_REALM_CAPABILITY, true, SecurityRealm.class)
        .build();

}
