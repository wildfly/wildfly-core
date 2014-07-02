/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller.capability.registry;

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
}
