/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.function.Supplier;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.security.auth.server.HttpAuthenticationFactory;

/**
 * A very trivial {@link Service} implementation where creation of the value type can easily be wrapped using a {@link Supplier}
 * and the scheduled executor in HttpAuthenticationFactory is shut down.
 *
 * @author <a href="mailto:aabdelsa@gmail.com">Ashley Abdel-Sayed</a>
 */

class HttpAuthenticationFactoryService implements Service<HttpAuthenticationFactory> {

    final Supplier<HttpAuthenticationFactory> httpAuthenticationFactorySupplier;
    private volatile HttpAuthenticationFactory httpAuthenticationFactory;

    HttpAuthenticationFactoryService(Supplier<HttpAuthenticationFactory> httpAuthenticationFactorySupplier) {
        this.httpAuthenticationFactorySupplier = checkNotNullParam("httpAuthenticationFactorySupplier", httpAuthenticationFactorySupplier);
    }

    @Override
    public void start(StartContext context) throws StartException {
        httpAuthenticationFactory = httpAuthenticationFactorySupplier.get();
    }

    @Override
    public void stop(StopContext context) {
        httpAuthenticationFactory.shutdownAuthenticationMechanismFactory();
        httpAuthenticationFactory = null;
    }

    @Override
    public HttpAuthenticationFactory getValue() throws IllegalStateException, IllegalArgumentException {
        return httpAuthenticationFactory;
    }

}
