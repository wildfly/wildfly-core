/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.operation;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.EnterStateCharacterHandler;
import org.jboss.as.cli.parsing.ExpressionBaseState;
import org.jboss.as.cli.parsing.GlobalCharacterHandlers;
import org.jboss.as.cli.parsing.ParsingContext;
import org.jboss.as.cli.parsing.WordCharacterHandler;


/**
 *
 * @author Alexey Loubyansky
 */
public class PropertyState extends ExpressionBaseState {

    public static final PropertyState INSTANCE = new PropertyState();
    public static final String ID = "PROP";

    PropertyState() {
        this(PropertyValueState.INSTANCE);
    }

    PropertyState(PropertyValueState valueState) {
        this(',', valueState, ')');
    }

    PropertyState(char propSeparator, char... listEnd) {
        this(propSeparator, new PropertyValueState(propSeparator, listEnd), listEnd);
    }

    PropertyState(char propSeparator, PropertyValueState valueState, char...listEnd) {
        super(ID);
        setIgnoreWhitespaces(true);
        setEnterHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                WordCharacterHandler.IGNORE_LB_ESCAPE_OFF.handle(ctx);
            }});
        for(int i = 0; i < listEnd.length; ++i) {
            putHandler(listEnd[i], GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        }
        enterState('=', new NameValueSeparatorState(valueState));
        // This is required to accept property without a value.
        putHandler(propSeparator, GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_OFF);
        setReturnHandler(GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
    }

    private static class NameValueSeparatorState extends DefaultParsingState {
        NameValueSeparatorState(PropertyValueState valueState) {
            super("NAME_VALUE_SEPARATOR");
            setDefaultHandler(new EnterStateCharacterHandler(valueState));
            setReturnHandler(GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
            setIgnoreWhitespaces(true);
        }
    }
}
