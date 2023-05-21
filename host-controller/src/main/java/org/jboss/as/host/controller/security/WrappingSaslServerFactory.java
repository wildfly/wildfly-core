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

package org.jboss.as.host.controller.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;

import org.wildfly.security.auth.server.SaslAuthenticationFactory;

/**
 * A {@code SaslServerFactory} which can wrap the Elytron {@code SaslAuthenticationFactory} to
 * add additional mechanism(s).
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class WrappingSaslServerFactory implements SaslServerFactory {

    private final SaslAuthenticationFactory originalSaslAuthenticationFactory;
    private final SaslServerFactory hostControllerSaslServerFactory;

    WrappingSaslServerFactory(final SaslAuthenticationFactory originalSaslAuthenticationFactory, final SaslServerFactory hostControllerSaslServerFactory) {
        this.originalSaslAuthenticationFactory = originalSaslAuthenticationFactory;
        this.hostControllerSaslServerFactory = hostControllerSaslServerFactory;
    }

    @Override
    public SaslServer createSaslServer(String mechanism, String protocol, String serverName, Map<String, ?> props,
            CallbackHandler cbh) throws SaslException {
        // In all cases we discard the CallbackHander passed in, can we find a way so the SaslAuthenticationFactory
        // supports augmenting?
        Collection<String> originalMechanisms = originalSaslAuthenticationFactory.getMechanismNames();
        if (originalMechanisms.contains(mechanism)) {
            return originalSaslAuthenticationFactory.createMechanism(mechanism);
        }

        // TODO Swap in CBH which will delegate to ServerInventory or more specifically one that has access to
        // some form of Evidence verifier.
        // The CallbackHanlder will also use the SecurityDomain directly to create an AdHocIdentity.
        return hostControllerSaslServerFactory.createSaslServer(mechanism, protocol, serverName, props, cbh);
    }

    @Override
    public String[] getMechanismNames(Map<String, ?> props) {
        String[] hostControllerMechanisms = hostControllerSaslServerFactory.getMechanismNames(props);
        Collection<String> originalMechanisms = originalSaslAuthenticationFactory.getMechanismNames();

        ArrayList<String> combined = new ArrayList<>(hostControllerMechanisms.length + originalMechanisms.size());
        combined.addAll(Arrays.asList(hostControllerMechanisms));
        combined.addAll(originalMechanisms);

        return combined.toArray(new String[combined.size()]);
    }

}
