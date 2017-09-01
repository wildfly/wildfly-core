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
package org.jboss.as.cli.impl;

import org.jboss.as.cli.AwaiterModelControllerClient;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jboss.as.cli.CliConfig;
import org.jboss.as.cli.CliEventListener;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandHandler;
import org.jboss.as.cli.CommandHistory;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.CommandLineRedirection;
import org.jboss.as.cli.ConnectionInfo;
import org.jboss.as.cli.ControllerAddress;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.batch.BatchedCommand;
import org.jboss.as.cli.operation.CommandLineParser;
import org.jboss.as.cli.operation.NodePathFormatter;
import org.jboss.as.cli.operation.OperationCandidatesProvider;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;
import org.jboss.threads.AsyncFuture;

/**
 * Implement the timeout logic when executing commands. Public for testing
 * purpose.
 *
 * @author jdenise@redhat.com
 */
public class CommandExecutor {

    private static final String CANCEL_MSG = "Cancelling running operation...";
    private static final String TIMEOUT_CANCEL_MSG = "Timeout. " + CANCEL_MSG;

    // A wrapper to allow to override ModelControllerClient.
    // Public for testing purpose.
    public class TimeoutCommandContext implements CommandContext {

        // A ModelControllerClient that tracks the latest executed Task.
        // Any attempt to make a request will fail if the command has timeouted.
        private class TimeoutModelControllerClient implements ModelControllerClient, AwaiterModelControllerClient {

            private final ModelControllerClient wrapped;

            TimeoutModelControllerClient(ModelControllerClient wrapped) {
                this.wrapped = wrapped;
            }

            @Override
            public ModelNode execute(ModelNode operation, OperationMessageHandler messageHandler) throws IOException {
                return doExecute(operation, messageHandler);
            }

            @Override
            public ModelNode execute(Operation operation, OperationMessageHandler messageHandler) throws IOException {
                return doExecute(operation, messageHandler);
            }

            @Override
            public ModelNode execute(ModelNode operation) throws IOException {
                return doExecute(operation);
            }

            @Override
            public ModelNode execute(Operation operation) throws IOException {
                return doExecute(operation);
            }

            @Override
            public AsyncFuture<ModelNode> executeAsync(ModelNode operation, OperationMessageHandler messageHandler) {
                return doExecuteAsync(operation, messageHandler);
            }

            @Override
            public AsyncFuture<ModelNode> executeAsync(Operation operation, OperationMessageHandler messageHandler) {
                return doExecuteAsync(operation, messageHandler);
            }

            @Override
            public OperationResponse executeOperation(Operation operation, OperationMessageHandler messageHandler) throws IOException {
                return doExecuteOperation(operation, messageHandler);
            }

            @Override
            public AsyncFuture<OperationResponse> executeOperationAsync(Operation operation, OperationMessageHandler messageHandler) {
                return doExecuteOperationAsync(operation, messageHandler);
            }

            @Override
            public ModelNode execute(ModelNode operation, boolean awaitClose) throws IOException {
                if (!(wrapped instanceof AwaiterModelControllerClient)) {
                    throw new IOException("Unsupported ModelControllerClient implementation " + wrapped.getClass().getName());
                }
                ModelNode response = execute(operation);
                if (!Util.isSuccess(response)) {
                    return response;
                }
                ((AwaiterModelControllerClient) wrapped).awaitClose(awaitClose);
                return response;
            }

            @Override
            public void awaitClose(boolean awaitClose) throws IOException {
                if (!(wrapped instanceof AwaiterModelControllerClient)) {
                    throw new IOException("Unsupported ModelControllerClient implementation " + wrapped.getClass().getName());
                }
                ((AwaiterModelControllerClient) wrapped).awaitClose(awaitClose);
            }

            @Override
            public boolean isConnected() {
                if (!(wrapped instanceof AwaiterModelControllerClient)) {
                    throw new RuntimeException("Unsupported ModelControllerClient implementation " + wrapped.getClass().getName());
                }
                return ((AwaiterModelControllerClient) wrapped).isConnected();
            }

            @Override
            public void ensureConnected(long timeoutMillis) throws CommandLineException, IOException {
                if (!(wrapped instanceof AwaiterModelControllerClient)) {
                    throw new CommandLineException("Unsupported ModelControllerClient implementation " + wrapped.getClass().getName());
                }
                ((AwaiterModelControllerClient) wrapped).ensureConnected(timeoutMillis);
            }

