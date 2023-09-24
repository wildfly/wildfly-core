/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.http.server;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_MECHANISM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CALLER_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USER;
import static org.jboss.as.domain.http.server.logging.HttpServerLogger.ROOT_LOGGER;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintWriter;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormData.FormValue;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.Headers;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.core.security.AccessMechanism;
import org.jboss.dmr.ModelNode;
import org.xnio.IoUtils;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class DomainApiUploadHandler implements HttpHandler {

    private final ModelController modelController;
    private final FormParserFactory formParserFactory;

    public DomainApiUploadHandler(ModelController modelController) {
        this.modelController = modelController;
        this.formParserFactory = FormParserFactory.builder().build();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final FormDataParser parser = formParserFactory.createParser(exchange);
        FormData data = parser.parseBlocking();
        for (String fieldName : data) {
            //Get all the files
            FormValue value = data.getFirst(fieldName);
            if (value.isFile()) {
                ModelNode response = null;
                InputStream in = new BufferedInputStream(new FileInputStream(value.getPath().toFile()));
                try {
                    final ModelNode dmr = new ModelNode();
                    dmr.get("operation").set("upload-deployment-stream");
                    dmr.get("address").setEmptyList();
                    dmr.get("input-stream-index").set(0);
                    ModelNode headers = dmr.get(OPERATION_HEADERS);
                    headers.get(ACCESS_MECHANISM).set(AccessMechanism.HTTP.toString());
                    headers.get(CALLER_TYPE).set(USER);

                    OperationBuilder operation = new OperationBuilder(dmr);
                    operation.addInputStream(in);
                    response = modelController.execute(dmr, OperationMessageHandler.logging, ModelController.OperationTransactionControl.COMMIT, operation.build());
                    if (!response.get(OUTCOME).asString().equals(SUCCESS)){
                        Common.sendError(exchange, false, response);
                        return;
                    }
                } catch (Throwable t) {
                    // TODO Consider draining input stream
                    ROOT_LOGGER.uploadError(t);
                    Common.sendError(exchange, false, t.getLocalizedMessage());
                    return;
                } finally {
                    IoUtils.safeClose(in);
                }

                // TODO Determine what format the response should be in for a deployment upload request.
                writeResponse(exchange, response, Common.TEXT_HTML);
                return; //Ignore later files
            }
        }
        Common.sendError(exchange, false, "No file found"); //TODO i18n
    }

    static void writeResponse(HttpServerExchange exchange, ModelNode response, String contentType) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType  + "; charset=" + Common.UTF_8);
        exchange.setStatusCode(200);

        //TODO Content-Length?
        exchange.startBlocking();

        PrintWriter print = new PrintWriter(exchange.getOutputStream());
        try {
            response.writeJSONString(print, true);
        } finally {
            IoUtils.safeClose(print);
        }
    }
}
