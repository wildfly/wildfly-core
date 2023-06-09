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

package org.wildfly.extension.elytron;

import java.security.Permissions;
import java.security.Policy;
import java.util.function.Consumer;

import javax.security.sasl.SaslServerFactory;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.server.security.VirtualDomainMetaData;
import org.jboss.msc.service.ServiceBuilder;
import org.wildfly.extension.elytron.common.ElytronCommonCapabilities;
import org.wildfly.extension.elytron.common.capabilities.CredentialSecurityFactory;
import org.wildfly.extension.elytron.common.capabilities._private.DirContextSupplier;
import org.wildfly.extension.elytron.common.capabilities._private.SecurityEventListener;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.server.EvidenceDecoder;
import org.wildfly.security.auth.server.HttpAuthenticationFactory;
import org.wildfly.security.auth.server.ModifiableSecurityRealm;
import org.wildfly.security.auth.server.PrincipalDecoder;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.authz.PermissionMapper;
import org.wildfly.security.authz.RoleDecoder;
import org.wildfly.security.authz.RoleMapper;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.http.HttpServerAuthenticationMechanismFactory;

/**
 * The capabilities provided by and required by the Elytron subsystem, extending {@link ElytronCommonCapabilities}.
 * <p>
 * It is a deliberate decision that this class is not public, by using capability definitions it should be possible to
 * completely remove this subsystem and allow another to provide all the capabilities - allowing references to this class would
 * not allow complete removal.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class Capabilities extends ElytronCommonCapabilities {

    static final String AUTHENTICATION_CONFIGURATION_CAPABILITY = CAPABILITY_BASE + "authentication-configuration";

    static final RuntimeCapability<Void> AUTHENTICATION_CONFIGURATION_RUNTIME_CAPABILITY = RuntimeCapability
            .Builder.of(AUTHENTICATION_CONFIGURATION_CAPABILITY, true, AuthenticationConfiguration.class)
            .build();

    static final String AUTHENTICATION_CONTEXT_CAPABILITY = CAPABILITY_BASE + "authentication-context";

    static final RuntimeCapability<Void> AUTHENTICATION_CONTEXT_RUNTIME_CAPABILITY = RuntimeCapability
            .Builder.of(AUTHENTICATION_CONTEXT_CAPABILITY, true, AuthenticationContext.class)
            .build();

    static final String CREDENTIAL_STORE_CAPABILITY = CAPABILITY_BASE + "credential-store";

    /*
     * A variant of the credential-store capability which also provides access to the underlying
     * {@code CredentialStore} as a runtime API from a {@code ExceptionFunction<OperationContext, CredentialStore, OperationFailedException>}.
     */
    static final String CREDENTIAL_STORE_API_CAPABILITY = CAPABILITY_BASE + "credential-store-api";

    static final RuntimeCapability<Void> CREDENTIAL_STORE_RUNTIME_CAPABILITY =  RuntimeCapability
            .Builder.of(CREDENTIAL_STORE_CAPABILITY, true, CredentialStore.class)
            .build();

    // This has to be at this position, and must not be a lambda, to avoid an init circularity problem on IBM
    @SuppressWarnings("Convert2Lambda")
    static final Consumer<ServiceBuilder> COMMON_DEPENDENCIES = new Consumer<ServiceBuilder>() {
        // unchecked because ServiceBuilder is a raw type
        @SuppressWarnings("unchecked")
        public void accept(final ServiceBuilder serviceBuilder) {
            ElytronDefinition.commonRequirements(serviceBuilder);
        }
    };

    static final RuntimeCapability<Consumer<ServiceBuilder>> ELYTRON_RUNTIME_CAPABILITY = RuntimeCapability
            .Builder.of(ELYTRON_CAPABILITY, COMMON_DEPENDENCIES)
            .build();

    static final String HTTP_AUTHENTICATION_FACTORY_CAPABILITY = CAPABILITY_BASE + "http-authentication-factory";

    static final RuntimeCapability<Void> HTTP_AUTHENTICATION_FACTORY_RUNTIME_CAPABILITY = RuntimeCapability
            .Builder.of(HTTP_AUTHENTICATION_FACTORY_CAPABILITY, true, HttpAuthenticationFactory.class)
            .build();

    static final String HTTP_SERVER_MECHANISM_FACTORY_CAPABILITY = CAPABILITY_BASE + "http-server-mechanism-factory";

    static final RuntimeCapability<Void> HTTP_SERVER_MECHANISM_FACTORY_RUNTIME_CAPABILITY =  RuntimeCapability
            .Builder.of(HTTP_SERVER_MECHANISM_FACTORY_CAPABILITY, true, HttpServerAuthenticationMechanismFactory.class)
            .build();

    static final String PERMISSION_MAPPER_CAPABILITY = CAPABILITY_BASE + "permission-mapper";

    static final RuntimeCapability<Void> PERMISSION_MAPPER_RUNTIME_CAPABILITY =  RuntimeCapability
            .Builder.of(PERMISSION_MAPPER_CAPABILITY, true, PermissionMapper.class)
            .build();

    static final String PERMISSION_SET_CAPABILITY = CAPABILITY_BASE + "permission-set";

    static final RuntimeCapability<Void> PERMISSION_SET_RUNTIME_CAPABILITY =  RuntimeCapability
            .Builder.of(PERMISSION_SET_CAPABILITY, true, Permissions.class)
            .build();

    static final String PRINCIPAL_DECODER_CAPABILITY = CAPABILITY_BASE + "principal-decoder";

    static final RuntimeCapability<Void> PRINCIPAL_DECODER_RUNTIME_CAPABILITY =  RuntimeCapability
            .Builder.of(PRINCIPAL_DECODER_CAPABILITY, true, PrincipalDecoder.class)
            .build();

    static final String ROLE_DECODER_CAPABILITY = CAPABILITY_BASE + "role-decoder";

    static final RuntimeCapability<Void> ROLE_DECODER_RUNTIME_CAPABILITY =  RuntimeCapability
            .Builder.of(ROLE_DECODER_CAPABILITY, true, RoleDecoder.class)
            .build();

    static final String ROLE_MAPPER_CAPABILITY = CAPABILITY_BASE + "role-mapper";

    static final RuntimeCapability<Void> ROLE_MAPPER_RUNTIME_CAPABILITY =  RuntimeCapability
            .Builder.of(ROLE_MAPPER_CAPABILITY, true, RoleMapper.class)
            .build();

    static final String EVIDENCE_DECODER_CAPABILITY = CAPABILITY_BASE + "evidence-decoder";

    static final RuntimeCapability<Void> EVIDENCE_DECODER_RUNTIME_CAPABILITY =  RuntimeCapability
            .Builder.of(EVIDENCE_DECODER_CAPABILITY, true, EvidenceDecoder.class)
            .build();

    static final String SECURITY_EVENT_LISTENER_CAPABILITY = CAPABILITY_BASE + "security-event-listener";

    static final RuntimeCapability<Void> SECURITY_EVENT_LISTENER_RUNTIME_CAPABILITY = RuntimeCapability
            .Builder.of(SECURITY_EVENT_LISTENER_CAPABILITY, true, SecurityEventListener.class)
            .build();

    static final String SASL_AUTHENTICATION_FACTORY_CAPABILITY = CAPABILITY_BASE + "sasl-authentication-factory";

    static final RuntimeCapability<Void> SASL_AUTHENTICATION_FACTORY_RUNTIME_CAPABILITY = RuntimeCapability
            .Builder.of(SASL_AUTHENTICATION_FACTORY_CAPABILITY, true, SaslAuthenticationFactory.class)
            .build();

    static final String SASL_SERVER_FACTORY_CAPABILITY = CAPABILITY_BASE + "sasl-server-factory";

    static final RuntimeCapability<Void> SASL_SERVER_FACTORY_RUNTIME_CAPABILITY = RuntimeCapability
            .Builder.of(SASL_SERVER_FACTORY_CAPABILITY, true, SaslServerFactory.class)
            .build();

    static final String VIRTUAL_SECURITY_DOMAIN_CAPABILITY = CAPABILITY_BASE + "virtual-security-domain";

    static final RuntimeCapability<Void> VIRTUAL_SECURITY_DOMAIN_RUNTIME_CAPABILITY = RuntimeCapability
            .Builder.of(VIRTUAL_SECURITY_DOMAIN_CAPABILITY, true, VirtualDomainMetaData.class)
            .build();

    static final String SECURITY_FACTORY_CAPABILITY_BASE = CAPABILITY_BASE + "security-factory.";

    static final String SECURITY_FACTORY_CREDENTIAL_CAPABILITY = SECURITY_FACTORY_CAPABILITY_BASE + "credential";

    static final RuntimeCapability<Void> SECURITY_FACTORY_CREDENTIAL_RUNTIME_CAPABILITY = RuntimeCapability
            .Builder.of(SECURITY_FACTORY_CREDENTIAL_CAPABILITY, true, CredentialSecurityFactory.class)
            .build();

    static final String MODIFIABLE_SECURITY_REALM_CAPABILITY = CAPABILITY_BASE + "modifiable-security-realm";

    static final RuntimeCapability<Void> MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY = RuntimeCapability
            .Builder.of(MODIFIABLE_SECURITY_REALM_CAPABILITY, true, ModifiableSecurityRealm.class)
            .build();

    static final String SECURITY_REALM_CAPABILITY = CAPABILITY_BASE + "security-realm";

    static final RuntimeCapability<Void> SECURITY_REALM_RUNTIME_CAPABILITY = RuntimeCapability
            .Builder.of(SECURITY_REALM_CAPABILITY, true, SecurityRealm.class)
            .build();

    static final RuntimeCapability<Void> DIR_CONTEXT_RUNTIME_CAPABILITY = RuntimeCapability
            .Builder.of(DIR_CONTEXT_CAPABILITY, true, DirContextSupplier.class)
            .build();

    static final String POLICY_CAPABILITY = CAPABILITY_BASE + "policy";
    static final RuntimeCapability<Void> POLICY_RUNTIME_CAPABILITY =  RuntimeCapability
            .Builder.of(POLICY_CAPABILITY, false, Policy.class)
            .build();

    static final String JACC_POLICY_CAPABILITY = CAPABILITY_BASE + "jacc-policy";
    static final RuntimeCapability<Void> JACC_POLICY_RUNTIME_CAPABILITY =  RuntimeCapability
            .Builder.of(JACC_POLICY_CAPABILITY, false, Policy.class)
            .build();

    static final String EXPRESSION_RESOLVER_CAPABILITY = CAPABILITY_BASE + "expression-resolver";

    /**
     * Requirements, capabilities from other subsystems.
     */

    /**
     * Required by the {@link JdbcRealmDefinition}.
     */
    static final String DATA_SOURCE_CAPABILITY_NAME = "org.wildfly.data-source";
}
