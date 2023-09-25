/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.operation;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.ExpressionBaseState;
import org.jboss.as.cli.parsing.GlobalCharacterHandlers;
import org.jboss.as.cli.parsing.ParsingContext;
import org.jboss.as.cli.parsing.WordCharacterHandler;


/**
 *
 * @author Alexey Loubyansky
 */
public final class HeaderNameState extends ExpressionBaseState {

    public static final String ID = "HEADER_NAME";
    public static final HeaderNameState INSTANCE = new HeaderNameState();

    public HeaderNameState() {
        super(ID);
        setEnterHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                ctx.getCallbackHandler().character(ctx);
            }});
        setLeaveOnWhitespace(true);
        setDefaultHandler(WordCharacterHandler.LB_LEAVE_ESCAPE_ON);
        putHandler(';', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        putHandler('}', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        putHandler('=', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
    }
}