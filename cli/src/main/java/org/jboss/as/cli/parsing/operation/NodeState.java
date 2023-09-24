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
public class NodeState extends ExpressionBaseState {

    public static final String ID = "NODE";
    public static final NodeState INSTANCE = new NodeState();

    public NodeState() {
        super(ID);

        setEnterHandler(new CharacterHandler(){

            @Override
            public void handle(ParsingContext ctx)
                    throws CommandFormatException {
                if(ctx.getCharacter() == '/') {
                    ctx.leaveState();
                } else {
                    getHandler(ctx.getCharacter()).handle(ctx);
                }
            }});

        setIgnoreWhitespaces(true);
        setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_ON);

        putHandler('=', new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx)
                    throws CommandFormatException {
                ctx.leaveState();
            }});

        putHandler('/', new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx)
                    throws CommandFormatException {
                ctx.leaveState();
            }});

        putHandler(':', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);

        enterState('"', QuotesState.QUOTES_EXCLUDED);
    }

    @Override
    public boolean lockValueIndex() {
        return true;
    }
}
