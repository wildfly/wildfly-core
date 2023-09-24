/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.completion.mock;

import java.security.cert.X509Certificate;
import java.util.Date;

import org.jboss.as.cli.ConnectionInfo;
import org.jboss.as.cli.ControllerAddress;

/**
 *
 * @author Claudio Miranda
 *
 */
public class ConnectionInfoBeanMock implements ConnectionInfo {

    private boolean disableLocalAuth;
    private String username;
    private Date loggedSince;
    private X509Certificate[] serverCertificates;
    private ControllerAddress address;

    ConnectionInfoBeanMock() {}

    public boolean isDisableLocalAuth() {
        return disableLocalAuth;
    }

    void setDisableLocalAuth(boolean disableLocalAuth) {
        this.disableLocalAuth = disableLocalAuth;
    }

    public String getUsername() {
        return username;
    }

    void setUsername(String username) {
        this.username = username;
    }

    public Date getLoggedSince() {
        return loggedSince;
    }

    void setLoggedSince(Date loggedSince) {
        this.loggedSince = loggedSince;
    }

    public X509Certificate[] getServerCertificates() {
        return serverCertificates;
    }

    void setServerCertificates(X509Certificate[] serverCertificates) {
        this.serverCertificates = serverCertificates;
    }

    public ControllerAddress getControllerAddress() {
        return address;
    }

    void setControllerAddress(ControllerAddress address) {
        this.address = address;
    }

}
