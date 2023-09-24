/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.secmgr;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.wildfly.test.undertow.UndertowServiceActivator;

/**
 * <p>Simple handler that reads and prints the <em>java.version</em> system
 * property.</p>
 *
 * @author rmartinc
 */
public class JavaVersionServiceActivator extends UndertowServiceActivator {

    @Override
    protected HttpHandler getHttpHandler() {
        return new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
                exchange.getResponseSender().send(System.getProperty("java.version"));
            }
        };
    }
}
