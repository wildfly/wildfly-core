/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing;


import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.command.CommandState;
import org.jboss.as.cli.parsing.operation.OperationRequestState;


/**
 *
 * @author Alexey Loubyansky
 */
public class InitialState extends DefaultParsingState {

    public static final InitialState INSTANCE;
    static {
        OperationRequestState opState = new OperationRequestState();
        opState.setHandleEntrance(true);
        INSTANCE = new InitialState(opState, CommandState.INSTANCE);
    }
    public static final String ID = "INITIAL";

    InitialState() {
        this(OperationRequestState.INSTANCE, CommandState.INSTANCE);
    }

    InitialState(OperationRequestState opState, final CommandState cmdState) {
        super(ID);
        final LeadingWhitespaceState leadingWs = new LeadingWhitespaceState();
        leadingWs.setEndContentHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                ctx.enterState(cmdState);
            }});
        this.setEnterHandler(new CharacterHandler() {
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(Character.isWhitespace(ctx.getCharacter())) {
                    ctx.enterState(leadingWs);
                } else {
                    ctx.resolveExpression(true, true);
                }
            }});
        enterState('.', opState);
        enterState(':', opState);
        enterState('/', opState);

        setDefaultHandler(new CharacterHandler() {
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                ctx.enterState(cmdState);
            }});
        setIgnoreWhitespaces(true);
    }

    class LeadingWhitespaceState extends DefaultParsingState {

        public LeadingWhitespaceState() {
            super("WS");
            setDefaultHandler(new CharacterHandler() {
                @Override
                public void handle(ParsingContext ctx) throws CommandFormatException {
                    if(!Character.isWhitespace(ctx.getCharacter())) {
                        ctx.leaveState();
                        ctx.resolveExpression(true, true);
                        InitialState.this.getHandler(ctx.getCharacter()).handle(ctx);
                    }
                }});
        }
    }
}
