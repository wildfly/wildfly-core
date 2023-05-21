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

import static org.wildfly.common.Assert.checkNotNullParam;

import java.security.Principal;

import org.wildfly.security.evidence.Evidence;

/**
 * A piece of evidence representing the authenticating domain server.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public final class DomainServerEvidence implements Evidence {

    private final Principal serverPrincipal;
    private final String token;

    public DomainServerEvidence(Principal serverPrincipal, String token) {
        this.serverPrincipal = checkNotNullParam("serverPrincipal", serverPrincipal);
        this.token = checkNotNullParam("token", token);
    }

    @Override
    public Principal getDefaultPrincipal() {
        return serverPrincipal;
    }

    /**
     * Get the server's security token.
     *
     * @return the server's security token
     */
    public String getToken() {
        return this.token;
    }

}