            private AsyncFuture<OperationResponse> doExecuteOperationAsync(Operation operation, OperationMessageHandler messageHandler) {
                AsyncFuture<OperationResponse> task
                        = wrapped.executeOperationAsync(operation, messageHandler);
                setLastHandlerTask(task);
                return task;
            }

            private OperationResponse doExecuteOperation(Operation operation, OperationMessageHandler messageHandler) throws IOException {
                AsyncFuture<OperationResponse> task;
                task = wrapped.executeOperationAsync(operation, messageHandler);
                setLastHandlerTask(task);
                try {
                    return task.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException(ex);
                } catch (ExecutionException ex) {
                    throw new IOException(ex);
                }
            }

            private AsyncFuture<ModelNode> doExecuteAsync(Operation operation, OperationMessageHandler messageHandler) {
                AsyncFuture<ModelNode> task
                        = wrapped.executeAsync(operation, messageHandler);
                setLastHandlerTask(task);
                return task;
            }

            private AsyncFuture<ModelNode> doExecuteAsync(ModelNode operation, OperationMessageHandler messageHandler) {
                AsyncFuture<ModelNode> task
                        = wrapped.executeAsync(operation, messageHandler);
                setLastHandlerTask(task);
                return task;
            }

            private ModelNode doExecute(Operation operation) throws IOException {
                try {
                    Future<ModelNode> task
                            = wrapped.executeAsync(operation, OperationMessageHandler.DISCARD);
                    setLastHandlerTask(task);
                    return task.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException(ex);
                } catch (ExecutionException ex) {
                    throw new IOException(ex);
                } catch (Exception ex) {
                    throw new IOException(ex);
                }
            }

            private ModelNode doExecute(ModelNode operation) throws IOException {
                try {
                    Future<ModelNode> task
                            = wrapped.executeAsync(operation, OperationMessageHandler.DISCARD);
                    setLastHandlerTask(task);
                    return task.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException(ex);
                } catch (ExecutionException ex) {
                    throw new IOException(ex);
                } catch (Exception ex) {
                    throw new IOException(ex);
                }
            }

            private ModelNode doExecute(ModelNode operation, OperationMessageHandler handler) throws IOException {
                try {
                    Future<ModelNode> task
                            = wrapped.executeAsync(operation, handler);
                    setLastHandlerTask(task);
                    return task.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException(ex);
                } catch (ExecutionException ex) {
                    throw new IOException(ex);
                } catch (Exception ex) {
                    throw new IOException(ex);
                }
            }

            private ModelNode doExecute(Operation operation,
                    OperationMessageHandler handler) throws IOException {
                try {
                    Future<ModelNode> task
                            = wrapped.executeAsync(operation, handler);
                    setLastHandlerTask(task);
                    return task.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException(ex);
                } catch (ExecutionException ex) {
                    throw new IOException(ex);
                } catch (Exception ex) {
                    throw new IOException(ex);
                }
            }

            @Override
            public void close() throws IOException {
                wrapped.close();
            }
        }

        private final CommandContext wrapped;
        private final ModelControllerClient client;
        private Future<?> handlerTask;
        private boolean timeout;

        TimeoutCommandContext(CommandContext wrapped) {
            this.wrapped = wrapped;
            this.client = wrapped.getModelControllerClient() == null ? null
                    : new TimeoutModelControllerClient(wrapped.getModelControllerClient());
        }

        synchronized void timeout() {
            timeout = true;
            cancelTask(handlerTask, wrapped, TIMEOUT_CANCEL_MSG);
        }

        synchronized void interrupted() {
            cancelTask(handlerTask, wrapped, CANCEL_MSG);
        }

        /**
         * When an handler execution occurs, multiple calls can be operated. A
         * reference to the last one (in progress) is stored in order to cancel
         * it if a timeout occurs. Public for testing purpose.
         *
         * @param handlerTask The task to cancel if a timeout occurs.
         */
        public synchronized void setLastHandlerTask(Future<?> handlerTask) {
            if (timeout) {
                cancelTask(handlerTask, wrapped, CANCEL_MSG);
            } else {
                this.handlerTask = handlerTask;
            }
        }

        // For testing purpose.
        public Future<?> getLastTask() {
            return handlerTask;
        }

        @Override
        public CliConfig getConfig() {
            return wrapped.getConfig();
        }

        @Override
        public String getArgumentsString() {
            return wrapped.getArgumentsString();
        }

