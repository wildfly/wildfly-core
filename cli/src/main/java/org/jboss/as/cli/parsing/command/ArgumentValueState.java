/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.command;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.BackQuotesState;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.DefaultStateWithEndCharacter;
import org.jboss.as.cli.parsing.ExpressionBaseState;
import org.jboss.as.cli.parsing.ParsingContext;
import org.jboss.as.cli.parsing.QuotesState;
import org.jboss.as.cli.parsing.WordCharacterHandler;

/**
 *
 * @author Alexey Loubyansky
 */
public class ArgumentValueState extends ExpressionBaseState {

    public static final ArgumentValueState INSTANCE = new ArgumentValueState();
    public static final String ID = "PROP_VALUE";

    ArgumentValueState() {
        super(ID, false);
        this.setEnterHandler(new CharacterHandler() {
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(ctx.getCharacter() != '=') {
                    getHandler(ctx.getCharacter()).handle(ctx);
                }
            }});
        enterState('[', DefaultStateWithEndCharacter.builder("BRACKETS")
                .setLeaveStateChar(']')
                .setEndRequired(false)
                .setEnterLeaveContent(true)
                .setEnterStateHandlers(enterStateHandlers)
                .setResolveSystemProperties(false)
                .build());
        enterState('(', DefaultStateWithEndCharacter.builder("PARENTHESIS")
                .setLeaveStateChar(')')
                .setEndRequired(false)
                .setEnterLeaveContent(true)
                .setEnterStateHandlers(enterStateHandlers)
                .setResolveSystemProperties(false)
                .build());
        enterState('{', DefaultStateWithEndCharacter.builder("BRACES")
                .setLeaveStateChar('}')
                .setEndRequired(false)
                .setEnterLeaveContent(true)
                .setEnterStateHandlers(enterStateHandlers)
                .setResolveSystemProperties(false)
                .build());
        setLeaveOnWhitespace(true);
        setDefaultHandler(new WordCharacterHandler(true, false));
        enterState('"', QuotesState.QUOTES_INCLUDED_KEEP_ESCAPES);
        enterState('`', new BackQuotesState(true, false));
        setReturnHandler(new CharacterHandler() {
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(ctx.isEndOfContent()) {
                    ctx.leaveState();
                }
            }});
    }

    @Override
    public boolean lockValueIndex() {
        return true;
    }
}
