/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.batch.impl;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.ArrayList;
import java.util.List;
import org.jboss.as.cli.Attachments;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContext.Scope;
import org.jboss.as.cli.CommandFormatException;

import org.jboss.as.cli.Util;
import org.jboss.as.cli.batch.Batch;
import org.jboss.as.cli.batch.BatchedCommand;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public class DefaultBatch implements Batch {

    private final List<BatchedCommand> commands = new ArrayList<BatchedCommand>();
    private final Attachments attachments = new Attachments();

    /* (non-Javadoc)
     * @see org.jboss.as.cli.batch.Batch#getCommands()
     */
    @Override
    public List<BatchedCommand> getCommands() {
        return commands;
    }

    @Override
    public void add(BatchedCommand cmd) {
        checkNotNullParam("cmd", cmd);
        commands.add(cmd);
    }

    @Override
    public void clear() {
        commands.clear();
    }

    @Override
    public void remove(int lineNumber) {
        ensureRange(lineNumber);
        commands.remove(lineNumber);
    }

    @Override
    public void set(int index, BatchedCommand cmd) {
        ensureRange(index);
        commands.set(index, cmd);
    }

    protected void ensureRange(int lineNumber) {
        if(lineNumber < 0 || lineNumber > commands.size() - 1) {
            throw new IndexOutOfBoundsException(lineNumber + " isn't in range [0.." + (commands.size() - 1) + "]");
        }
    }

    @Override
    public int size() {
        return commands.size();
    }

    @Override
    public void move(int currentIndex, int newIndex) {
        ensureRange(currentIndex);
        ensureRange(newIndex);
        if(currentIndex == newIndex) {
            return;
        }

        BatchedCommand cmd = commands.get(currentIndex);
        int step = newIndex > currentIndex ? 1 : -1;
        for(int i = currentIndex; i != newIndex; i += step) {
            commands.set(i, commands.get(i + step));
        }
        commands.set(newIndex, cmd);
    }

    @Override
    public ModelNode toRequest() {
        final ModelNode composite = new ModelNode();
        composite.get(Util.OPERATION).set(Util.COMPOSITE);
        composite.get(Util.ADDRESS).setEmptyList();
        final ModelNode steps = composite.get(Util.STEPS);
        for (BatchedCommand cmd : commands) {
            CommandContext ctx = cmd.getCommandContext();
            ModelNode request = cmd.getRequest();
            if (ctx.getConfig().isValidateOperationRequests()) {
                try {
                    ctx.set(Scope.REQUEST, Util.DESCRIPTION_RESPONSE,
                            cmd.getDescriptionResponse());
                    ModelNode opDescOutcome = Util.validateRequest(ctx, request);
                    if (opDescOutcome != null) { // operation has params that might need to be replaced
                        Util.replaceFilePathsWithBytes(request, opDescOutcome);
                    }
                } catch (CommandFormatException ex) {
                    throw new RuntimeException(ex);
                } finally {
                    ctx.remove(CommandContext.Scope.REQUEST,
                            Util.DESCRIPTION_RESPONSE);
                }
            }
            steps.add(request);
        }
        return composite;
    }

    @Override
    public Attachments getAttachments() {
        return attachments;
    }
}
