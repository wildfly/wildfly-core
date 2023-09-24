/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

import org.jboss.as.domain.management.security.password.PasswordCheckUtil;

/**
 * Class to hold options either dynamically discovered or passed in on the command line.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class RuntimeOptions {

    private ConsoleWrapper consoleWrapper;

    private PasswordCheckUtil checkUtil;

    private String jbossHome;

    private String serverConfigDir;

    private String domainConfigDir;

    private String userProperties;

    private String groupProperties;

    private boolean confirmWarning = false;

    private boolean displaySecret = false;

    /**
     * Enable/Disable mode is active by using --enable or --disable argument
     */
    private boolean enableDisableMode = false;

    /**
     * Disable a user
     */
    private boolean disable = false;

    public ConsoleWrapper getConsoleWrapper() {
        return consoleWrapper;
    }

    public void setConsoleWrapper(ConsoleWrapper consoleWrapper) {
        this.consoleWrapper = consoleWrapper;
    }

    public PasswordCheckUtil getCheckUtil() {
        return checkUtil;
    }

    public void setCheckUtil(PasswordCheckUtil checkUtil) {
        this.checkUtil = checkUtil;
    }

    public String getJBossHome() {
        return jbossHome;
    }

    public void setJBossHome(String jbossHome) {
        this.jbossHome = jbossHome;
    }

    public String getServerConfigDir() {
        return serverConfigDir;
    }

    public void setServerConfigDir(String serverConfigDir) {
        this.serverConfigDir = serverConfigDir;
    }

    public String getDomainConfigDir() {
        return domainConfigDir;
    }

    public void setDomainConfigDir(String domainConfigDir) {
        this.domainConfigDir = domainConfigDir;
    }

    public String getUserProperties() {
        return userProperties;
    }

    public void setUserProperties(String userProperties) {
        this.userProperties = userProperties;
    }

    public String getGroupProperties() {
        return groupProperties;
    }

    public void setGroupProperties(String groupProperties) {
        this.groupProperties = groupProperties;
    }

    boolean isEnableDisableMode() {
        return enableDisableMode;
    }

    void setEnableDisableMode(boolean enableDisableMode) {
        this.enableDisableMode = enableDisableMode;
    }

    boolean isDisable() {
        return disable;
    }

    void setDisable(boolean disable) {
        this.disable = disable;
    }

    boolean isConfirmWarning() {
        return confirmWarning;
    }

    void setConfirmWarning(boolean confirmWarning) {
        this.confirmWarning = confirmWarning;
    }

    boolean isDisplaySecret() {
        return displaySecret;
    }

    void setDisplaySecret(boolean displaySecret) {
        this.displaySecret = displaySecret;
    }
}
