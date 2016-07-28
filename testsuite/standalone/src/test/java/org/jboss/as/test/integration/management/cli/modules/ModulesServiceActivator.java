/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
