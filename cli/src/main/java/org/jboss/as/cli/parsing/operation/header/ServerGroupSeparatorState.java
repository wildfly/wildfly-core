/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.operation.header;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.GlobalCharacterHandlers;
import org.jboss.as.cli.parsing.LineBreakHandler;
import org.jboss.as.cli.parsing.ParsingContext;


/**
 *
 * @author Alexey Loubyansky
 */
public class ServerGroupSeparatorState extends DefaultParsingState {

    public static final ServerGroupSeparatorState INSTANCE = new ServerGroupSeparatorState();
    public static final String ID = "SG_SEPARATOR";

    ServerGroupSeparatorState() {
        this(ServerGroupState.INSTANCE);
    }

    ServerGroupSeparatorState(final ServerGroupState sg) {
        super(ID);
        setIgnoreWhitespaces(true);
        setDefaultHandler(new LineBreakHandler(false, false){
            @Override
            protected void doHandle(ParsingContext ctx) throws CommandFormatException {
                ctx.enterState(sg);
            }
        });
        setReturnHandler(GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
    }
}
