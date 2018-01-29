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

package org.jboss.as.cli.handlers.trycatch;

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

/**
 *
 * @author Alexey Loubyansky
 */
class TryCatchFinallyControlFlow implements CommandLineRedirection {

    private static final String CTX_KEY = "TRY";

    private static final int IN_TRY = 0;
    private static final int IN_CATCH = 1;
    private static final int IN_FINALLY = 2;

    static TryCatchFinallyControlFlow get(CommandContext ctx) {
        return (TryCatchFinallyControlFlow) ctx.get(Scope.CONTEXT, CTX_KEY);
    }

    private Registration registration;
    private final List<String> tryList = new ArrayList<>();
    private List<String> catchList;
    private List<String> finallyList;
    private int state;

    TryCatchFinallyControlFlow(CommandContext ctx) {
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
            if ("try".equals(cmd)) {
                throw new CommandFormatException("try is not allowed while in try block");
            }
            if("catch".equals(cmd) || "finally".equals(cmd) || "end-try".equals(cmd)) {
                registration.handle(line);
            } else {
                addLine(line.getOriginalLine());
            }
        } else {
            addLine(line.getOriginalLine());
        }

    }

    boolean isInTry() {
        return state == IN_TRY;
    }

    boolean isInFinally() {
        return state == IN_FINALLY;
    }

    void moveToCatch() throws CommandLineException {
        switch(state) {
            case IN_TRY:
                catchList = new ArrayList<>();
                state = IN_CATCH;
                break;
            case IN_CATCH:
                throw new CommandLineException("Already in catch block. Only one catch block is allowed.");
            case IN_FINALLY:
                throw new CommandLineException("Catch block is not allowed in finally");
            default:
                throw new IllegalStateException("Unexpected block id: " + state);
        }
    }

    void moveToFinally() throws CommandLineException {
        switch(state) {
            case IN_TRY:
            case IN_CATCH:
                finallyList = new ArrayList<>();
                state = IN_FINALLY;
                break;
            case IN_FINALLY:
                throw new CommandLineException("Already in finally");
            default:
                throw new IllegalStateException("Unexpected block id: " + state);
        }
    }

    private void addLine(String line) {
        switch(state) {
            case IN_TRY:
                tryList.add(line);
                break;
            case IN_CATCH:
                catchList.add(line);
                break;
            case IN_FINALLY:
                finallyList.add(line);
                break;
            default:
                throw new IllegalStateException("Unexpected block id: " + state);
        }
    }

    void run(CommandContext ctx) throws CommandLineException {

        if(state == IN_TRY) {
            throw new CommandLineException("The flow can be executed only after catch or finally.");
        }

        try {
            final ModelControllerClient client = ctx.getModelControllerClient();
            if (client == null) {
                throw new CommandLineException("The connection to the controller has not been established.");
            }

            registration.unregister();

            CommandLineException error = null;

            if (tryList.isEmpty()) {
                throw new CommandLineException("The try block is empty");
            }

            try {
                executeBlock(ctx, tryList, "try");
            } catch(CommandLineException eTry) {
                if(catchList == null) {
                    error = eTry;
                } else {
                    try {
                        executeBlock(ctx, catchList, "catch");
                    } catch(CommandLineException eCatch) {
                        error = eCatch;
                    }
                }
            }

            try {
                executeBlock(ctx, finallyList, "finally");
            } catch (CommandLineException eFinally) {
                // Try or catch exception are hidden by the finally exception.
                // Make them an exception suppressed by the finally one.
                // Doing so, they will be part of the error message displayed by
                // the CLI.
                if (error != null) {
                    eFinally.addSuppressed(error);
                }
                error = eFinally;
            }

            if(error != null) {
                throw error;
            }
        } finally {
            if(registration.isActive()) {
                registration.unregister();
            }
            ctx.remove(Scope.CONTEXT, CTX_KEY);
        }
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
}
