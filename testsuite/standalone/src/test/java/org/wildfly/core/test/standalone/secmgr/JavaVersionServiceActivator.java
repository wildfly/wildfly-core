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
