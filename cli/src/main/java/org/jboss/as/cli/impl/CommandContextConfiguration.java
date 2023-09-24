/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl;

import java.io.InputStream;
import java.io.OutputStream;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class CommandContextConfiguration {
    private final String controller;
    private final String username;
    private final char[] password;
    private final String clientBindAddress;
    private final InputStream consoleInput;
    private final OutputStream consoleOutput;
    private final boolean initConsole;
    private final boolean disableLocalAuth;
    private final int connectionTimeout;
    private boolean silent;
    private Boolean errorOnInteract;
    private Boolean validateOperationRequests;
    private final boolean echoCommand;
    private final Integer commandTimeout;
    private final boolean outputJSON;
    private final boolean colorOutput;
    private final boolean outputPaging;
    private final boolean resolveParameters;

    private CommandContextConfiguration(String controller, String username, char[] password, String clientBindAddress,
            boolean disableLocalAuth, boolean initConsole, int connectionTimeout, InputStream consoleInput, OutputStream consoleOutput,
            boolean echoCommand, Integer commandTimeout, boolean outputJSON, boolean colorOutput,
            boolean outputPaging, boolean resolveParameters) {
        this.controller = controller;
        this.username = username;
        this.password = password;
        this.clientBindAddress = clientBindAddress;
        this.consoleInput = consoleInput;
        this.consoleOutput = consoleOutput;
        this.initConsole = initConsole;
        this.disableLocalAuth = disableLocalAuth || username != null;
        this.connectionTimeout = connectionTimeout;
        this.echoCommand = echoCommand;
        this.commandTimeout = commandTimeout;
        this.outputJSON = outputJSON;
        this.colorOutput = colorOutput;
        this.outputPaging = outputPaging;
        this.resolveParameters = resolveParameters;
    }

    public Integer getCommandTimeout() {
        return commandTimeout;
    }

    public String getController() {
        return controller;
    }

    public String getUsername() {
        return username;
    }

    public char[] getPassword() {
        return password;
    }

    public String getClientBindAddress() {
        return clientBindAddress;
    }

    public InputStream getConsoleInput() {
        return consoleInput;
    }

    public OutputStream getConsoleOutput() {
        return consoleOutput;
    }

    public boolean isInitConsole() {
        return initConsole;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public boolean isDisableLocalAuth() {
        return disableLocalAuth;
    }

    public boolean isSilent() {
        return silent;
    }

    public Boolean isErrorOnInteract() {
        return errorOnInteract;
    }

    public Boolean isValidateOperationRequests() {
        return validateOperationRequests;
    }

    public boolean isEchoCommand() {
        return echoCommand;
    }

    public boolean isOutputJSON() {
        return outputJSON;
    }

    public boolean isOutputPaging() {
        return outputPaging;
    }

    public boolean isColorOutput() {
        return colorOutput;
    }

    public boolean isResolveParameterValues() {
        return resolveParameters;
    }

    public static class Builder {
        private String controller;
        private String username;
        private char[] password;
        private String clientBindAddress;
        private InputStream consoleInput;
        private OutputStream consoleOutput;
        private boolean initConsole = false;
        private boolean disableLocalAuth;
        private int connectionTimeout = -1;
        private boolean disableLocalAuthUnset = true;
        private boolean silent;
        private Boolean errorOnInteract;
        private Boolean validateOperationRequests;
        private boolean echoCommand;
        private Integer commandTimeout;
        private boolean outputJSON;
        private boolean colorOutput;
        private boolean outputPaging = true;
        private boolean resolveParameters;

        public Builder() {
        }

        public CommandContextConfiguration build() {
            if(disableLocalAuthUnset) {
                this.disableLocalAuth = username != null;
            }
            final CommandContextConfiguration config = new CommandContextConfiguration(controller, username, password,
                    clientBindAddress, disableLocalAuth, initConsole, connectionTimeout, consoleInput, consoleOutput,
                    echoCommand, commandTimeout, outputJSON, colorOutput, outputPaging, resolveParameters);
            config.silent = silent;
            config.errorOnInteract = errorOnInteract;
            config.validateOperationRequests = validateOperationRequests;
            return config;
        }

        public Builder setCommandTimeout(Integer commandTimeout) {
            this.commandTimeout = commandTimeout;
            return this;
        }

        public Builder setController(String controller) {
            this.controller = controller;
            return this;
        }

        public Builder setEchoCommand(boolean echoCommand) {
            this.echoCommand = echoCommand;
            return this;
        }

        public Builder setUsername(String username) {
            this.username = username;
            return this;
        }

        public Builder setPassword(char[] password) {
            this.password = password;
            return this;
        }

        public Builder setClientBindAddress(String clientBindAddress) {
            this.clientBindAddress = clientBindAddress;
            return this;
        }

        public Builder setConsoleInput(InputStream consoleInput) {
            this.consoleInput = consoleInput;
            if(errorOnInteract == null && consoleInput != null) {
                // if the input is provided, interaction is assumed
                this.errorOnInteract = false;
            }
            return this;
        }

        public Builder setConsoleOutput(OutputStream consoleOutput) {
            this.consoleOutput = consoleOutput;
            return this;
        }

        public Builder setInitConsole(boolean initConsole) {
            this.initConsole = initConsole;
            return this;
        }

        public Builder setDisableLocalAuth(boolean disableLocalAuth) {
            this.disableLocalAuth = disableLocalAuth;
            this.disableLocalAuthUnset = false;
            return this;
        }

        public Builder setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        public Builder setSilent(boolean silent) {
            this.silent = silent;
            return this;
        }

        public Builder setErrorOnInteract(boolean errorOnInteract) {
            this.errorOnInteract = errorOnInteract;
            return this;
        }

        public Builder setValidateOperationRequests(boolean validateOperationRequests) {
            this.validateOperationRequests = validateOperationRequests;
            return this;
        }

        public Builder setOutputJSON(boolean outputJSON) {
            this.outputJSON = outputJSON;
            return this;
        }

        public Builder setColorOutput(boolean colorOutput) {
            this.colorOutput = colorOutput;
            return this;
        }

        public Builder setOutputPaging(boolean outputPaging) {
            this.outputPaging = outputPaging;
            return this;
        }

        public Builder setResolveParameterValues(boolean resolveParameters) {
            this.resolveParameters = resolveParameters;
            return this;
        }
    }
}
