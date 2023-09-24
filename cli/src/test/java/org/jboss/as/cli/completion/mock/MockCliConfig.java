/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.completion.mock;


import org.jboss.as.cli.CliConfig;
import org.jboss.as.cli.ColorConfig;
import org.jboss.as.cli.ControllerAddress;
import org.jboss.as.cli.SSLConfig;

/**
 *
 * @author Alexey Loubyansky
 */
public class MockCliConfig implements CliConfig {

    @Override
    public SSLConfig getSslConfig() {
        return null;
    }

    @Override
    public String getDefaultControllerProtocol() {
        return "remote+http";
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
    public boolean isUseLegacyOverride() {
        return true;
    }

    @Override
    public ControllerAddress getDefaultControllerAddress() {
        return new ControllerAddress("remote+http", "localhost", 9990);
    }

    @Override
    public ControllerAddress getAliasedControllerAddress(String alias) {
        return null;
    }

    @Override
    public boolean isHistoryEnabled() {
        return false;
    }

    @Override
    public String getHistoryFileName() {
        return ".jboss-cli-history";
    }

    @Override
    public String getHistoryFileDir() {
        return null;
    }

    @Override
    public int getHistoryMaxSize() {
        return 500;
    }

    @Override
    public int getConnectionTimeout() {
        return 5000;
    }

    @Override
    public boolean isValidateOperationRequests() {
        return true;
    }

    @Override
    public boolean isResolveParameterValues() {
        return false;
    }

    @Override
    public boolean isSilent() {
        return false;
    }

    @Override
    public boolean isErrorOnInteract() {
        return false;
    }

    @Override
    public boolean isAccessControl() {
        return false;
    }

    @Override
    public boolean isEchoCommand() {
        return false;
    }

    @Override
    public Integer getCommandTimeout() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isOutputJSON() {
        return false;
    }

    @Override
    public boolean isColorOutput() {
        return false;
    }

    @Override
    public ColorConfig getColorConfig() {
        return null;
    }

    @Override
    public boolean isOutputPaging() {
        return false;
    }
}
