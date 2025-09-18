/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.http.server;

import static io.undertow.server.handlers.ResponseCodeHandler.HANDLE_404;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import io.undertow.io.IoCallback;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.Headers;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class Common {

    public static final HttpHandler MOVED_PERMANENTLY = new ContentSecurityPolicyHeaderHandler(new ResponseCodeHandler(301));
    public static final HttpHandler TEMPORARY_REDIRECT = new ContentSecurityPolicyHeaderHandler(new ResponseCodeHandler(307));
    public static final HttpHandler UNAUTHORIZED = new ContentSecurityPolicyHeaderHandler(new ResponseCodeHandler(403));
    public static final HttpHandler NOT_FOUND = new ContentSecurityPolicyHeaderHandler(HANDLE_404);
    public static final HttpHandler METHOD_NOT_ALLOWED_HANDLER = new ContentSecurityPolicyHeaderHandler(new ResponseCodeHandler(405));
    public static final HttpHandler UNSUPPORTED_MEDIA_TYPE = new ContentSecurityPolicyHeaderHandler(new ResponseCodeHandler(415));
    public static final HttpHandler INTERNAL_SERVER_ERROR = new ContentSecurityPolicyHeaderHandler(new ResponseCodeHandler(500));
    public static final HttpHandler SERVICE_UNAVAIABLE = new ContentSecurityPolicyHeaderHandler(new ResponseCodeHandler(503));

    static final String APPLICATION_DMR_ENCODED = "application/dmr-encoded";
    static final String APPLICATION_JSON = "application/json";
    static final String TEXT_PLAIN = "text/plain";
    static final String TEXT_HTML = "text/html";
    static final int ONE_WEEK = 7 * 24 * 60 * 60;

    static final String UTF_8 = "utf-8";

    static void sendError(HttpServerExchange exchange, boolean encode, String msg) {
        int errorCode = getErrorResponseCode(msg);
        sendError(exchange, encode, new ModelNode(msg == null ? "" : msg), errorCode);
    }

    static void sendError(HttpServerExchange exchange, boolean encode, ModelNode msg) {
        int errorCode = getErrorResponseCode(msg.asString());
        sendError(exchange, encode, msg, errorCode);
    }

    static void sendError(HttpServerExchange exchange, boolean encode, ModelNode msg, int errorCode) {
        if(encode) {

            try {
                ModelNode response = new ModelNode();
                response.set(msg);

                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                response.writeBase64(bout);
                byte[] bytes = bout.toByteArray();

                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, APPLICATION_DMR_ENCODED+ "; charset=" + UTF_8);
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, String.valueOf(bytes.length));
                exchange.setStatusCode(errorCode);

                exchange.getResponseSender().send(new String(bytes, StandardCharsets.UTF_8), IoCallback.END_EXCHANGE);

            } catch (IOException e) {
                // fallback, should not happen
                sendError(exchange, false, msg);
            }

        }
        else {
            sendTextError(exchange, msg, errorCode, APPLICATION_JSON);
        }
    }

    public static void sendPlainTextError(HttpServerExchange exchange, String msg, int errorCode) {
        sendTextError(exchange, new ModelNode(msg == null ? "" : msg), errorCode, TEXT_PLAIN);
    }

    private static void sendTextError(HttpServerExchange exchange, ModelNode msg, int errorCode, String contentType) {
        StringWriter stringWriter = new StringWriter();
        final PrintWriter print = new PrintWriter(stringWriter);
        try {
            msg.writeJSONString(print, false);
        } finally {
            print.flush();
            stringWriter.flush();
            print.close();
        }

        String msgString = stringWriter.toString();
        byte[] bytes = msgString.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType + "; charset=" + UTF_8);
        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, String.valueOf(bytes.length));
        exchange.setStatusCode(errorCode);

        exchange.getResponseSender().send(msgString, IoCallback.END_EXCHANGE);
    }

    private static int getErrorResponseCode(String failureMsg) {
        // WFLY-2037. This is very hacky; better would be something like an internal failure-http-code that
        // is set on the response from the OperationFailedException and stripped from non-HTTP interfaces.
        // But this will do for now.
        int result = 500;
        if (failureMsg != null && failureMsg.contains("WFLYCTL0313")) {
            result = 403;
        }
        return result;
    }

}
