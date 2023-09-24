/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.http.server;

import static io.undertow.predicate.Predicates.not;
import static io.undertow.predicate.Predicates.path;
import static io.undertow.predicate.Predicates.suffixes;
import static io.undertow.util.Headers.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.Map;

import io.undertow.attribute.ExchangeAttributes;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PredicateHandler;
import io.undertow.server.handlers.RedirectHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;
import org.xnio.IoUtils;


/**
 * Utility methods used for HTTP based domain management.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class DomainUtil {

    private static final String NOCACHE_JS = ".nocache.js";
    private static final String INDEX_HTML = "index.html";
    private static final String APP_HTML = "App.html";
    private static final String DEFAULT_RESOURCE = "/" + INDEX_HTML;
    private static final String USE_STREAM_AS_RESPONSE = "useStreamAsResponse";
    private static final HttpString USE_STREAM_AS_RESPONSE_HEADER = new HttpString("org.wildfly.useStreamAsResponse");

    public static void writeResponse(final HttpServerExchange exchange, final int status, ModelNode response,
            OperationParameter operationParameter) {

        exchange.setStatusCode(status);

        final HeaderMap responseHeaders = exchange.getResponseHeaders();
        final String contentType = operationParameter.isEncode() ? Common.APPLICATION_DMR_ENCODED : Common.APPLICATION_JSON;
        responseHeaders.put(Headers.CONTENT_TYPE, contentType + "; charset=" + Common.UTF_8);

        writeCacheHeaders(exchange, status, operationParameter);

        if (operationParameter.isGet() && status == 200) {
            // For GET request the response is purely the model nodes result. The outcome
            // is not send as part of the response but expressed with the HTTP status code.
            response = response.get(RESULT);
        }
        try {
            byte[] data = getResponseBytes(response, operationParameter);
            responseHeaders.put(Headers.CONTENT_LENGTH, data.length);
            exchange.getResponseSender().send(ByteBuffer.wrap(data));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    static void writeStreamResponse(final HttpServerExchange exchange,
                                     final OperationResponse operationResponse,
                                     final int streamIndex,
                                     OperationParameter operationParameter) {

        assert !exchange.isInIoThread(); // The WFCORE-1777 solution assumes we are not. And we shouldn't be

        exchange.setStatusCode(200);

        final HeaderMap responseHeaders = exchange.getResponseHeaders();
        final OperationResponse.StreamEntry entry = operationResponse.getInputStreams().get(streamIndex);
        final String mimeType = determineMimeType(entry, exchange);
        responseHeaders.put(Headers.CONTENT_TYPE, mimeType + "; charset=" + Common.UTF_8);

        writeCacheHeaders(exchange, 200, operationParameter);

        final Sender sender = exchange.getResponseSender();
        class ServeTask implements IoCallback, Runnable {
            private final byte[] buffer = new byte[1024];//TODO: we should be pooling these

            public void run() {
                try {
                    final InputStream inputStream = entry.getStream();
                    int res = inputStream.read(buffer);
                    if (res == -1) {
                        //we are done, clean up and return
                        IoUtils.safeClose(operationResponse);
                        return;
                    }
                    sender.send(ByteBuffer.wrap(buffer, 0, res), this);
                } catch (IOException e) {
                    onException(exchange, sender, e);
                }

            }
            @Override
            public void onComplete(HttpServerExchange exchange, Sender sender) {
                assert !exchange.isInIoThread(); // The WFCORE-1777 solution assumes we are not
                run();
            }

            @Override
            public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                IoUtils.safeClose(operationResponse);
                if (!exchange.isResponseStarted()) {
                    exchange.setStatusCode(500);
                }
            }
        }

        new ServeTask().run();
    }

    private static String determineMimeType(OperationResponse.StreamEntry entry, HttpServerExchange exchange) {
        // We see if the type provided by the response "matches" the ACCEPT header; if yes, use it
        // If not, use application/octet-stream to trigger the browser to treat it as a download
        String entryType = entry.getMimeType();
        HeaderValues headerValues = exchange.getRequestHeaders().get(Headers.ACCEPT);
        if (headerValues == null || headerValues.isEmpty()) {
            // The browser doesn't care
            return entryType;
        }
        String wildCard = null;
        int slash = entryType.indexOf('/');
        if (slash > 0) {
            wildCard = entryType.substring(0, slash) + "/*";
        }
        for (String acceptable : headerValues) {
            if ("*/*".equals(acceptable) || acceptable.contains(entryType) || (wildCard != null && acceptable.contains(wildCard))) {
                return entryType;
            }
        }

        return "application/octet-stream";
    }

    private static byte[] getResponseBytes(final ModelNode modelNode, final OperationParameter operationParameter) throws IOException {
        if (operationParameter.isEncode()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BufferedOutputStream out = new BufferedOutputStream(baos);
            modelNode.writeBase64(out);
            out.flush();
            return baos.toByteArray();
        } else {
            String json = modelNode.toJSONString(!operationParameter.isPretty());
            return json.getBytes(StandardCharsets.UTF_8);
        }
    }

    static void writeCacheHeaders(final HttpServerExchange exchange, final int status, final OperationParameter operationParameter) {
        final HeaderMap responseHeaders = exchange.getResponseHeaders();

        // No need to send this in a 304
        // See http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.5
        if (operationParameter.getMaxAge() > 0 && status != 304) {
            responseHeaders.put(Headers.CACHE_CONTROL, "max-age=" + operationParameter.getMaxAge() + ", private, must-revalidate");
        }
        if (operationParameter.getEtag() != null) {
            responseHeaders.put(Headers.ETAG, operationParameter.getEtag().toString());
        }
    }

    /**
     * Based on the current request represented by the HttpExchange construct a complete URL for the supplied path.
     *
     * @param exchange - The current HttpExchange
     * @param path - The path to include in the constructed URL
     * @return The constructed URL
     */
    public static String constructUrl(final HttpServerExchange exchange, final String path) {
        final HeaderMap headers = exchange.getRequestHeaders();
        String host = headers.getFirst(HOST);
        String protocol = exchange.getConnection().getSslSessionInfo() != null ? "https" : "http";

        return protocol + "://" + host + path;
    }

    static ResourceHandlerDefinition createStaticContentHandler(ResourceManager resource, String context) {
        final io.undertow.server.handlers.resource.ResourceHandler handler = new io.undertow.server.handlers.resource.ResourceHandler(resource)
                .setCacheTime(60 * 60 * 24 * 31)
                .setAllowed(not(path("META-INF")))
                .setResourceManager(resource)
                .setDirectoryListingEnabled(false)
                .setCachable(not(suffixes(NOCACHE_JS, APP_HTML, INDEX_HTML)));

        // avoid clickjacking attacks: console must not be included in (i)frames
        SetHeaderHandler frameHandler = new SetHeaderHandler(handler, "X-Frame-Options", "SAMEORIGIN");
        // we also need to setup the default resource redirect
        PredicateHandler predicateHandler = new PredicateHandler(path("/"), new RedirectHandler(ExchangeAttributes.constant(context + DEFAULT_RESOURCE)), frameHandler);
        return new ResourceHandlerDefinition(context, DEFAULT_RESOURCE, predicateHandler);
    }

    static int getStreamIndex(final HttpServerExchange exchange, final HeaderMap requestHeaders) {
        // First check for an HTTP header
        int result = getStreamIndex(requestHeaders.get(USE_STREAM_AS_RESPONSE_HEADER));
        if (result == -1) {
            // Nope. Now check for a URL query parameter
            Map<String, Deque<String>> queryParams = exchange.getQueryParameters();
            result = getStreamIndex(queryParams.get(USE_STREAM_AS_RESPONSE));
        }
        return result;
    }

    private static int getStreamIndex(Deque<String> holder) {
        int result;
        if (holder != null) {
            if (!holder.isEmpty() && !holder.getFirst().isEmpty()) {
                result = Integer.parseInt(holder.getFirst());
            } else {
                result = 0;
            }
        } else {
            result = -1;
        }
        return result;
    }
}
