/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.as.test.manualmode.management.cli;

import java.io.IOException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandHandler;
import org.jboss.as.cli.CommandHandlerProvider;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.handlers.BaseOperationCommand;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.ArgumentWithoutValue;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author jdenise@redhat.com
 */
public class LongOperationProvider implements CommandHandlerProvider {

    private class LongOperation extends BaseOperationCommand {

        private final ArgumentWithValue waitTime;

        private final ArgumentWithoutValue local;

        private LongOperation(CommandContext ctx) {
            super(ctx, "take-your-time", false);
            waitTime = new ArgumentWithValue(this, 0, "time");
            local = new ArgumentWithoutValue(this, "--local");
        }

        @Override
        public boolean isAvailable(CommandContext ctx) {
            return true;
        }

        @Override
        public boolean isBatchMode(CommandContext ctx) {
            return true;
        }

        @Override
        public void handle(CommandContext ctx) throws CommandLineException {
            takeTime(ctx);
        }

        private void takeTime(CommandContext ctx) throws CommandLineException {
            if(local.isPresent(ctx.getParsedCommandLine())) {
                local(ctx);
            } else {
                remote(ctx);
            }
        }

        private void local(CommandContext ctx) throws CommandLineException {
            String t = waitTime.getValue(ctx.getParsedCommandLine(), true);
            int time = Integer.parseInt(t);
            try {
                Thread.sleep(time);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new CommandLineException("Interrupted");
            }
        }

        private void remote(CommandContext ctx) throws CommandLineException {
            ModelNode req = buildRequestWithoutHeaders(ctx);
            ModelControllerClient client = ctx.getModelControllerClient();
            try {
                client.execute(req);
            } catch (IOException ex) {
                throw new CommandLineException(ex);
            }
        }

        @Override
        protected ModelNode buildRequestWithoutHeaders(CommandContext ctx) throws CommandFormatException {
            String t = waitTime.getValue(ctx.getParsedCommandLine(), true);
            int time = Integer.parseInt(t);
            DefaultCallbackHandler parser = new DefaultCallbackHandler();
            OperationRequestAddress address = new DefaultOperationRequestAddress();
            address.toNode("subsystem", "blocker-test");
            parser.parse(address, ":block(block-point=MODEL,block-time=" + time + ")", ctx);
            return parser.toOperationRequest(ctx);
        }
    }

    @Override
    public CommandHandler createCommandHandler(CommandContext ctx) {
        return new LongOperation(ctx);
    }

    @Override
    public boolean isTabComplete() {
        return true;
    }

    @Override
    public String[] getNames() {
        String[] names = {"take-your-time"};
        return names;
    }

}
