/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;


import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;
import static org.wildfly.extension.elytron.Capabilities.SCHEDULED_EXECUTOR_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.security.manager.WildFlySecurityManager.getPropertyPrivileged;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
import org.wildfly.security.auth.realm.BruteForceRealmWrapper;
import org.wildfly.security.auth.realm.SimpleMapBackedSecurityRealm;
import org.wildfly.security.auth.realm.SimpleRealmEntry;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.authz.MapAttributes;
/**
 * Container class for {@link SecurityRealm} resource definitions.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class RealmDefinitions {

    /**
     * System properties used to configure brute force protection on the security realms.
     */
    private static final String BRUTE_FORCE_ENABLED = "wildfly.elytron.realm.%s.brute-force.enabled";
    private static final String BRUTE_FORCE_MAX_FAILED_ATTEMPTS = "wildfly.elytron.realm.%s.brute-force.max-failed-attempts";
    private static final String BRUTE_FORCE_LOCKOUT_INTERVAL = "wildfly.elytron.realm.%s.brute-force.lockout-interval";
    private static final String BRUTE_FORCE_SESSION_TIMEOUT = "wildfly.elytron.realm.%s.brute-force.session-timeout";

    static final AttributeDefinition IDENTITY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.IDENTITY, ModelType.STRING, false)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final AttributeDefinition ATTRIBUTE_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ATTRIBUTE_NAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final StringListAttributeDefinition ATTRIBUTE_VALUES = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.ATTRIBUTE_VALUES)
            .setMinSize(0)
            .setRequired(false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final AttributeDefinition[] IDENTITY_REALM_ATTRIBUTES = { IDENTITY, ATTRIBUTE_NAME, ATTRIBUTE_VALUES };

    static ResourceDefinition getIdentityRealmDefinition() {
        AbstractAddStepHandler add = new TrivialAddHandler<SecurityRealm>(SecurityRealm.class, SECURITY_REALM_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<SecurityRealm> getValueSupplier(ServiceBuilder<SecurityRealm> serviceBuilder,
                    OperationContext context, ModelNode model) throws OperationFailedException {

                final String identity = IDENTITY.resolveModelAttribute(context, model).asString();
                final String attributeName = ATTRIBUTE_NAME.resolveModelAttribute(context, model).asStringOrNull();
                final List<String> attributeValues = ATTRIBUTE_VALUES.unwrap(context, model);

                return () -> {
                    final Map<String, ? extends Collection<String>> attributesMap;
                    if (attributeName != null) {
                        attributesMap = Collections.singletonMap(attributeName, Collections.unmodifiableList(attributeValues));
                    } else {
                        attributesMap = Collections.emptyMap();
                    }
                    final Map<String, SimpleRealmEntry> realmMap = Collections.singletonMap(identity, new SimpleRealmEntry(Collections.emptyList(), new MapAttributes(attributesMap)));
                    SimpleMapBackedSecurityRealm securityRealm = new SimpleMapBackedSecurityRealm();
                    securityRealm.setPasswordMap(realmMap);

                    return securityRealm;
                };
            }
        };

        return new TrivialResourceDefinition(ElytronDescriptionConstants.IDENTITY_REALM, add, IDENTITY_REALM_ATTRIBUTES, SECURITY_REALM_RUNTIME_CAPABILITY);
    }

    private static boolean isBruteForceProtectionEnabled(final String realmName) {
        return Boolean.parseBoolean(getPropertyPrivileged(String.format(BRUTE_FORCE_ENABLED, realmName), "true"));
    }

    private static int getBruteForceConfigValue(final String realmName, final String systemPropertyTemplate) {
        try {
            return Integer.parseInt(getPropertyPrivileged(String.format(systemPropertyTemplate, realmName), "-1"));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static <T extends SecurityRealm> T addBruteForceProtection(final T original, final Class<T> clazz, final ScheduledExecutorService executor,
                                                 final int maxAttempts, final int lockoutInterval, final int sessionTimeout) {
        return BruteForceRealmWrapper.create()
                .wrapping(original)
                .withExecutor(executor)
                .setMaxFailedAttempts(maxAttempts)
                .setLockoutInterval(lockoutInterval)
                .setFailureSessionTimeout(sessionTimeout)
                .wrap(clazz);
    }

    static <T extends SecurityRealm> Function<T, T> createBruteForceRealmTransformer(String name, Class<T> clazz, ServiceBuilder<?> serviceBuilder) {
            Function<T, T> transformer;
            if (isBruteForceProtectionEnabled(name)) {
                final Supplier<ScheduledExecutorService> executorSupplier =
                        serviceBuilder.requires(SCHEDULED_EXECUTOR_RUNTIME_CAPABILITY.getCapabilityServiceName());
                int maxAttempts = getBruteForceConfigValue(name, BRUTE_FORCE_MAX_FAILED_ATTEMPTS);
                int lockoutInterval = getBruteForceConfigValue(name, BRUTE_FORCE_LOCKOUT_INTERVAL);
                int sessionTimeout = getBruteForceConfigValue(name, BRUTE_FORCE_SESSION_TIMEOUT);

                ROOT_LOGGER.tracef("Applying brute force protection to '%s' security realm. maxAttempts=%d, lockoutTimeout=%d, sessionTimeout=%d"
                        , name, maxAttempts, lockoutInterval, sessionTimeout);

                transformer = (r) -> addBruteForceProtection(r, clazz, executorSupplier.get(), maxAttempts, lockoutInterval, sessionTimeout);
            } else {
                ROOT_LOGGER.tracef("Not applying brute force protection to '%s' security realm.", name);
                transformer = Function.identity();
            }

            return transformer;
    }

    static class CustomRealmBruteForceTransformer<T extends SecurityRealm> implements CustomComponentDefinition.CustomComponentTransformer<T, T> {

        private final Class<T> securityRealmClazz;

        CustomRealmBruteForceTransformer(Class<T> securityRealmClazz) {
            this.securityRealmClazz = securityRealmClazz;
        }

        @Override
        public Object prepareTransformer(String name, ServiceBuilder<?> serviceBuilder) {
            return createBruteForceRealmTransformer(name, securityRealmClazz, serviceBuilder);
        }

        @Override
        public T apply(Object o, T securityRealm) {
            return ((Function<T, T>) o).apply(securityRealm);
        }

    }

}
