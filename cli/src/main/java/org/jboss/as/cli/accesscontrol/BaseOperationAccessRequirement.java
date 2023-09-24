/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.accesscontrol;

import static org.wildfly.common.Assert.checkNotNullParam;

import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.as.cli.operation.impl.DefaultPrefixFormatter;

/**
 * @author Alexey Loubyansky
 *
 */
public abstract class BaseOperationAccessRequirement extends AddressAccessRequirement {

    protected final String operation;

    BaseOperationAccessRequirement(String operation) {
        this(new DefaultOperationRequestAddress(), operation);
    }

    BaseOperationAccessRequirement(String address, String operation) {
        super(address);
        this.operation = checkNotNullParam("operation", operation);
    }

    BaseOperationAccessRequirement(OperationRequestAddress address, String operation) {
        super(address);
        this.operation = checkNotNullParam("operation", operation);
    }

    protected String toString;
    @Override
    public String toString() {
        if (toString == null) {
            final StringBuilder buf = new StringBuilder();
            if (address != null) {
                buf.append(DefaultPrefixFormatter.INSTANCE.format(address));
            }
            buf.append(':').append(operation);
            toString = buf.toString();
        }
        return toString;
    }
}
