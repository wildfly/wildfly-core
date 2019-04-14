/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller.extension;

import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.ParsedBootOp;
import org.jboss.as.controller._private.OperationFailedRuntimeException;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;

/**
 * Special handler that executes extension initialization in parallel.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public final class ParallelExtensionAddHandler implements OperationStepHandler {

    private final ExecutorService executor;
    private final List<ParsedBootOp> extensionAdds = new ArrayList<ParsedBootOp>();
    private ParsedBootOp ourOp;
    private final MutableRootResourceRegistrationProvider rootResourceRegistrationProvider;
    private final int maxParallelBootTasks;

    public ParallelExtensionAddHandler(final ExecutorService executorService,
                                       final int maxParallelBootTasks,
                                       MutableRootResourceRegistrationProvider rootResourceRegistrationProvider) {
        assert maxParallelBootTasks > 1; // else this handler should not be used
        this.executor = executorService;
        this.maxParallelBootTasks = maxParallelBootTasks;
        this.rootResourceRegistrationProvider = rootResourceRegistrationProvider;
    }

    public void addParsedOp(final ParsedBootOp op, final ExtensionAddHandler handler) {
        ParsedBootOp toAdd = new ParsedBootOp(op, handler);
        extensionAdds.add(toAdd);
        getParsedBootOp().addChildOperation(toAdd);
    }

    public ParsedBootOp getParsedBootOp() {
        if (ourOp == null) {
            ModelNode op = Util.getEmptyOperation("parallel-extension-add", new ModelNode().setEmptyList());
            ourOp = new ParsedBootOp(op, this);
        }
        return ourOp;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        context.addStep(getParallelExtensionInitializeStep(), OperationContext.Stage.MODEL, true);

        for (int i = extensionAdds.size() -1; i >= 0; i--) { // Reverse order so they execute in normal order!
            ParsedBootOp op = extensionAdds.get(i);
            context.addStep(op.response, op.operation, op.handler, OperationContext.Stage.MODEL, true);
        }
    }

    private OperationStepHandler getParallelExtensionInitializeStep() {

        return new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

                long start = System.currentTimeMillis();
                final GroupInitializeTask[] initializeTasks = new GroupInitializeTask[maxParallelBootTasks];
                final ManagementResourceRegistration rootResourceRegistration = rootResourceRegistrationProvider.getRootResourceRegistrationForUpdate(context);
                int taskIdx = -1;
                for (ParsedBootOp op : extensionAdds) {
                    if (taskIdx == maxParallelBootTasks - 1) {
                        taskIdx = 0;
                    } else {
                        taskIdx++;
                    }
                    String module = op.address.getLastElement().getValue();
                    ExtensionAddHandler addHandler = ExtensionAddHandler.class.cast(op.handler);
                    ExtensionInitializeTask initializeTask = new ExtensionInitializeTask(module, addHandler, rootResourceRegistration);
                    if (initializeTasks[taskIdx] == null) {
                        initializeTasks[taskIdx] = new GroupInitializeTask(initializeTask);
                    } else {
                        initializeTasks[taskIdx].loadTasks.add(initializeTask);
                    }
                }

                for (GroupInitializeTask initTask : initializeTasks) {
                    if (initTask != null) {
                        initTask.execute(executor);
                    }
                }

                for (GroupInitializeTask initTask : initializeTasks) {
                    if (initTask != null) {
                        try {
                            OperationFailedRuntimeException ofe = initTask.future.get();
                            if (ofe != null) {
                                throw ofe;
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw ControllerLogger.ROOT_LOGGER.moduleInitializationInterrupted(initTask.currentModule);
                        } catch (ExecutionException e) {
                            throw ControllerLogger.ROOT_LOGGER.failedInitializingModule(e, initTask.currentModule);
                        }
                    }
                }

                if (MGMT_OP_LOGGER.isDebugEnabled()) {
                    long elapsed = System.currentTimeMillis() - start;
                    MGMT_OP_LOGGER.debugf("Initialized extensions in [%d] ms", elapsed);
                }
            }
        };
    }

    private static class ExtensionInitializeTask implements Callable<OperationFailedRuntimeException> {

        private final String module;
        private final ExtensionAddHandler addHandler;
        private final ManagementResourceRegistration rootResourceRegistration;

        ExtensionInitializeTask(String module, ExtensionAddHandler addHandler,
                                ManagementResourceRegistration rootResourceRegistration) {
            this.module = module;
            this.addHandler = addHandler;
            this.rootResourceRegistration = rootResourceRegistration;
        }

        @Override
        public OperationFailedRuntimeException call() {
            OperationFailedRuntimeException failure = null;
            try {
                addHandler.initializeExtension(module, rootResourceRegistration);
            } catch (OperationFailedRuntimeException e) {
                failure = e;
            }
            return failure;
        }
    }

    private static class GroupInitializeTask implements Callable<OperationFailedRuntimeException> {
        private final List<ExtensionInitializeTask> loadTasks = new ArrayList<>();
        private volatile String currentModule;
        private volatile Future<OperationFailedRuntimeException> future;

        private GroupInitializeTask(ExtensionInitializeTask first) {
            this.currentModule = first.module;
            loadTasks.add(first);
        }

        @Override
        public OperationFailedRuntimeException call() throws Exception {

            for (ExtensionInitializeTask task : loadTasks) {
                currentModule = task.module;
                OperationFailedRuntimeException ex = task.call();
                if (ex != null) {
                    return ex;
                }
            }
            return null;
        }

        private void execute(ExecutorService executorService) {
            future = executorService.submit(this);
        }
    }
}
