/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.operation;

import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.EnterStateCharacterHandler;
import org.jboss.as.cli.parsing.GlobalCharacterHandlers;

/**
 *
 * @author Alexey Loubyansky
 */
public class AddressOperationSeparatorState extends DefaultParsingState {

    public static final AddressOperationSeparatorState INSTANCE = new AddressOperationSeparatorState();

    public AddressOperationSeparatorState() {
        this(OperationNameState.INSTANCE);
    }

    public AddressOperationSeparatorState(final OperationNameState opName) {
        super("ADDR_OP_SEP");
        this.setIgnoreWhitespaces(true);
        setDefaultHandler(new EnterStateCharacterHandler(opName));
        setReturnHandler(GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
    }

}
