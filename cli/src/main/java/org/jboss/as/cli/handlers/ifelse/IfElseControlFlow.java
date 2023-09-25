/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.handlers.ifelse;

import static org.wildfly.common.Assert.checkNotNullParam;

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
        checkNotNullParam("ctx", ctx);
        this.ifCondition = checkNotNullParam("ifCondition", ifCondition);
        this.ifRequest = ctx.buildRequest(checkNotNullParam("ifRequest", ifRequest));
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
