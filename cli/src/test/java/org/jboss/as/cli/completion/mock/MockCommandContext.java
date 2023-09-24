/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.completion.mock;

import static org.wildfly.common.Assert.checkNotNullParam;
import static org.wildfly.common.Assert.checkNotNullParamWithNullPointerException;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.cli.CliConfig;
import org.jboss.as.cli.CliEventListener;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandHistory;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.CommandLineRedirection;
import org.jboss.as.cli.ConnectionInfo;
import org.jboss.as.cli.ControllerAddress;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.batch.BatchedCommand;
import org.jboss.as.cli.operation.CommandLineParser;
import org.jboss.as.cli.operation.NodePathFormatter;
import org.jboss.as.cli.operation.OperationCandidatesProvider;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.DefaultOperationCandidatesProvider;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestParser;
import org.jboss.as.cli.operation.impl.DefaultPrefixFormatter;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public class MockCommandContext implements CommandContext {

    private MockCliConfig config = new MockCliConfig();
    private ModelControllerClient mcc;
    //private CommandLineParser operationParser;
    private OperationRequestAddress prefix;
    private OperationCandidatesProvider operationCandidatesProvider;

    private DefaultCallbackHandler parsedCmd = new DefaultCallbackHandler();

    private int exitCode;

    private File curDir = new File("");
    private boolean resolveParameterValues;
    private Map<Scope, Map<String, Object>> map = new HashMap<>();

    private boolean silent;
    private ConnectionInfoBeanMock connInfo =  new ConnectionInfoBeanMock();

    private String buffer;

    public MockCommandContext() {
        connInfo.setUsername("test");
        set(Scope.CONTEXT, "connection_info", connInfo);
    }

    public void parseCommandLine(String buffer, boolean validate) throws CommandFormatException {
        try {
            this.buffer = buffer;
            parsedCmd.parse(prefix, buffer, validate);
        } catch (CommandFormatException e) {
            if(!parsedCmd.endsOnAddressOperationNameSeparator() || !parsedCmd.endsOnSeparator()) {
               throw e;
            }
        }
    }

    @Override
    public String getArgumentsString() {
        // just for test like CommandCompletionTestCase
        // mock method to support completion of commands and ops spread across multiple lines like in CommandContextImpl
        if (buffer == null) {
            return null;
        } else {
            int index = buffer.indexOf("\\n");
            return index == -1 ? null : buffer.substring(0, index);
        }
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandContext#printLine(java.lang.String)
     */
    @Override
    public void printLine(String message) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandContext#printColumns(java.util.Collection<java.lang.String>)
     */
    @Override
    public void printColumns(Collection<String> col) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandContext#terminateSession()
     */
    @Override
    public void terminateSession() {
        // TODO Auto-generated method stub

    }

    @Override
    public void set(Scope scope, String key, Object value) {
        checkNotNullParamWithNullPointerException("scope", scope);
        checkNotNullParamWithNullPointerException("key", key);
        Map<String, Object> store = map.get(scope);
        if (store == null) {
            store = new HashMap<>();
            map.put(scope, store);
        }
        store.put(key, value);
    }

    @Override
    public Object get(Scope scope, String key) {
        checkNotNullParamWithNullPointerException("scope", scope);
        checkNotNullParamWithNullPointerException("key", key);
        Map<String, Object> store = map.get(scope);
        Object value = null;
        if (store != null) {
            value = store.get(key);
        }
        return value;
    }

    @Override
    public void clear(Scope scope) {
        checkNotNullParamWithNullPointerException("scope", scope);
        Map<String, Object> store = map.remove(scope);
        if (store != null) {
            store.clear();
        }
    }

    @Override
    public Object remove(Scope scope, String key) {
        return null;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandContext#getModelControllerClient()
     */
    @Override
    public ModelControllerClient getModelControllerClient() {
        return mcc;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandContext#getOperationRequestParser()
     */
    @Override
    public CommandLineParser getCommandLineParser() {
        return DefaultOperationRequestParser.INSTANCE;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandContext#getPrefix()
     */
    @Override
    public OperationRequestAddress getCurrentNodePath() {
        if(prefix == null) {
            prefix = new DefaultOperationRequestAddress();
        }
        return prefix;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandContext#getPrefixFormatter()
     */
    @Override
    public NodePathFormatter getNodePathFormatter() {
        return DefaultPrefixFormatter.INSTANCE;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandContext#getOperationCandidatesProvider()
     */
    @Override
    public OperationCandidatesProvider getOperationCandidatesProvider() {
        if(operationCandidatesProvider == null) {
            operationCandidatesProvider = new DefaultOperationCandidatesProvider();
        }
        return operationCandidatesProvider;
    }

    public void setOperationCandidatesProvider(OperationCandidatesProvider provider) {
        this.operationCandidatesProvider = provider;
    }

    @Override
    public void connectController(String controller) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void connectController(String controller, String clientAddress) throws CommandLineException {
        // TODO Auto-generated method stub
    }

    @Override
    @Deprecated
    public void connectController(String host, int port) throws CommandLineException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void bindClient(ModelControllerClient newClient) {
        throw new UnsupportedOperationException();
    }


    @Override
    public void disconnectController() {
        connInfo = null;
    }

    @Override
    @Deprecated
    public String getDefaultControllerHost() {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public int getDefaultControllerPort() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ControllerAddress getDefaultControllerAddress() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getControllerHost() {
        return null;
    }

    @Override
    public int getControllerPort() {
        return -1;
    }

    @Override
    public CommandHistory getHistory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isBatchMode() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isWorkflowMode() {
        return false;
    }

    @Override
    public BatchManager getBatchManager() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BatchedCommand toBatchedCommand(String line)
            throws OperationFormatException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CommandLineCompleter getDefaultCommandCompleter() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ParsedCommandLine getParsedCommandLine() {
        return parsedCmd;
    }

    @Override
    public boolean isDomainMode() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void clearScreen() {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean isTerminated() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void addEventListener(CliEventListener listener) {
        // TODO Auto-generated method stub

    }

    @Override
    public CliConfig getConfig() {
        return config;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    @Override
    public void handle(String line) throws CommandLineException {
        // TODO Auto-generated method stub
    }

    @Override
    public File getCurrentDir() {
        return curDir;
    }

    @Override
    public void setCurrentDir(File dir) {
        this.curDir = checkNotNullParam("dir", dir);
    }

    @Override
    public void handleSafe(String line) {
        // TODO Auto-generated method stub

    }

    @Override
    public void interact() {
        // TODO Auto-generated method stub

    }

    @Override
    public ModelNode buildRequest(String line) throws CommandFormatException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void connectController() {
        connectController(null);
    }

    public boolean isResolveParameterValues() {
        return resolveParameterValues;
    }

    @Override
    public void setResolveParameterValues(boolean resolve) {
        resolveParameterValues = resolve;
    }

    @Override
    public boolean isSilent() {
        return silent;
    }

    @Override
    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    @Override
    public int getTerminalWidth() {
        return -1;
    }

    @Override
    public int getTerminalHeight() {
        return -1;
    }

    @Override
    public void setVariable(String name, String value) throws CommandLineException {
        // TODO Auto-generated method stub

    }

    @Override
    public String getVariable(String name) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<String> getVariables() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void registerRedirection(CommandLineRedirection redirection) throws CommandLineException {
        throw new CommandLineException("Redirection isn't supported by this impl");
    }

    @Override
    public ConnectionInfo getConnectionInfo() {
        return connInfo;
    }

    @Override
    public void captureOutput(PrintStream captor) {
        // TODO Auto-generated method stub
    }

    @Override
    public void releaseOutput() {
        // TODO Auto-generated method stub
    }

    @Override
    public void setCommandTimeout(int numSeconds) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getCommandTimeout() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void resetTimeout(TIMEOUT_RESET_VALUE value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ModelNode execute(ModelNode mn, String msg) throws CommandLineException, IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ModelNode execute(Operation op, String msg) throws CommandLineException, IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
