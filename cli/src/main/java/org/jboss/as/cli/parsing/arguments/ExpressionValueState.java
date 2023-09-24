/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.parsing.arguments;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.GlobalCharacterHandlers;
import org.jboss.as.cli.parsing.ParsingContext;
import org.jboss.as.cli.parsing.WordCharacterHandler;

/**
 * @author Alexey Loubyansky
 *
 */
public class ExpressionValueState extends DefaultParsingState {

    public static final String ID = "EXPR_VALUE";

    public static final ExpressionValueState INSTANCE = new ExpressionValueState();

    public ExpressionValueState() {
        super(ID);
        setEnterHandler(GlobalCharacterHandlers.CONTENT_CHARACTER_HANDLER);
        setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_ON);
        putHandler('}', new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                ctx.getCallbackHandler().character(ctx);
                ctx.leaveState();
            }});
    }
}
