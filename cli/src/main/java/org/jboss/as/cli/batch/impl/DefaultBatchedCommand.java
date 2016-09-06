/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.batch.impl;

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
        if (command == null) {
            throw new IllegalArgumentException("Command is null.");
        }
        this.command = command;
        if(request == null) {
            throw new IllegalArgumentException("Request is null.");
        }
        this.request = request;
        if (ctx == null) {
            throw new IllegalArgumentException("Context is null.");
        }
        this.ctx = ctx;
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
