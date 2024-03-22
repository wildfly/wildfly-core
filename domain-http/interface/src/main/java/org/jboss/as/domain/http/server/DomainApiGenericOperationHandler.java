/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.http.server;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_MECHANISM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CALLER_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USER;
import static org.jboss.as.domain.http.server.DomainUtil.writeResponse;
import static org.jboss.as.domain.http.server.logging.HttpServerLogger.ROOT_LOGGER;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Deque;
import java.util.Iterator;
import java.util.Set;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.core.security.AccessMechanism;
import org.jboss.dmr.ModelNode;
import org.xnio.IoUtils;

/**
 * Generic http POST handler accepting a single operation and multiple input streams passed as part of
 * a {@code multipart/form-data} message. The operation is required, the attachment streams are optional.
 *
 * Content-Disposition: form-data; name="operation"
 * (optional) Content-Type: application/dmr-encoded
 *
 * Content-Disposition: form-data; name="..."; filename="..."
 *
 * @author Emanuel Muckenhuber
 */
class DomainApiGenericOperationHandler implements HttpHandler {

    private static final String OPERATION = "operation";

    private static final String CLIENT_NAME = "X-Management-Client-Name";

    private static final Set<String> PREPARED_RESPONSE_OPERATIONS = ModelDescriptionConstants.RELOAD_OPERATIONS;

    private final ModelController modelController;
    private final FormParserFactory formParserFactory;


    public DomainApiGenericOperationHandler(ModelController modelController) {
        this.modelController = modelController;
        this.formParserFactory = FormParserFactory.builder().build();
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final FormDataParser parser = formParserFactory.createParser(exchange);
        if (parser == null) {
            Common.UNSUPPORTED_MEDIA_TYPE.handleRequest(exchange);
        }

        // Prevent CSRF which can occur from standard a multipart/form-data submission from a standard HTML form.
        // If the browser sends an Origin header (Chrome / Webkit) then the earlier origin check will have passed
        // to reach this point. If the browser doesn't (FireFox), then  only requests which came from Javascript,
        // which enforces same-origin policy when no Origin header is present, should be allowed. The presence of
        // a custom header indicates usage of XHR since simple forms can not set them.
        HeaderMap headers = exchange.getRequestHeaders();
        if (!headers.contains(Headers.ORIGIN) && !headers.contains(CLIENT_NAME)) {
            ROOT_LOGGER.debug("HTTP Origin or X-Management-Client-Name header is required for all multipart form data posts.");
            Common.UNAUTHORIZED.handleRequest(exchange);
            return;
        }

        // Parse the form data
        final FormData data = parser.parseBlocking();
        final OperationParameter.Builder operationParameterBuilder = new OperationParameter.Builder(false);

        // Process the operation
        final FormData.FormValue op = data.getFirst(OPERATION);
        final ModelNode operation;
        try {
            String type = op.getHeaders().getFirst(Headers.CONTENT_TYPE);
            if (Common.APPLICATION_DMR_ENCODED.equals(type)) {
                try (InputStream stream = convertToStream(op)) {
                    operation = ModelNode.fromBase64(stream);
                }
                operationParameterBuilder.encode(true);
            } else if (Common.APPLICATION_JSON.equals(stripSuffix(type))) {
                try (InputStream stream = convertToStream(op)) {
                    operation = ModelNode.fromJSONStream(stream);
                }
            } else {
                ROOT_LOGGER.debug("Content-type must be application/dmr-encoded or application/json");
                Common.UNAUTHORIZED.handleRequest(exchange);
                return;
            }
        } catch (Exception e) {
            ROOT_LOGGER.errorf("Unable to construct ModelNode '%s'", e.getMessage());
            Common.sendError(exchange, false, e.getLocalizedMessage());
            return;
        }

        // Set up the correct headers
        ModelNode opheaders = operation.get(OPERATION_HEADERS);
        opheaders.get(ACCESS_MECHANISM).set(AccessMechanism.HTTP.toString());
        opheaders.get(CALLER_TYPE).set(USER);
        // Don't allow a domain-uuid operation header from a user call
        if (opheaders.hasDefined(DOMAIN_UUID)) {
            opheaders.remove(DOMAIN_UUID);
        }

        // Process the input streams
        final OperationBuilder builder = OperationBuilder.create(operation, true);
        final Iterator<String> i = data.iterator();
        while (i.hasNext()) {
            final String name = i.next();
            final Deque<FormData.FormValue> contents = data.get(name);
            if (contents != null && !contents.isEmpty()) {
                for (final FormData.FormValue value : contents) {
                    if (value.isFile()) {
                        builder.addFileAsAttachment(value.getPath().toFile());
                    }
                }
            }
        }

        operationParameterBuilder.pretty(operation.hasDefined("json.pretty") && operation.get("json.pretty").asBoolean());
        // Response callback to handle the :reload operation
        final OperationParameter opParam = operationParameterBuilder.build();
        final ResponseCallback callback = new ResponseCallback() {
            @Override
            void doSendResponse(final OperationResponse operationResponse) {
                ModelNode response = operationResponse.getResponseNode();
                if (response.hasDefined(OUTCOME) && FAILED.equals(response.get(OUTCOME).asString())) {
                    Common.sendError(exchange, opParam.isEncode(), response);
                    return;
                }
                writeResponse(exchange, 200, response, opParam);
            }
        };

        final boolean sendPreparedResponse = sendPreparedResponse(operation);
        final ModelController.OperationTransactionControl control = sendPreparedResponse
                ? new EarlyResponseTransactionControl(callback, operation)
                : ModelController.OperationTransactionControl.COMMIT;

        OperationResponse response;
        final Operation builtOp = builder.build();
        try {
            response = modelController.execute(builtOp, OperationMessageHandler.DISCARD, control);
        } catch (Throwable t) {
            ROOT_LOGGER.modelRequestError(t);
            Common.sendError(exchange, opParam.isEncode(), t.getLocalizedMessage());
            return;
        } finally {
            // Close any input streams that were open
            if (builtOp.isAutoCloseStreams()) {
                for (InputStream in : builtOp.getInputStreams()) {
                    IoUtils.safeClose(in);
                }
            }
        }

        callback.sendResponse(response);
    }

    private InputStream convertToStream(FormData.FormValue op) throws IOException {
        if (op.isFile()) {
            return Files.newInputStream(op.getPath());
        } else {
            return new ByteArrayInputStream(op.getValue().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String stripSuffix(String contentType) {
        if (contentType == null) {
            return null;
        }

        int index = contentType.indexOf(';');

        if (index > 0) {
            contentType = contentType.substring(0, index);
        }

        return contentType;
    }

    /**
     * Determine whether the prepared response should be sent, before the operation completed. This is needed in order
     * that operations like :reload() can be executed without causing communication failures.
     *
     * @param operation the operation to be executed
     * @return {@code true} if the prepared result should be sent, {@code false} otherwise
     */
    private boolean sendPreparedResponse(final ModelNode operation) {
        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        final String op = operation.get(OP).asString();
        final int size = address.size();
        if (size == 0) {
            if (PREPARED_RESPONSE_OPERATIONS.contains(op)) {
                return true;
            } else if (op.equals(COMPOSITE)) {
                // TODO
                return false;
            } else {
                return false;
            }
        } else if (size == 1) {
            if (address.getLastElement().getKey().equals(HOST)) {
                return PREPARED_RESPONSE_OPERATIONS.contains(op);
            }
        }
        return false;
    }

}
