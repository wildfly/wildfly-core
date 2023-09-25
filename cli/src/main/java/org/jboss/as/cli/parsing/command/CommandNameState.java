/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.command;


import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.ExpressionBaseState;
import org.jboss.as.cli.parsing.ParsingContext;
import org.jboss.as.cli.parsing.WordCharacterHandler;


/**
 *
 * @author Alexey Loubyansky
 */
public class CommandNameState extends ExpressionBaseState {

    public static final CommandNameState INSTANCE = new CommandNameState();
    public static final String ID = "OP_NAME";

    CommandNameState() {
        super(ID);
        setLeaveOnWhitespace(true);
        setEnterHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                WordCharacterHandler.IGNORE_LB_ESCAPE_ON.handle(ctx);
            }});
        setDefaultHandler(WordCharacterHandler.LB_LEAVE_ESCAPE_ON);
    }
}
