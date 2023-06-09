/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package org.wildfly.extension.elytron.common;

import java.security.KeyStore;
import java.security.Provider;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.wildfly.extension.elytron.common.capabilities.PrincipalTransformer;
import org.wildfly.security.auth.server.RealmMapper;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.x500.cert.acme.AcmeAccount;
import org.wildfly.security.x500.cert.acme.CertificateAuthority;

/**
 * The capabilities provided by and required by Elytron.
 *
 * @implNote It is a deliberate decision that this class is not public, by using capability definitions it should be possible to
 * completely remove this subsystem and allow another to provide all the capabilities - allowing references to this class would
 * not allow complete removal.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:carodrig@redhat.com">Cameron Rodriguez</a>
 */
public abstract class ElytronCommonCapabilities {

    protected static final String CAPABILITY_BASE = "org.wildfly.security.";

    public static final String CERTIFICATE_AUTHORITY_ACCOUNT_CAPABILITY = CAPABILITY_BASE + "certificate-authority-account";

    public static final RuntimeCapability<Void> CERTIFICATE_AUTHORITY_ACCOUNT_RUNTIME_CAPABILITY =  RuntimeCapability
            .Builder.of(CERTIFICATE_AUTHORITY_ACCOUNT_CAPABILITY, true, AcmeAccount.class)
            .build();

    public static final String CERTIFICATE_AUTHORITY_CAPABILITY = CAPABILITY_BASE + "certificate-authority";

    public static final RuntimeCapability<Void> CERTIFICATE_AUTHORITY_RUNTIME_CAPABILITY =  RuntimeCapability
            .Builder.of(CERTIFICATE_AUTHORITY_CAPABILITY, true, CertificateAuthority.class)
            .build();

    public static final String ELYTRON_CAPABILITY = CAPABILITY_BASE + "elytron";

    public static final String KEY_MANAGER_CAPABILITY = CAPABILITY_BASE + "key-manager";

    public static final RuntimeCapability<Void> KEY_MANAGER_RUNTIME_CAPABILITY =  RuntimeCapability
            .Builder.of(KEY_MANAGER_CAPABILITY, true, KeyManager.class)
            .build();

    public static final String KEY_STORE_CAPABILITY = CAPABILITY_BASE + "key-store";

    public static final RuntimeCapability<Void> KEY_STORE_RUNTIME_CAPABILITY =  RuntimeCapability
            .Builder.of(KEY_STORE_CAPABILITY, true, KeyStore.class)
            .build();

    public static final String PRINCIPAL_TRANSFORMER_CAPABILITY = CAPABILITY_BASE + "principal-transformer";

    public static final RuntimeCapability<Void> PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY =  RuntimeCapability
            .Builder.of(PRINCIPAL_TRANSFORMER_CAPABILITY, true, PrincipalTransformer.class)
            .build();

    public static final String PROVIDERS_CAPABILITY = CAPABILITY_BASE + "providers";

    /*
     * A variant of the credential-store capability which also provides access to the underlying
     * {@code CredentialStore} as a runtime API from a {@code ExceptionFunction<OperationContext, Provider[], OperationFailedException>}.
     */
    public static final String PROVIDERS_API_CAPABILITY = CAPABILITY_BASE + "providers-api";

    public static final RuntimeCapability<Void> PROVIDERS_RUNTIME_CAPABILITY =  RuntimeCapability
            .Builder.of(PROVIDERS_CAPABILITY, true, Provider[].class)
            .build();

    public static final String REALM_MAPPER_CAPABILITY = CAPABILITY_BASE + "realm-mapper";

    public static final RuntimeCapability<Void> REALM_MAPPER_RUNTIME_CAPABILITY =  RuntimeCapability
            .Builder.of(REALM_MAPPER_CAPABILITY, true, RealmMapper.class)
            .build();

    public static final String SECURITY_DOMAIN_CAPABILITY = CAPABILITY_BASE + "security-domain";

    public static final RuntimeCapability<Void> SECURITY_DOMAIN_RUNTIME_CAPABILITY = RuntimeCapability
            .Builder.of(SECURITY_DOMAIN_CAPABILITY, true, SecurityDomain.class)
            .build();

    public static final String SSL_CONTEXT_CAPABILITY = CAPABILITY_BASE + "ssl-context";

    public static final RuntimeCapability<Void> SSL_CONTEXT_RUNTIME_CAPABILITY = RuntimeCapability
            .Builder.of(SSL_CONTEXT_CAPABILITY, true, SSLContext.class)
            .build();

    public static final String TRUST_MANAGER_CAPABILITY = CAPABILITY_BASE + "trust-manager";

    public static final RuntimeCapability<Void> TRUST_MANAGER_RUNTIME_CAPABILITY =  RuntimeCapability
            .Builder.of(TRUST_MANAGER_CAPABILITY, true, TrustManager.class)
            .build();

    public static final String DIR_CONTEXT_CAPABILITY = CAPABILITY_BASE + "dir-context";
}
