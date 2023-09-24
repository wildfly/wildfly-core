/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.security;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Principal;


/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public final class InetAddressPrincipal implements Principal, Cloneable, Serializable {

    private static final long serialVersionUID = -4933833947550618331L;

    private final InetAddress inetAddress;

    /**
     * Create a new instance.
     *
     * @param inetAddress the address
     */
    public InetAddressPrincipal(final InetAddress inetAddress) {
        checkNotNullParam("inetAddress", inetAddress);
        try {
            this.inetAddress = InetAddress.getByAddress(inetAddress.getHostAddress(), inetAddress.getAddress());
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Get the name of this principal; it will be the string representation of the IP address.
     *
     * @return the name of this principal
     */
    public String getName() {
        return inetAddress.getHostAddress();
    }

    /**
     * Get the IP address of this principal.
     *
     * @return the address
     */
    public InetAddress getInetAddress() {
        return inetAddress;
    }

    /**
     * Determine whether this instance is equal to another.
     *
     * @param other the other instance
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final Object other) {
        return other instanceof InetAddressPrincipal && equals((InetAddressPrincipal) other);
    }

    /**
     * Determine whether this instance is equal to another.
     *
     * @param other the other instance
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final InetAddressPrincipal other) {
        return other != null && inetAddress.equals(other.inetAddress);
    }

    /**
     * Get the hash code for this instance.  It will be equal to the hash code of the {@code InetAddress} object herein.
     *
     * @return the hash code
     */
    public int hashCode() {
        return inetAddress.hashCode();
    }

    /**
     * Get a human-readable representation of this principal.
     *
     * @return the string
     */
    public String toString() {
        return "InetAddressPrincipal <" + inetAddress.toString() + ">";
    }

    /**
     * Create a clone of this instance.
     *
     * @return the clone
     */
    public InetAddressPrincipal clone() {
        try {
            return (InetAddressPrincipal) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }
}