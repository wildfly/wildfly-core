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
import org.jboss.as.cli.parsing.QuotesState;
import org.jboss.as.cli.parsing.WordCharacterHandler;

/**
 *
 * @author Alexey Loubyansky
 */
public class HeaderValueState extends ExpressionBaseState {

    public static final HeaderValueState INSTANCE = new HeaderValueState();
    public static final String ID = "HEADER_VALUE";

    HeaderValueState() {
        super(ID);
        putHandler(';', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        putHandler('}', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        setEnterHandler(new CharacterHandler() {
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if (ctx.getCharacter() != '=') {
                    if (ctx.getCharacter() == '"') {
                        ctx.enterState(QuotesState.QUOTES_EXCLUDED);
                    } else {
                        ctx.getCallbackHandler().character(ctx);
                    }
                }
            }
        });
        setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_ON);
        setReturnHandler(new CharacterHandler() {
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                // We do return from quoted content.
                // We should leave if the separator were handled at the list level.
                // Because they are not, we stay in the same state until the separator is found
                // Doing so the separator is skip and not associated to any next header name.
                char c = ctx.getCharacter();
                if (c == ';' || c == '}') {
                    ctx.leaveState();
                }
            }
        });
        setIgnoreWhitespaces(true);
    }

    @Override
    public boolean lockValueIndex() {
        return true;
    }
}
