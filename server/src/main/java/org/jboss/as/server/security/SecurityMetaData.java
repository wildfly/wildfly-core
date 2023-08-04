/*
 * Copyright 2020 Red Hat, Inc.
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
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.msc.service.ServiceName;

/**
 * Meta Data to be attached to a {@link DeploymentUnit} or {@link OperationContext} to contain information
 * about the active security policy.
 *
 * Note: This applies to security backed by WildFly Elytron only not legacy security.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class SecurityMetaData {

    public static final AttachmentKey<SecurityMetaData> ATTACHMENT_KEY = AttachmentKey.create(SecurityMetaData.class);
    public static final OperationContext.AttachmentKey<SecurityMetaData> OPERATION_CONTEXT_ATTACHMENT_KEY = OperationContext.AttachmentKey.create(SecurityMetaData.class);

    private volatile ServiceName securityDomain;

    /**
     * Get the {@code ServiceName} of the {@code SecurityDomain} selected for use with this deployment.
     *
     * @return the {@code ServiceName} of the {@code SecurityDomain} selected for use with this deployment.
     */
    public ServiceName getSecurityDomain() {
        return securityDomain;
    }

    /**
     * Get the {@code ServiceName} of the {@code SecurityDomain} selected for use with this deployment.
     *
     * @return the {@code ServiceName} of the {@code SecurityDomain} selected for use with this deployment.
     */
    public void setSecurityDomain(ServiceName securityDomain) {
        this.securityDomain = securityDomain;
    }

}
