/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.arguments;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.BackQuotesState;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.ParsingContext;
import org.jboss.as.cli.parsing.QuotesState;
import org.jboss.as.cli.parsing.WordCharacterHandler;

/**
 *
 * @author Alexey Loubyansky
 */
public class NonObjectArgumentValueState extends DefaultParsingState {

    public static final String ID = ArgumentValueState.ID;

    public static final NonObjectArgumentValueState INSTANCE = new NonObjectArgumentValueState();

    public NonObjectArgumentValueState() {
        super(ID);
        setEnterHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                final char ch = ctx.getCharacter();
                switch(ch) {
                    case '"':
                        ctx.enterState(QuotesState.QUOTES_EXCLUDED);
                        break;
                    case '$':
                        ctx.enterState(ExpressionValueState.INSTANCE);
                        break;
                    case '`':
                        ctx.enterState(BackQuotesState.QUOTES_INCLUDED);
                        break;
                    default:
                        ctx.getCallbackHandler().character(ctx);
                }
            }});
        setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_ON);
        enterState('"', QuotesState.QUOTES_INCLUDED);
        enterState('`', BackQuotesState.QUOTES_INCLUDED);
        enterState('$', ExpressionValueState.INSTANCE);
    }
}