        @Override
        public ParsedCommandLine getParsedCommandLine() {
            return wrapped.getParsedCommandLine();
        }

        @Override
        public void printLine(String message) {
            wrapped.printLine(message);
        }

        @Override
        public void printColumns(Collection<String> col) {
            wrapped.printColumns(col);
        }

        @Override
        public void clearScreen() {
            wrapped.clearScreen();
        }

        @Override
        public void terminateSession() {
            wrapped.terminateSession();
        }

        @Override
        public boolean isTerminated() {
            return wrapped.isTerminated();
        }

        @Override
        public void set(Scope scope, String key, Object value) {
            wrapped.set(scope, key, value);
        }

        @Override
        public Object get(Scope scope, String key) {
            return wrapped.get(scope, key);
        }

        @Override
        public void clear(Scope scope) {
            wrapped.clear(scope);
        }

        @Override
        public Object remove(Scope scope, String key) {
            return wrapped.remove(scope, key);
        }

        @Override
        public ModelControllerClient getModelControllerClient() {
            return client;
        }

        @Override
        public void connectController() throws CommandLineException {
            wrapped.connectController();
        }

        @Override
        public void connectController(String controller) throws CommandLineException {
            wrapped.connectController(controller);
        }

        @Override
        public void connectController(String host, int port) throws CommandLineException {
            wrapped.connectController(host, port);
        }

        @Override
        public void bindClient(ModelControllerClient newClient) {
            wrapped.bindClient(newClient);
        }

        @Override
        public void disconnectController() {
            wrapped.disconnectController();
        }

        @Override
        public String getDefaultControllerHost() {
            return wrapped.getDefaultControllerHost();
        }

        @Override
        public int getDefaultControllerPort() {
            return wrapped.getDefaultControllerPort();
        }

        @Override
        public ControllerAddress getDefaultControllerAddress() {
            return wrapped.getDefaultControllerAddress();
        }

        @Override
        public String getControllerHost() {
            return wrapped.getControllerHost();
        }

        @Override
        public int getControllerPort() {
            return wrapped.getControllerPort();
        }

        @Override
        public CommandLineParser getCommandLineParser() {
            return wrapped.getCommandLineParser();
        }

        @Override
        public OperationRequestAddress getCurrentNodePath() {
            return wrapped.getCurrentNodePath();
        }

        @Override
        public NodePathFormatter getNodePathFormatter() {
            return wrapped.getNodePathFormatter();
        }

        @Override
        public OperationCandidatesProvider getOperationCandidatesProvider() {
            return wrapped.getOperationCandidatesProvider();
        }

        @Override
        public CommandHistory getHistory() {
            return wrapped.getHistory();
        }

        @Override
        public boolean isBatchMode() {
            return wrapped.isBatchMode();
        }

        @Override
        public boolean isWorkflowMode() {
            return wrapped.isWorkflowMode();
        }

        @Override
        public BatchManager getBatchManager() {
            return wrapped.getBatchManager();
        }

        @Override
        public BatchedCommand toBatchedCommand(String line) throws CommandFormatException {
            return wrapped.toBatchedCommand(line);
        }

        @Override
        public ModelNode buildRequest(String line) throws CommandFormatException {
            return wrapped.buildRequest(line);
        }

        @Override
        public CommandLineCompleter getDefaultCommandCompleter() {
            return wrapped.getDefaultCommandCompleter();
        }

        @Override
        public boolean isDomainMode() {
            return wrapped.isDomainMode();
        }

        @Override
        public void addEventListener(CliEventListener listener) {
            wrapped.addEventListener(listener);
        }

        @Override
        public int getExitCode() {
            return wrapped.getExitCode();
        }

        @Override
        public void handle(String line) throws CommandLineException {
            wrapped.handle(line);
        }

        @Override
        public void handleSafe(String line) {
            wrapped.handleSafe(line);
        }

        @Override
        public void interact() {
            wrapped.interact();
        }

        @Override
        public File getCurrentDir() {
            return wrapped.getCurrentDir();
        }

        @Override
        public void setCurrentDir(File dir) {
            wrapped.setCurrentDir(dir);
        }

        @Override
        public boolean isResolveParameterValues() {
            return wrapped.isResolveParameterValues();
        }

        @Override
        public void setResolveParameterValues(boolean resolve) {
            wrapped.setResolveParameterValues(resolve);
        }

        @Override
        public boolean isSilent() {
            return wrapped.isSilent();
        }

