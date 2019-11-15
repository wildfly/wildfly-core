/*
 * Copyright 2019 Red Hat, Inc.
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

package org.jboss.as.domain.http.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * An Undertow {@link HttpHandler} that can take a set of static headers to be applied to requests against a matched path.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class StaticHeadersHandler implements HttpHandler {

    private final Map<String, List<HeaderConstant>> headersMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private final HttpHandler next;

    StaticHeadersHandler(final HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String requestPath = exchange.getRelativePath();
        for (Entry<String, List<HeaderConstant>> entry : headersMap.entrySet()) {
            if (requestPath.startsWith(entry.getKey())) {
                for (HeaderConstant constant : entry.getValue()) {
                    constant.apply(exchange);
                }
            }
        }

        next.handleRequest(exchange);
    }

    void addHeader(final String path, final String header, final String value) {
        final String actualPath = path.startsWith("/") ? path : "/" + path;
        List<HeaderConstant> headers = headersMap.computeIfAbsent(actualPath, (s) -> new ArrayList<HeaderConstant>());
        headers.add(new HeaderConstant(header, value));
    }

    private static class HeaderConstant {

        private final HttpString headerName;
        private final String value;

        HeaderConstant(final String headerName, final String value) {
            this.headerName = new HttpString(headerName);
            this.value = value;
        }

        void apply(HttpServerExchange exchange) {
            exchange.getResponseHeaders().add(headerName, value);
        }
    }

}
