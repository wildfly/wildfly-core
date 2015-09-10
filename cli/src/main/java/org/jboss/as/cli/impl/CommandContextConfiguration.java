/*
 * Copyright (C) 2014 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
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
    private boolean securityPrompts;

    private CommandContextConfiguration(String controller, String username, char[] password, String clientBindAddress, boolean disableLocalAuth, boolean initConsole, int connectionTimeout, InputStream consoleInput, OutputStream consoleOutput) {
        this.controller = controller;
        this.username = username;
        this.password = password;
        this.clientBindAddress = clientBindAddress;
        this.consoleInput = consoleInput;
        this.consoleOutput = consoleOutput;
        this.initConsole = initConsole;
        this.disableLocalAuth = disableLocalAuth || username != null;
        this.connectionTimeout = connectionTimeout;
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

    public boolean isSecurityPrompts() {
        return securityPrompts;
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
        private boolean securityPrompts = true;

        public Builder() {
        }

        public CommandContextConfiguration build() {
            if(disableLocalAuthUnset) {
                this.disableLocalAuth = username != null;
            }
            final CommandContextConfiguration config = new CommandContextConfiguration(controller, username, password, clientBindAddress, disableLocalAuth,
                    initConsole, connectionTimeout, consoleInput, consoleOutput);
            config.securityPrompts = this.securityPrompts;
            return config;
        }
        public Builder setController(String controller) {
            this.controller = controller;
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

        public Builder setSecurityPrompts(boolean securityPrompts) {
            this.securityPrompts = securityPrompts;
            return this;
        }
    }
}
