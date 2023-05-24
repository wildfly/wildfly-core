/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;

import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.msc.service.ServiceName;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.security.auth.callback.ChannelBindingCallback;
import org.wildfly.security.auth.callback.CredentialCallback;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.sasl.util.SaslMechanismInformation;

import mockit.integration.junit4.JMockit;

/**
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
@RunWith(JMockit.class)
public class SaslTestCase extends AbstractSubsystemTest {

    public SaslTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    private KernelServices services = null;

    private void init() throws Exception {
        TestEnvironment.mockCallerModuleClassloader(); // to allow loading classes from testsuite
        services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("sasl-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            if (services.getBootError() != null) {
                Assert.fail(services.getBootError().toString());
            }
            Assert.fail("Failed to boot, no reason provided");
        }
    }

    @Test
    public void testSaslServerDigest() throws Exception {
        init();
        ServiceName serviceNameServer = Capabilities.SASL_SERVER_FACTORY_RUNTIME_CAPABILITY.getCapabilityServiceName("MySaslServer");
        SaslServerFactory serverFactory = (SaslServerFactory) services.getContainer().getService(serviceNameServer).getValue();

        Map<String, Object> serverClientProps = new HashMap<String, Object>();
        serverClientProps.put("javax.security.sasl.qop", "auth-conf");
        SaslServer server = serverFactory.createSaslServer(SaslMechanismInformation.Names.DIGEST_MD5,
                "protocol", "TestingRealm1", serverClientProps, serverCallbackHandler("user1", "TestingRealm1", "password1"));
        SaslClient client = Sasl.createSaslClient(new String[]{SaslMechanismInformation.Names.DIGEST_MD5},
                "user1", "protocol", "TestingRealm1", serverClientProps, clientCallbackHandler("user1", "TestingRealm1", "password1"));

        testSaslServerClient(server, client);
    }

    @Test
    public void testSaslAuthenticationPlain() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SASL_AUTHENTICATION_FACTORY_RUNTIME_CAPABILITY.getCapabilityServiceName("MySaslAuth");
        SaslAuthenticationFactory authFactory = (SaslAuthenticationFactory) services.getContainer().getService(serviceName).getValue();

        SaslServer server = authFactory.createMechanism(SaslMechanismInformation.Names.PLAIN);
        SaslClient client = Sasl.createSaslClient(new String[]{SaslMechanismInformation.Names.PLAIN},
                "firstUser", "protocol", "TestServer", Collections.<String, Object>emptyMap(), clientCallbackHandler("firstUser", "PlainRealm", "clearPassword"));

        testSaslServerClient(server, client);
    }

    @Test
    public void testSaslAuthenticationDigest() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SASL_AUTHENTICATION_FACTORY_RUNTIME_CAPABILITY.getCapabilityServiceName("MySaslAuth");
        SaslAuthenticationFactory authFactory = (SaslAuthenticationFactory) services.getContainer().getService(serviceName).getValue();

        SaslServer server = authFactory.createMechanism(SaslMechanismInformation.Names.DIGEST_SHA);
        SaslClient client = Sasl.createSaslClient(new String[]{SaslMechanismInformation.Names.DIGEST_SHA},
                "firstUser", "myProtocol", "TestingServer", Collections.<String, Object>emptyMap(), clientCallbackHandler("firstUser", "DigestRealm", "clearPassword"));

        testSaslServerClient(server, client);
    }

    @Test
    public void testSaslAuthenticationScram() throws Exception {
        init();
        ServiceName serviceName = Capabilities.SASL_AUTHENTICATION_FACTORY_RUNTIME_CAPABILITY.getCapabilityServiceName("MySaslAuth");
        SaslAuthenticationFactory authFactory = (SaslAuthenticationFactory) services.getContainer().getService(serviceName).getValue();

        SaslServer server = authFactory.createMechanism(SaslMechanismInformation.Names.SCRAM_SHA_1);
        SaslClient client = Sasl.createSaslClient(new String[]{SaslMechanismInformation.Names.SCRAM_SHA_1},
                "firstUser", "protocol", "TestServer", Collections.<String, Object>emptyMap(), clientCallbackHandler("firstUser", "ScramRealm", "clearPassword"));

        testSaslServerClient(server, client);
    }

    private void testSaslServerClient(SaslServer server, SaslClient client) throws SaslException {
        byte[] message = new byte[]{};
        if (client.hasInitialResponse()) message = client.evaluateChallenge(message);
        while(!server.isComplete() || !client.isComplete()) {
            if (!server.isComplete()) message = server.evaluateResponse(message);
            if (!client.isComplete()) message = client.evaluateChallenge(message);
        }
    }

    private CallbackHandler serverCallbackHandler(String username, String realm, String password) {
        return callbacks -> {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback) {
                    Assert.assertEquals(username, ((NameCallback) callback).getDefaultName());
                } else if (callback instanceof RealmCallback) {
                    Assert.assertEquals(realm, ((RealmCallback) callback).getDefaultText());
                } else if (callback instanceof PasswordCallback) {
                    ((PasswordCallback) callback).setPassword(password.toCharArray());
                } else if (callback instanceof AuthorizeCallback) {
                    ((AuthorizeCallback) callback).setAuthorized(((AuthorizeCallback) callback).getAuthorizationID().equals(((AuthorizeCallback) callback).getAuthenticationID()));
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        };
    }

    private CallbackHandler clientCallbackHandler(String username, String realm, String password) throws Exception {
        return callbacks -> {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback) {
                    ((NameCallback) callback).setName(username);
                } else if (callback instanceof RealmCallback) {
                    ((RealmCallback) callback).setText(realm);
                } else if (callback instanceof PasswordCallback) {
                    ((PasswordCallback) callback).setPassword(password.toCharArray());
                } else if (callback instanceof CredentialCallback && ClearPassword.ALGORITHM_CLEAR.equals(((CredentialCallback) callback).getAlgorithm())) {
                    try {
                        PasswordFactory factory = PasswordFactory.getInstance(ClearPassword.ALGORITHM_CLEAR);
                        Password pass = factory.generatePassword(new ClearPasswordSpec(password.toCharArray()));

                        ((CredentialCallback) callback).setCredential(new PasswordCredential(pass));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } else if (callback instanceof ChannelBindingCallback) {
                    ((ChannelBindingCallback) callback).setBindingType("type");
                    ((ChannelBindingCallback) callback).setBindingData(new byte[]{0x12, 0x34});
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        };
    }

}
