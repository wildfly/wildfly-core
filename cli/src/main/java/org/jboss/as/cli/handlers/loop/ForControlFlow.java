/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.loop;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContext.Scope;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.CommandLineRedirection;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.parsing.command.CommandFormat;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 * Implements the loop iteration logic.
 *
 * @author jfdenise
 */
class ForControlFlow implements CommandLineRedirection {

    private static final String CTX_KEY = "FOR";

    static ForControlFlow get(CommandContext ctx) {
        return (ForControlFlow) ctx.get(Scope.CONTEXT, CTX_KEY);
    }

    private CommandLineRedirection.Registration registration;

    private final List<ModelNode> result;
    private final List<String> forBlock = new ArrayList<>();
    private final String varName;

    ForControlFlow(CommandContext ctx, String varName, String iterable) throws CommandLineException {
        checkNotNullParam("ctx", ctx);
        checkNotNullParam("varName", varName);
        checkNotNullParam("iterable", iterable);

        if (ctx.getVariable(varName) != null) {
            throw new CommandFormatException("Variable " + varName + " already exists.");
        }

        final ModelControllerClient client = ctx.getModelControllerClient();
        if (client == null) {
            throw new CommandLineException("The connection to the controller has not been established.");
        }

        this.varName = varName;
        ModelNode forRequest = ctx.buildRequest(iterable);

        ModelNode targetValue;
        try {
            targetValue = ctx.execute(forRequest, "for iterable");
        } catch (IOException e) {
            throw new CommandLineException("iterable request failed", e);
        }
        if (!targetValue.hasDefined(Util.RESULT)) {
            throw new CommandLineException("iterable request failed, no result");
        }
        ModelNode mn = targetValue.get(Util.RESULT);
        try {
            result = mn.asList();
        } catch (Exception ex) {
            throw new CommandLineException("for cannot be used with operations that produce a non-iterable result");
        }
        // Define the variable with a dummy value. That is required for operations in the block
        // referencing this variable otherwise parsing would fail.
        ctx.setVariable(varName, "null");
        ctx.set(Scope.CONTEXT, CTX_KEY, this);
    }

    @Override
    public void set(CommandLineRedirection.Registration registration) {
        this.registration = registration;
    }

    @Override
    public void handle(CommandContext ctx) throws CommandLineException {

        final ParsedCommandLine line = ctx.getParsedCommandLine();
        if (line.getFormat() == CommandFormat.INSTANCE) {
            // let the help through
            final String cmd = line.getOperationName();
            if ("for".equals(cmd)) {
                throw new CommandFormatException("for is not allowed while in for block");
            }
            if (line.hasProperty("--help")
                    || line.hasProperty("-h")
                    || "done".equals(cmd) || "help".equals(cmd)) {
                registration.handle(line);
                return;
            }
        }
        forBlock.add(line.getOriginalLine());
    }

    void run(CommandContext ctx, boolean discard) throws CommandLineException {
        try {
            registration.unregister();
            if (!discard) {
                for (ModelNode v : result) {
                    String value = v.asString();
                    ctx.setVariable(varName, value);
                    for (String cmd : forBlock) {
                        ctx.handle(cmd);
                    }
                }
            }
        } finally {
            ctx.setVariable(varName, null);
            if (registration.isActive()) {
                registration.unregister();
            }
            ctx.remove(Scope.CONTEXT, CTX_KEY);
        }
    }
}
