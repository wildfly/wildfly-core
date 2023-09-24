/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.batch.impl;

import static org.wildfly.common.Assert.checkNotNullParam;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.batch.BatchedCommand;
import org.jboss.dmr.ModelNode;
import org.jboss.as.cli.handlers.ResponseHandler;

/**
 *
 * @author Alexey Loubyansky
 */
public class DefaultBatchedCommand implements BatchedCommand {

    private final String command;
    private final ModelNode request;
    private final ModelNode description;
    private final CommandContext ctx;
    private final ResponseHandler handler;

    public DefaultBatchedCommand(CommandContext ctx, String command, ModelNode request, ResponseHandler handler) {
        this.command = checkNotNullParam("command", command);
        this.request = checkNotNullParam("request", request);
        this.ctx = checkNotNullParam("ctx", ctx);
        // Keep a ref on description to speedup validation.
        description = (ModelNode) ctx.get(CommandContext.Scope.REQUEST,
                Util.DESCRIPTION_RESPONSE);
        this.handler = handler;
    }

    @Override
    public ModelNode getDescriptionResponse() {
        return description;
    }

    @Override
    public CommandContext getCommandContext() {
        return ctx;
    }

    public String getCommand() {
        return command;
    }

    public ModelNode getRequest() {
        return request;
    }

    @Override
    public ResponseHandler getResponseHandler() {
        return handler;
    }
}
