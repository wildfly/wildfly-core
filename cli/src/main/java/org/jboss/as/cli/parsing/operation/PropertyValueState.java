/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.operation;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.BackQuotesState;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.DefaultStateWithEndCharacter;
import org.jboss.as.cli.parsing.ExpressionBaseState;
import org.jboss.as.cli.parsing.GlobalCharacterHandlers;
import org.jboss.as.cli.parsing.ParsingContext;
import org.jboss.as.cli.parsing.QuotesState;
import org.jboss.as.cli.parsing.WordCharacterHandler;

/**
 *
 * @author Alexey Loubyansky
 */
public class PropertyValueState extends ExpressionBaseState {

    public static final PropertyValueState INSTANCE = new PropertyValueState();
    public static final String ID = "PROP_VALUE";

    PropertyValueState() {
        this(',', ')');
    }

    PropertyValueState(char propSeparator, char... listEnd) {
        super(ID, false);
        this.setEnterHandler(new CharacterHandler() {
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                getHandler(ctx.getCharacter()).handle(ctx);
            }});
        putHandler(propSeparator, GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        for(int i = 0; i < listEnd.length; ++i) {
            putHandler(listEnd[i], GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        }
        enterState('"', QuotesState.QUOTES_INCLUDED_KEEP_ESCAPES);
        enterState('`', BackQuotesState.QUOTES_INCLUDED_KEEP_ESCAPES);
        enterState('[', DefaultStateWithEndCharacter.builder("BRACKETS")
                .setLeaveStateChar(']')
                .setEndRequired(true)
                .setEnterLeaveContent(true)
                .setEnterStateHandlers(enterStateHandlers)
                .setResolveSystemProperties(false)
                .build());
        enterState('(', DefaultStateWithEndCharacter.builder("PARENTHESIS")
                .setLeaveStateChar(')')
                .setEndRequired(true)
                .setEnterLeaveContent(true)
                .setEnterStateHandlers(enterStateHandlers)
                .setResolveSystemProperties(false)
                .build());
        enterState('{', DefaultStateWithEndCharacter.builder("BRACES")
                .setLeaveStateChar('}')
                .setEndRequired(true)
                .setEnterLeaveContent(true)
                .setEnterStateHandlers(enterStateHandlers)
                .setResolveSystemProperties(false)
                .build());
        setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_OFF);
    }

    @Override
    public boolean lockValueIndex() {
        return true;
    }
}