        @Override
        public void setSilent(boolean silent) {
            wrapped.setSilent(silent);
        }

        @Override
        public int getTerminalWidth() {
            return wrapped.getTerminalWidth();
        }

        @Override
        public int getTerminalHeight() {
            return wrapped.getTerminalHeight();
        }

        @Override
        public void setVariable(String name, String value) throws CommandLineException {
            wrapped.setVariable(name, value);
        }

        @Override
        public String getVariable(String name) {
            return wrapped.getVariable(name);
        }

        @Override
        public Collection<String> getVariables() {
            return wrapped.getVariables();
        }

        @Override
        public void registerRedirection(CommandLineRedirection redirection) throws CommandLineException {
            wrapped.registerRedirection(redirection);
        }

        @Override
        public ConnectionInfo getConnectionInfo() {
            return wrapped.getConnectionInfo();
        }

        @Override
        public void captureOutput(PrintStream captor) {
            wrapped.captureOutput(captor);
        }

        @Override
        public void releaseOutput() {
            wrapped.releaseOutput();
        }

        @Override
        public void setCommandTimeout(int numSeconds) {
            wrapped.setCommandTimeout(numSeconds);
        }

        @Override
        public int getCommandTimeout() {
            return wrapped.getCommandTimeout();
        }

        @Override
        public void resetTimeout(TIMEOUT_RESET_VALUE value) {
            wrapped.resetTimeout(value);
        }

        @Override
        public ModelNode execute(ModelNode mn, String description) throws CommandLineException, IOException {
            return wrapped.execute(mn, description);
        }

        @Override
        public ModelNode execute(Operation op, String description) throws CommandLineException, IOException {
            return wrapped.execute(op, description);
        }
    }

    private final CommandContext ctx;
    private final ExecutorService executorService = Executors.newCachedThreadPool(
            (r) -> new Thread(r, "CLI command executor"));

    private Future<?> handlerTask;

    // public for testing purpose.
    public CommandExecutor(CommandContext ctx) {
        this.ctx = ctx;
    }

    ModelNode execute(Operation op, int timeout, TimeUnit unit) throws CommandLineException,
            InterruptedException, ExecutionException, TimeoutException, IOException {
        ModelControllerClient client = ctx.getModelControllerClient();
        if (client == null) {
            throw new CommandLineException("CLI not connected");
        }

        Future<ModelNode> task = client.executeAsync(op,
                OperationMessageHandler.DISCARD);
        try {
            if (timeout <= 0) { //Synchronous
                return task.get();
            } else { // Guarded execution
                try {
                    return task.get(timeout, unit);
                } catch (TimeoutException ex) {
                    cancelTask(task, ctx, CANCEL_MSG);
                    throw ex;
                }
            }
        } catch (InterruptedException ex) {
            // User sending Ctrl-C
            Thread.currentThread().interrupt();
            cancelTask(task, ctx, CANCEL_MSG);
            throw ex;
        }
    }

    // Public for testing purpose.
    public void execute(CommandHandler handler, int timeout, TimeUnit unit) throws
            CommandLineException,
            InterruptedException, ExecutionException, TimeoutException {
        TimeoutCommandContext context = new TimeoutCommandContext(ctx);
        Future<Void> task = executorService.submit(() -> {
            handler.handle(context);
            return null;
        });
        try {
            if (timeout <= 0) { //Synchronous
                task.get();
            } else { // Guarded execution
                try {
                    task.get(timeout, unit);
                } catch (TimeoutException ex) {
                    // First make the context unusable
                    context.timeout();
                    // Then cancel the task.
                    task.cancel(true);
                    throw ex;
                }
            }
        } catch (InterruptedException ex) {
            // Could have been interrupted by user (Ctrl-C)
            Thread.currentThread().interrupt();
            cancelTask(task, context, null);
            // Interrupt running operation.
            context.interrupted();
            throw ex;
        }
    }

    private static void cancelTask(Future<?> task, CommandContext ctx, String msg) {
        if (task != null && !(task.isDone()
                && task.isCancelled())) {
            try {
                if (msg != null) {
                    ctx.printLine(msg);
                }
                task.cancel(true);
            } catch (Exception cex) {
                // XXX OK, task could be already canceled or done.
            }
        }
    }

    void cancel() {
        executorService.shutdownNow();
    }

    // FOR TESTING PURPOSE ONLY
    public Future<?> getLastTask() {
        return handlerTask;
    }
}
