/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

import java.security.cert.X509Certificate;
import java.util.Date;

import org.jboss.as.cli.ConnectionInfo;
import org.jboss.as.cli.ControllerAddress;

/**
 *
 * @author Claudio Miranda
 *
 */
public class ConnectionInfoBean implements ConnectionInfo {

    private boolean disableLocalAuth;
    private String username;
    private Date loggedSince;
    private X509Certificate[] serverCertificates;
    private ControllerAddress address;

    ConnectionInfoBean() {}

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
