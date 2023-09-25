/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.accesscontrol;

import static org.wildfly.common.Assert.checkNotNullParam;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.operation.CommandLineParser;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.as.cli.parsing.ParserUtil;

/**
 * @author Alexey Loubyansky
 *
 */
public abstract class AddressAccessRequirement extends BaseAccessRequirement {

    protected final OperationRequestAddress address;

    AddressAccessRequirement() {
        address = new DefaultOperationRequestAddress();
    }

    AddressAccessRequirement(String address) {
        this.address = new DefaultOperationRequestAddress();
        if (address != null) {
            final CommandLineParser.CallbackHandler handler = new DefaultCallbackHandler(this.address);
            try {
                ParserUtil.parseOperationRequest(address, handler);
            } catch (CommandFormatException e) {
                throw new IllegalArgumentException("Failed to parse path '" + address + "': " + e.getMessage());
            }
        }
    }

    AddressAccessRequirement(OperationRequestAddress address) {
        this.address = checkNotNullParam("address", address);
    }

    protected OperationRequestAddress getAddress() {
        return address;
    }
}
