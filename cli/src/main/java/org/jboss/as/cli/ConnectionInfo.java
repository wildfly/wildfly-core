/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;

import java.security.cert.X509Certificate;
import java.util.Date;
/**
 *
 * Retain information about the current connection to the server, the information is
 * address, username, date and hour since logged in. If an SSL
 * connection exposes the server certificate.
 *
 * @author Claudio Miranda
 *
 */
public interface ConnectionInfo {

    /**
     * @return true if disabled the local authentication mechanism
     */
    boolean isDisableLocalAuth() ;
    String getUsername();
    Date getLoggedSince() ;
    X509Certificate[] getServerCertificates();

    /**
     * Returns the Controller Address. If a ModelControllerClient has been
     * passed to the CommandContext and no connection occured, then the address
     * is unknown.
     *
     * @return The Controller Address if known. Otherwise returns null.
     */
    ControllerAddress getControllerAddress();
}
