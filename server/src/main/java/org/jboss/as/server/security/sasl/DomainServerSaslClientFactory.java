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

package org.jboss.as.server.security.sasl;

import static org.jboss.as.server.security.sasl.Constants.JBOSS_DOMAIN_SERVER;

import java.util.Collections;
import java.util.Map;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslClientFactory;
import javax.security.sasl.SaslException;

import org.wildfly.common.Assert;

/**
 * The client factory for the JBOSS-DOMAIN-SERVER SASL mechanism.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public final class DomainServerSaslClientFactory implements SaslClientFactory {

    public static final SaslClientFactory INSTANCE = new DomainServerSaslClientFactory();

    public SaslClient createSaslClient(final String[] mechanisms, final String authorizationId, final String protocol, final String serverName, Map<String, ?> props, final CallbackHandler cbh) throws SaslException {
        Assert.checkNotNullParam("cbh", cbh);
        if (props == null) props = Collections.emptyMap();
        for (String mechanism : mechanisms) {
            if (JBOSS_DOMAIN_SERVER.equals(mechanism)) {
                return new DomainServerSaslClient(cbh);
            }
        }
        return null;
    }

    public String[] getMechanismNames(final Map<String, ?> props) {
        return new String[] { JBOSS_DOMAIN_SERVER };
    }

    /**
     * Get the singleton instance of the {@code SaslClientFactory}
     *
     * @return the singleton instance of the {@code SaslClientFactory}
     */
    public static SaslClientFactory getInstance() {
        return INSTANCE;
    }

}
