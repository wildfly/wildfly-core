/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability.registry;

import java.util.Objects;

import org.jboss.as.controller.PathAddress;

/**
 * Encapsulates the point in the model that triggered the registration of a capability or requirement.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class RegistrationPoint {

    private final PathAddress address;
    private final String attribute;

    public RegistrationPoint(PathAddress address, String attribute) {
        this.address = address;

        this.attribute = attribute;
    }

    /**
     * Gets the address of the resource that triggered the registration.
     *
     * @return the address. Will not be {@code null}
     */
    public PathAddress getAddress() {
        return address;
    }

    /**
     * Gets the name of the specific attribute at {@link #getAddress() address} that triggered the registration,
     * if the was a single attribute responsible.
     *
     * @return the name of the attribute, or {@code null} if there wasn't a single attribute responsible
     */
    public String getAttribute() {
        return attribute;
    }

    @Override
    public String toString() {
        if (attribute == null) {
            return address.toString();
        } else {
            return "address=" + address.toString() +";attribute=" + attribute;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegistrationPoint that = (RegistrationPoint) o;
        return Objects.equals(address, that.address) &&
                Objects.equals(attribute, that.attribute);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, attribute);
    }
}
