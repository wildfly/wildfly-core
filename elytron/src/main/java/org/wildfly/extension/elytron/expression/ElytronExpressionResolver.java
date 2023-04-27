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
import static org.wildfly.extension.elytron._private.ElytronCommonMessages.ROOT_LOGGER;
import static org.wildfly.security.encryption.CipherUtil.decrypt;
import static org.wildfly.security.encryption.CipherUtil.encrypt;

import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Map;

import javax.crypto.SecretKey;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.extension.ExpressionResolverExtension;
import org.jboss.as.controller.OperationClientException;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.wildfly.common.function.ExceptionBiConsumer;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.extension.elytron._private.ElytronCommonMessages;
import org.wildfly.security.credential.SecretKeyCredential;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.CredentialStoreException;

/**
 * An Elytron backed {@code ExpressionResolver} implementation to handle the decryption of encrypted expressions.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ElytronExpressionResolver implements ExpressionResolverExtension {

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
    public void initialize(OperationContext context) throws OperationFailedException {
        ensureInitialised(null, context);
    }

    @Override
    public String resolveExpression(String expression, OperationContext context) {
        checkNotNullParam("expression", expression);
        checkNotNullParam("context", context);
        return resolveExpressionInternal(expression, context, null);
    }

    /**
     * Resolves expressions found in deployment resources.
     *
     * @param expression the expression string. Cannot be {@code null}
     * @param serviceSupport support object to use to resolve the needed {@link CredentialStore}. Cannot be {@code null}
     * @return the resolved expression string or {@code null} if {@code expression} isn't a credential store expression
     *         or 'looks like' one but doesn't use the prefix supported by this resolver.
     */
    String resolveDeploymentExpression(String expression, CapabilityServiceSupport serviceSupport) {
        return resolveExpressionInternal(expression, null, serviceSupport);
    }

    private String resolveExpressionInternal(String fullExpression, OperationContext operationContext, CapabilityServiceSupport serviceSupport) {
        assert operationContext == null || serviceSupport == null;
        assert operationContext != null || serviceSupport != null;

        if (fullExpression.length() > 3) {
            String expression = fullExpression.substring(2, fullExpression.length() - 1);

            try {
                ensureInitialised(fullExpression, operationContext);
            } catch (OperationFailedException e) {
                // Shouldn't happen. Any use that provides an OperationContext should have
                // called initialize before trying to resolve. And ensureInitialized should not
                // throw OFE if no OperationContext is provided
                throw new IllegalStateException(e);
            }

            if (expression.startsWith(completePrefix)) {
                int delimiter = expression.indexOf(':', completePrefix.length());
                String resolver = delimiter > 0 ? expression.substring(completePrefix.length(), delimiter) : defaultResolver;
                if (resolver == null) {
                    throw ROOT_LOGGER.expressionResolutionWithoutResolver(fullExpression);
                }

                ResolverConfiguration resolverConfiguration = resolverConfigurations.get(resolver);
                if (resolverConfiguration == null) {
                    throw ROOT_LOGGER.invalidResolver(fullExpression);
                }

                ROOT_LOGGER.tracef("Attempting to decrypt expression '%s' using credential store '%s' and alias '%s'.",
                        fullExpression, resolverConfiguration.credentialStore, resolverConfiguration.alias);
                CredentialStore credentialStore = resolveCredentialStore(resolverConfiguration.getCredentialStore(), operationContext, serviceSupport);
                SecretKey secretKey;
                try {
                    SecretKeyCredential credential = credentialStore.retrieve(resolverConfiguration.getAlias(),
                            SecretKeyCredential.class);
                    secretKey = credential.getSecretKey();
                } catch (CredentialStoreException e) {
                    throw ROOT_LOGGER.unableToLoadCredential(e);
                }

                String token = expression.substring(expression.lastIndexOf(':') + 1);

                try {
                    return decrypt(token, secretKey);
                } catch (GeneralSecurityException e) {
                    throw ROOT_LOGGER.unableToDecryptExpression(fullExpression, e);
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

        CredentialStore credentialStore = resolveCredentialStore(resolverConfiguration.getCredentialStore(), context, null);
        SecretKey secretKey;
        try {
            SecretKeyCredential credential = credentialStore.retrieve(resolverConfiguration.getAlias(), SecretKeyCredential.class);
            if (credential == null) {
                throw ROOT_LOGGER.credentialDoesNotExist(resolverConfiguration.getAlias(), SecretKeyCredential.class.getSimpleName());
            }
            secretKey = credential.getSecretKey();
        } catch (CredentialStoreException e) {
            throw new OperationFailedException(ROOT_LOGGER.unableToLoadCredential(e));
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

    // Package-protected so ExpressionResolverRuntimeHandler can initialize
    // on behalf of the add op that adds the /subsystem=elytron/expression=encryption resource
    void ensureInitialised(String initialisingFor, OperationContext context) throws OperationFailedException {
        if (initialised == false) {

            if (firstFailure != null) {
                // We wrap the original OperationFaileException to ensure we have an appropriate stack trace on the
                // subsequent error. Wrap with IllegalStateException as we don't want to repeatedly
                // treat this failure as a user mistake; the user mistake was reported with the throw
                // of the initial OFE. Don't wrap with ResolverExtensionException because that
                // is only used if we tried to resolve an expression we know is appropriate for us,
                // and this method is called before we know that.
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
                        if (context == null) {
                            // If a caller that can't provide an OperationContext needs to initialize,
                            // there's a programming bug as this object should be initialized
                            // before any call paths are executed that don't come through
                            // the OperationStepHandlers that provide a context.
                            throw ElytronCommonMessages.ROOT_LOGGER.illegalNonManagementInitialization(getClass());
                        }
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

    /**
     * Gets the CredentialStore provided by the management resource with the given name.
     *
     * @param credentialStore the credential store resource name. Cannot be {@code null}.
     * @param operationContext OperationContext to use for capability resolution. Can be {@code null} if {@code serviceSupport} is not {@code null}
     * @param serviceSupport CapabilityServiceSupport to use for capability resolution. Can be {@code null} if {@code operationContext} is not {@code null}
     * @return the CredentialStore
     *
     * @throws ExpressionResolver.ExpressionResolutionUserException if the credential store is not initialized and a failure occurs initializing it
     * @throws ExpressionResolver.ExpressionResolutionServerException if capability lookup is disabled due to being in Stage.MODEL,
     *                               if there is no credential store resource with the given name or if there is one
     *                               but the store wasn't initialized and no OperationContext was provided to
     *                               initialize it. (Both would indicate program flaws; the former a flaw in validating
     *                               the data in a ResolverConfiguration; the latter an unexpected call pattern where
     *                               deployment resource expression resolution can occur before credential store resources
     *                               have been initialized by their OperationStepHandler.)
     */
    @SuppressWarnings("unchecked")
    private CredentialStore resolveCredentialStore(String credentialStore, OperationContext operationContext, CapabilityServiceSupport serviceSupport) {

        ExceptionFunction<OperationContext, CredentialStore, OperationFailedException> function;
        RuntimeException toThrow;
        try {
            if (operationContext != null) {
                try {
                    function = operationContext.getCapabilityRuntimeAPI(CREDENTIAL_STORE_API_CAPABILITY, credentialStore, ExceptionFunction.class);
                } catch (IllegalStateException re) {
                    if (operationContext.getCurrentStage() == OperationContext.Stage.MODEL) {
                        // Assume ISE is because capability lookups are not allowed in Stage.MODEL
                        throw ROOT_LOGGER.modelStageResolutionNotSupported(re);
                    } else {
                        throw re;
                    }
                }
            } else {
                checkNotNullParam("serviceSupport", serviceSupport);
                function = serviceSupport.getCapabilityRuntimeAPI(CREDENTIAL_STORE_API_CAPABILITY, credentialStore, ExceptionFunction.class);
            }

            return function.apply(operationContext);
        } catch (ExpressionResolver.ExpressionResolutionServerException | ExpressionResolver.ExpressionResolutionUserException ree) {
            // Initializing the CredentialStore can itself trigger expression resolution, which can fail. Just propagate those.
            toThrow = ree;
        } catch (OperationFailedException | CapabilityServiceSupport.NoSuchCapabilityException | RuntimeException e) {
            // Wrap in one of the ExpressionResolver.ResolverExtension exception types
            // so exception handlers know the expression was relevant to us
            if (e instanceof OperationClientException) {
                // Use ExpressionResolutionUserException to wrap user failures
                toThrow = ROOT_LOGGER.unableToInitializeCredentialStore(credentialStore, e.getLocalizedMessage(), e);
            } else {
                // Use ExpressionResolutionServerException to wrap other failures
                toThrow = ROOT_LOGGER.unableToResolveCredentialStore(credentialStore, e.getLocalizedMessage(), e);
            }
        }

        throw toThrow;
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
