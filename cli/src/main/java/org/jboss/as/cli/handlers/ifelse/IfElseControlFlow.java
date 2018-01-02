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

package org.jboss.as.cli.handlers.ifelse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContext.Scope;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.CommandLineRedirection;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.parsing.command.CommandFormat;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 * Handles the if/else/end-if control flow.
 *
 * @author Alexey Loubyansky
 */
class IfElseControlFlow implements CommandLineRedirection {

    private static final String CTX_KEY = "IF";

    static IfElseControlFlow get(CommandContext ctx) {
        return (IfElseControlFlow) ctx.get(Scope.CONTEXT, CTX_KEY);
    }

    private Registration registration;

    private final Operation ifCondition;
    private final ModelNode ifRequest;
    private List<String> ifBlock;
    private List<String> elseBlock;
    private boolean inElse;

    IfElseControlFlow(CommandContext ctx, Operation ifCondition, String ifRequest) throws CommandLineException {
        if(ifCondition == null) {
            throw new IllegalArgumentException("Condition is null");
        }
        if(ifRequest == null) {
            throw new IllegalArgumentException("Condition request is null");
        }
        this.ifCondition = ifCondition;
        this.ifRequest = ctx.buildRequest(ifRequest);
        ctx.set(Scope.CONTEXT, CTX_KEY, this);
    }

    @Override
    public void set(Registration registration) {
        this.registration = registration;
    }

    @Override
    public void handle(CommandContext ctx) throws CommandLineException {

        final ParsedCommandLine line = ctx.getParsedCommandLine();
        if(line.getFormat() == CommandFormat.INSTANCE) {

            // let the help through
            if(line.hasProperty("--help") || line.hasProperty("-h")) {
                registration.handle(line);
                return;
            }

            final String cmd = line.getOperationName();
            if ("if".equals(cmd)) {
                throw new CommandFormatException("if is not allowed while in if block");
            }
            if("else".equals(cmd) || "end-if".equals(cmd)) {
                registration.handle(line);
            } else {
                addLine(line);
            }
        } else {
            addLine(line);
        }
    }

    void run(CommandContext ctx) throws CommandLineException {

        try {
            final ModelControllerClient client = ctx.getModelControllerClient();
            if(client == null) {
                throw new CommandLineException("The connection to the controller has not been established.");
            }

            ModelNode targetValue;
            try {
                targetValue = ctx.execute(ifRequest, "if condition");
            } catch (IOException e) {
                throw new CommandLineException("condition request failed", e);
            }
            final Object value = ifCondition.resolveValue(ctx, targetValue);
            if(value == null) {
                throw new CommandLineException("if expression resolved to a null");
            }

            registration.unregister();

            if(Boolean.TRUE.equals(value)) {
                executeBlock(ctx, ifBlock, "if");
            } else if(inElse) {
                executeBlock(ctx, elseBlock, "else");
            }
        } finally {
            if(registration.isActive()) {
                registration.unregister();
            }
            ctx.remove(Scope.CONTEXT, CTX_KEY);
        }
    }

    boolean isInIf() {
        return !inElse;
    }

    void moveToElse() {
        this.inElse = true;
    }

    private void executeBlock(CommandContext ctx, List<String> block, String blockName) throws CommandLineException {

        if(block != null && !block.isEmpty()) {
            final BatchManager batchManager = ctx.getBatchManager();
            // this is to discard a batch started by the block in case the block fails
            // as the cli remains in the batch mode in case run-batch resulted in an error
            final boolean discardActivatedBatch = !batchManager.isBatchActive();
            try {
                for (String l : block) {
                    ctx.handle(l);
                }
            } finally {
                if(discardActivatedBatch && batchManager.isBatchActive()) {
                    batchManager.discardActiveBatch();
                }
            }
        }
    }

    private void addLine(final ParsedCommandLine line) {
        if(inElse) {
            if(elseBlock == null) {
                elseBlock = new ArrayList<String>();
            }
            elseBlock.add(line.getOriginalLine());
        } else {
            if(ifBlock == null) {
                ifBlock = new ArrayList<String>();
            }
            ifBlock.add(line.getOriginalLine());
        }
    }
}
