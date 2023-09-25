/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.operation;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.ExpressionBaseState;
import org.jboss.as.cli.parsing.GlobalCharacterHandlers;
import org.jboss.as.cli.parsing.OperatorState;
import org.jboss.as.cli.parsing.ParsingContext;
import org.jboss.as.cli.parsing.WordCharacterHandler;


/**
 *
 * @author Alexey Loubyansky
 */
public final class OperationNameState extends ExpressionBaseState {

    public static final String ID = "OP_NAME";
    public static final OperationNameState INSTANCE = new OperationNameState();

    public OperationNameState() {
        super(ID);
        setIgnoreWhitespaces(true);
        setEnterHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                WordCharacterHandler.IGNORE_LB_ESCAPE_ON.handle(ctx);
            }});
        setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_ON);
        putHandler('(', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        putHandler('{', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        OperatorState.registerLeaveHandlers(this);
    }
}