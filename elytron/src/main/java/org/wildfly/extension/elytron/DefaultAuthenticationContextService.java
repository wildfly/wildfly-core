/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.SecurityActions.doPrivileged;

import java.security.PrivilegedAction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.context.ContextManager;
import org.wildfly.security.auth.client.AuthenticationContext;

/**
 * A simple {@link Service} to take an {@link AuthenticationContext} and register it as the process wide default.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class DefaultAuthenticationContextService implements Service {

    static final ServiceName SERVICE_NAME = ElytronExtension.BASE_SERVICE_NAME.append(ElytronDescriptionConstants.AUTHENTICATION_CONTEXT_REGISTRATION);

    private final Supplier<AuthenticationContext> defaultAuthenticationContextSupplier;
    private final Consumer<AuthenticationContext> valueConsumer;
    private volatile AuthenticationContext originalAuthenticationContext;

    DefaultAuthenticationContextService(Supplier<AuthenticationContext> defaultAuthenticationContextSupplier, Consumer<AuthenticationContext> valueConsumer) {
        this.defaultAuthenticationContextSupplier = defaultAuthenticationContextSupplier;
        this.valueConsumer = valueConsumer;
    }

    @Override
    public void start(StartContext context) throws StartException {
        final AuthenticationContext authenticationContext = defaultAuthenticationContextSupplier.get();
        originalAuthenticationContext = setDefaultAuthenticationContext(authenticationContext);
        valueConsumer.accept(authenticationContext);
    }

    @Override
    public void stop(StopContext context) {
        setDefaultAuthenticationContext(originalAuthenticationContext);
        originalAuthenticationContext = null;
    }

    private static final AuthenticationContext setDefaultAuthenticationContext(final AuthenticationContext authenticationContext) {
        return doPrivileged((PrivilegedAction<AuthenticationContext>) () -> {
            ContextManager<AuthenticationContext> contextManager = AuthenticationContext.getContextManager();
            AuthenticationContext original = contextManager.getGlobalDefault();
            contextManager.setGlobalDefault(authenticationContext);

            return original;
        });


    }

}
