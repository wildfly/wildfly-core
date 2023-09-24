/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.http.server;

import static org.jboss.as.domain.http.server.cors.CorsUtil.matchOrigin;
import static org.jboss.as.domain.http.server.logging.HttpServerLogger.ROOT_LOGGER;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.jboss.as.controller.ModelController;
import org.jboss.as.domain.http.server.cors.CorsUtil;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class DomainApiCheckHandler implements HttpHandler {

    static final String PATH = "/management";
    static final String GENERIC_CONTENT_REQUEST = PATH + "-upload";
    private static final String ADD_CONTENT_REQUEST = PATH + "/add-content";

    private final HttpHandler domainApiHandler;
    private final HttpHandler addContentHandler;
    private final HttpHandler genericOperationHandler;
    private final Collection<String> allowedOrigins = new ArrayList<String>();
    private final ConsoleAvailability consoleAvailability;


    DomainApiCheckHandler(final ModelController modelController, final Collection<String> allowedOrigins, final ConsoleAvailability consoleAvailability) {
        this.consoleAvailability = consoleAvailability;
        domainApiHandler = new EncodingHandler.Builder().build(Collections.<String,Object>emptyMap()).wrap(new DomainApiHandler(modelController));
        addContentHandler = new DomainApiUploadHandler(modelController);
        genericOperationHandler = new EncodingHandler.Builder().build(Collections.<String,Object>emptyMap()).wrap(new DomainApiGenericOperationHandler(modelController));
        if (allowedOrigins != null) {
            for (String allowedOrigin : allowedOrigins) {
                this.allowedOrigins.add(CorsUtil.sanitizeDefaultPort(allowedOrigin));
            }
        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (!commonChecks(exchange)) {
            return;
        }

        if (Methods.POST.equals(exchange.getRequestMethod())) {
            boolean isAddContent = ADD_CONTENT_REQUEST.equals(exchange.getRequestPath());
            boolean isGeneric = GENERIC_CONTENT_REQUEST.equals(exchange.getRequestPath());
            if (isAddContent) {
                addContentHandler.handleRequest(exchange);
                return;
            } else if (isGeneric) {
                genericOperationHandler.handleRequest(exchange);
                return;
            }
            if (!checkPostContentType(exchange)) {
                return;
            }
        }

        domainApiHandler.handleRequest(exchange);
    }

    private boolean checkPostContentType(HttpServerExchange exchange) throws Exception {
        HeaderMap headers = exchange.getRequestHeaders();
        String contentType = extractContentType(headers.getFirst(Headers.CONTENT_TYPE));
        if (!(Common.APPLICATION_JSON.equals(contentType) || Common.APPLICATION_DMR_ENCODED.equals(contentType))) {
            // RFC 2616: 14.11 Content-Encoding
            // If the content-coding of an entity in a request message is not
            // acceptable to the origin server, the server SHOULD respond with a
            // status code of 415 (Unsupported Media Type).
            ROOT_LOGGER.debug("Request rejected due to unsupported media type - should be one of (application/json,application/dmr-encoded).");
            Common.UNSUPPORTED_MEDIA_TYPE.handleRequest(exchange);
            return false;
        }
        return true;
    }

    private String extractContentType(final String fullContentType) {
        if (fullContentType == null) {
            return "";
        }
        int pos = fullContentType.indexOf(';');
        return pos < 0 ? fullContentType : fullContentType.substring(0, pos).trim();
    }

    private boolean commonChecks(HttpServerExchange exchange) throws Exception {
        // AS7-2284 If we are starting or stopping the web console won't be available, tell caller the service is unavailable and to try again
        // later. If "stopping" it's either a reload, in which case trying again will eventually succeed,
        // or it's a true process stop eventually the server will have stopped.
        if (!consoleAvailability.isAvailable()) {
            exchange.getResponseHeaders().add(Headers.RETRY_AFTER, "2"); //  2 secs is just a guesstimate
            Common.SERVICE_UNAVAIABLE.handleRequest(exchange);
            return false;
        }

        /*
         * Completely disallow OPTIONS - if the browser suspects this is a cross site request just reject it.
         */
        final HttpString requestMethod = exchange.getRequestMethod();
        if (!Methods.POST.equals(requestMethod) && !Methods.GET.equals(requestMethod)) {
            if (Methods.OPTIONS.equals(requestMethod)) {
                ROOT_LOGGER.debug("Request rejected due to 'OPTIONS' method which is not supported.");
            } else {
                ROOT_LOGGER.debug("Request rejected as method not one of (GET,POST).");
            }
            Common.METHOD_NOT_ALLOWED_HANDLER.handleRequest(exchange);
            return false;
        }

        /*
         *  Origin check, if it is set the Origin header should match the Host otherwise reject the request.
         *
         *  This check is for cross site scripted GET and POST requests.
         */
        final HeaderMap headers = exchange.getRequestHeaders();
        if (headers.contains(Headers.ORIGIN)) {
           return matchOrigin(exchange, allowedOrigins) != null;
        }
        return true;
    }
}
