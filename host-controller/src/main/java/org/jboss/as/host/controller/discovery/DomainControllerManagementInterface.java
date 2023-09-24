/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.discovery;

import org.jboss.as.remoting.Protocol;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class DomainControllerManagementInterface {
    private final Protocol protocol;
    private final String address;
    private final int port;
    private String host;

    public DomainControllerManagementInterface(int port, String address, Protocol protocol) {
        this.port = port;
        this.address = address;
        this.host = address;
        this.protocol = protocol;
    }

    public int getPort() {
        return port;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getHost() {
        if(this.host == null) {
            return getAddress();
        }
        return host;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public String getAddress() {
        return address;
    }

}
