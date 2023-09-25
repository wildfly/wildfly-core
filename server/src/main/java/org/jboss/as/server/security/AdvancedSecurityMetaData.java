/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.security;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.msc.service.ServiceName;

/**
 * Meta Data to be attached to a {@link DeploymentUnit} or {@link OperationContext} to contain
 * information about the active security policy.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public class AdvancedSecurityMetaData extends SecurityMetaData {

    private volatile ServiceName httpServerAuthenticationMechanismFactory;

    /**
     * Get the {@code ServiceName} of the {@code HttpServerAuthenticationMechanismFactory} selected for use with this deployment
     * or operation context.
     *
     * @return the {@code ServiceName} of the {@code HttpServerAuthenticationMechanismFactory} selected for use with this deployment
     * or operation context.
     */
    public ServiceName getHttpServerAuthenticationMechanismFactory() {
        return httpServerAuthenticationMechanismFactory;
    }

    /**
     * Get the {@code ServiceName} of the {@code HttpServerAuthenticationMechanismFactory} selected for use with this deployment
     * or operation context.
     *
     * @return the {@code ServiceName} of the {@code HttpServerAuthenticationMechanismFactory} selected for use with this deployment
     * or operation context.
     */
    public void setHttpServerAuthenticationMechanismFactory(ServiceName httpServerAuthenticationMechanismFactory) {
        this.httpServerAuthenticationMechanismFactory = httpServerAuthenticationMechanismFactory;
    }

}

