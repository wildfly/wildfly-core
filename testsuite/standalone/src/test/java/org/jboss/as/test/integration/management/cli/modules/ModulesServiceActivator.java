/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.cli.modules;

import java.util.Deque;
import java.util.Map;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.wildfly.test.undertow.UndertowServiceActivator;

/**
 * @author Martin Simka
 */
public class ModulesServiceActivator extends UndertowServiceActivator {
    static final String ACTION_TEST_MODULE_RESOURCE = "testModuleResource";
    static final String ACTION_TEST_ABSOLUTE_RESOURCE = "testAbsoluteResource";

    @Override
    protected HttpHandler getHttpHandler() {
        return new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                Map<String, Deque<String>> parameters = exchange.getQueryParameters();
                String action = parameters.get("action").getFirst();
                if (action.equals(ACTION_TEST_MODULE_RESOURCE)) {
                    exchange.getResponseSender().send(ModuleResource.test());
                    return;
                } else if (action.equals(ACTION_TEST_ABSOLUTE_RESOURCE)) {
                    exchange.getResponseSender().send(AbsoluteResource.test());
                    return;
                }
                exchange.getResponseSender().send("wrong reponse!");
            }
        };
    }
}
