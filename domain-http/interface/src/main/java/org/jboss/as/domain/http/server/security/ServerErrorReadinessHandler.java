/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.http.server.security;

import java.util.function.Function;

import org.jboss.as.domain.http.server.Common;
import org.jboss.as.domain.http.server.OperatingSystemDetector;
import org.jboss.as.domain.http.server.logging.HttpServerLogger;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * A filter to return 500 if the realm is not ready to handle authentication requests.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ServerErrorReadinessHandler extends RealmReadinessHandler {

    private final String contextName;

    public ServerErrorReadinessHandler(String contextName, final Function<HttpServerExchange, Boolean> readyFunction, final HttpHandler next) {
        super(readyFunction, next);
        this.contextName = contextName;
    }

    /**
     * @see RealmReadinessHandler#rejectRequest(HttpServerExchange)
     */
    @Override
    void rejectRequest(HttpServerExchange exchange) {
        String scriptFile = OperatingSystemDetector.INSTANCE.isWindows() ? "add-user.bat" : "add-user.sh";
        String message = HttpServerLogger.ROOT_LOGGER.realmNotReadyForSecuredManagementHandler(scriptFile);
        Common.sendPlainTextError(exchange, message, 500);
    }
}