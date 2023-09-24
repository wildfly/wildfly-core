/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ElytronDefinition.RESTORE_DEFAULT_SSL_CONTEXT;
import static org.wildfly.extension.elytron.SecurityActions.doPrivileged;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * A simple {@link Service} to take an {@link SSLContext} and register it as the process wide default.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class DefaultSSLContextService implements Service {

    static final ServiceName SERVICE_NAME = ElytronExtension.BASE_SERVICE_NAME.append(ElytronDescriptionConstants.SSL_CONTEXT_REGISTRATION);

    private static final boolean RESTORE_SSL_CONTEXT = doPrivileged((PrivilegedAction<Boolean>) () -> Boolean.getBoolean(RESTORE_DEFAULT_SSL_CONTEXT));

    private final Supplier<SSLContext> defaultSSLContextSupplier;
    private final Consumer<SSLContext> valueConsumer;

    DefaultSSLContextService(Supplier<SSLContext> defaultSSLContextSupplier, Consumer<SSLContext> valueConsumer) {
        this.defaultSSLContextSupplier = defaultSSLContextSupplier;
        this.valueConsumer = valueConsumer;
    }

    @Override
    public void start(StartContext context) throws StartException {
        final SSLContext sslContext = defaultSSLContextSupplier.get();
        doPrivileged((PrivilegedAction<Void>) () -> {
            SSLContext.setDefault(sslContext);
            return null;
        });
        valueConsumer.accept(sslContext);
    }

    @Override
    public void stop(StopContext context) {
        // We can't set the default back to 'null' as that would cause a NullPointerException.
        // For the purpose of testing we may want to restore the default.
        if (RESTORE_SSL_CONTEXT) {
            try {
                final SSLContext defaultSSLContext = SSLContext.getInstance("Default");
                doPrivileged((PrivilegedAction<Void>) () -> {
                    SSLContext.setDefault(defaultSSLContext);
                    return null;
                });
            } catch (NoSuchAlgorithmException e) {
                ROOT_LOGGER.debug(e);
            }
        }
    }

}
