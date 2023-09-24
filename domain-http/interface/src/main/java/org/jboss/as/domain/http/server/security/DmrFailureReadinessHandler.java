/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.http.server.security;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLED_BACK;
import static org.jboss.as.domain.http.server.DomainUtil.constructUrl;
import static org.jboss.as.domain.http.server.DomainUtil.writeResponse;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import java.io.IOException;
import java.util.function.Function;

import io.undertow.util.Methods;
import org.jboss.as.domain.http.server.logging.HttpServerLogger;
import org.jboss.as.domain.http.server.OperationParameter;
import org.jboss.dmr.ModelNode;

/**
 * A RealmReadinessFilter implementation to report by DMR failure that requests can not be processed as the realm is not ready.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class DmrFailureReadinessHandler extends RealmReadinessHandler {

    private final String redirectTo;

    public DmrFailureReadinessHandler(final Function<HttpServerExchange, Boolean> readyFunction, final HttpHandler next, final String redirectTo) {
        super(readyFunction, next);
        this.redirectTo = redirectTo;
    }

    /**
     * @see org.jboss.as.domain.http.server.security.RealmReadinessHandler#rejectRequest(io.undertow.server.HttpServerExchange)
     */
    @Override
    void rejectRequest(HttpServerExchange exchange) throws IOException {
        ModelNode rejection = new ModelNode();
        rejection.get(OUTCOME).set(FAILED);
        rejection.get(FAILURE_DESCRIPTION).set(HttpServerLogger.ROOT_LOGGER.realmNotReadyMessage(constructUrl(exchange, redirectTo)));
        rejection.get(ROLLED_BACK).set(Boolean.TRUE.toString());

        // Keep the response visible so it can easily be seen in network traces.
        boolean get = exchange.getRequestMethod().equals(Methods.GET);
        OperationParameter operationParameter = new OperationParameter.Builder(get)
                .encode(false)
                .pretty(true)
                .build();
        writeResponse(exchange, 403, rejection, operationParameter);
    }
}