/*
 * Copyright 2021 Red Hat, Inc.
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

package org.wildfly.extension.elytron.expression;

import static org.wildfly.common.Assert.checkNotNullParam;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;
import static org.wildfly.security.encryption.CipherUtil.decrypt;
import static org.wildfly.security.encryption.CipherUtil.encrypt;

import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Map;

import javax.crypto.SecretKey;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.wildfly.common.function.ExceptionBiConsumer;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.security.credential.SecretKeyCredential;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.CredentialStoreException;

/**
 * An Elytron backed {@code ExpressionResolver} implementation to handle the decryption of encrypted expressions.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ElytronExpressionResolver implements ExpressionResolver {

    private static final String CREDENTIAL_STORE_API_CAPABILITY = "org.wildfly.security.credential-store-api";

    private volatile boolean initialised = false;
    private final ThreadLocal<String> initialisingFor = new ThreadLocal<>();
    private volatile OperationFailedException firstFailure = null;

    private final ExceptionBiConsumer<ElytronExpressionResolver, OperationContext, OperationFailedException> configurator;

    private volatile String prefix;
    private volatile String completePrefix;
    private volatile String defaultResolver;
    private volatile Map<String, ResolverConfiguration> resolverConfigurations;

    public ElytronExpressionResolver(ExceptionBiConsumer<ElytronExpressionResolver, OperationContext, OperationFailedException> configurator) {
        this.configurator = configurator;
    }
    @Override
    public ModelNode resolveExpressions(ModelNode node) throws OperationFailedException {
        // We know this will fail but it is an internal bug if we come in this way.
        return resolveExpressions(node, null);
    }

    @Override
    public ModelNode resolveExpressions(ModelNode node, OperationContext context) throws OperationFailedException {
        checkNotNullParam("context", context);

        String fullExpression = node.asString();
        if (fullExpression.length() > 3) {
            System.out.println(String.format("Being asked to resolve expression '%s'", fullExpression));
            String expression = fullExpression.substring(2, fullExpression.length() - 1);

            ensureInitialised(fullExpression, context);

            if (expression.startsWith(completePrefix)) {
                int delimiter = expression.indexOf(':', completePrefix.length());
                String resolver = delimiter > 0 ? expression.substring(completePrefix.length(), delimiter) : defaultResolver;
                System.out.println("Resolver = " + resolver);
                if (resolver == null) {
                    throw new IllegalStateException("No resolver specified.");
                }

                ResolverConfiguration resolverConfiguration = resolverConfigurations.get(resolver);
                if (resolverConfiguration == null) {
                    throw new IllegalStateException("Resolver configuration not found");
                }

                ExceptionFunction<OperationContext, CredentialStore, OperationFailedException> credentialStoreApi = context
                        .getCapabilityRuntimeAPI(CREDENTIAL_STORE_API_CAPABILITY, resolverConfiguration.getCredentialStore(),
                                ExceptionFunction.class);
                CredentialStore credentialStore = credentialStoreApi.apply(context);
                SecretKey secretKey;
                try {
                    SecretKeyCredential credential = credentialStore.retrieve(resolverConfiguration.getAlias(),
                            SecretKeyCredential.class);
                    secretKey = credential.getSecretKey();
                } catch (CredentialStoreException e) {
                    throw ROOT_LOGGER.unableToLoadCredential(e);
                }

                String token = expression.substring(expression.lastIndexOf(':') + 1);
                System.out.println("Token = " + token);

                try {
                    String clearText = decrypt(token, secretKey);
                    System.out.println("Clear Text = " + clearText);

                    return new ModelNode(clearText);
                } catch (GeneralSecurityException e) {
                    throw new OperationFailedException(e);
                }

            }
        }
        return null;
    }

    public String createExpression(final String resolver, final String clearText, OperationContext context) throws OperationFailedException {
        ensureInitialised(null, context);
        String resolvedResolver = resolver != null ? resolver : defaultResolver;
        if (resolvedResolver == null) {
            throw ROOT_LOGGER.noResolverSpecifiedAndNoDefault();
        }

        ResolverConfiguration resolverConfiguration = resolverConfigurations.get(resolvedResolver);
        if (resolverConfiguration == null) {
            throw ROOT_LOGGER.noResolverWithSpecifiedName(resolvedResolver);
        }

        ExceptionFunction<OperationContext, CredentialStore, OperationFailedException> credentialStoreApi = context.getCapabilityRuntimeAPI(CREDENTIAL_STORE_API_CAPABILITY,
                resolverConfiguration.getCredentialStore(), ExceptionFunction.class);
        CredentialStore credentialStore = credentialStoreApi.apply(context);
        SecretKey secretKey;
        try {
            SecretKeyCredential credential = credentialStore.retrieve(resolverConfiguration.getAlias(), SecretKeyCredential.class);
            secretKey = credential.getSecretKey();
        } catch (CredentialStoreException e) {
            throw ROOT_LOGGER.unableToLoadCredential(e);
        }

        String cipherTextToken;
        try {
            cipherTextToken = encrypt(clearText, secretKey);
        } catch (GeneralSecurityException e) {
            throw ROOT_LOGGER.unableToEncryptClearText(e);
        }

        String expression = resolver == null ? String.format("${%s::%s}", prefix, cipherTextToken)
                : String.format("${%s::%s:%s}", prefix, resolvedResolver, cipherTextToken);

        return expression;
    }

    public ElytronExpressionResolver setPrefix(final String prefix) {
        this.prefix = prefix;
        this.completePrefix = prefix + "::";

        return this;
    }

    public ElytronExpressionResolver setDefaultResolver(final String defaultResolver) {
        this.defaultResolver = defaultResolver;

        return this;
    }

    public ElytronExpressionResolver setResolverConfigurations(final Map<String, ResolverConfiguration> resolverConfigurations) {
        this.resolverConfigurations = Collections.unmodifiableMap(resolverConfigurations);

        return this;
    }

    private void ensureInitialised(String initialisingFor, OperationContext context) throws OperationFailedException {
        if (initialised == false) {

            if (firstFailure != null) {
                // We wrap the original Exception to ensure we have an appropriate stack trace on the
                // subsequent error.
                throw ROOT_LOGGER.expressionResolverInitialisationAlreadyFailed(firstFailure);
            }

            if (initialisingFor != null) {
                String existingInitialisation = this.initialisingFor.get();
                if (existingInitialisation != null) {
                    throw ROOT_LOGGER.cycleDetectedInitialisingExpressionResolver(existingInitialisation,
                            existingInitialisation);
                }
            }

            synchronized (this) {
                if (initialised == false) {
                    try {
                        this.initialisingFor.set(initialisingFor);
                        configurator.accept(this, context);
                        initialised = true;
                    } catch (OperationFailedException e) {
                        firstFailure = e;
                        throw e;
                    } finally {
                        this.initialisingFor.set(null);
                    }
                }
            }
        }
    }

    public static class ResolverConfiguration {

        private final String credentialStore;
        private final String alias;

        public ResolverConfiguration(final String credentialStore, final String alias) {
            this.credentialStore = checkNotNullParam("credentialStore", credentialStore);
            this.alias = checkNotNullParam("alias", alias);
        }

        public String getCredentialStore() {
            return credentialStore;
        }

        public String getAlias() {
            return alias;
        }
    }



}
