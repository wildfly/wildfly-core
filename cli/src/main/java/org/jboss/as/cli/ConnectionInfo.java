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
