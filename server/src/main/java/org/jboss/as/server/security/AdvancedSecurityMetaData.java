/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

