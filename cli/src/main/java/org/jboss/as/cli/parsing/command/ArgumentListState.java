/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.command;


import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.LineBreakHandler;
import org.jboss.as.cli.parsing.OperatorState;
import org.jboss.as.cli.parsing.ParsingContext;


/**
 *
 * @author Alexey Loubyansky
 */
public class ArgumentListState extends DefaultParsingState {

    public static final ArgumentListState INSTANCE = new ArgumentListState();
    public static final String ID = "PROP_LIST";

    ArgumentListState() {
        this(ArgumentState.INSTANCE, ArgumentValueState.INSTANCE);
    }

    ArgumentListState(ArgumentState argState, final ArgumentValueState valueState) {
        super(ID);
        setEnterHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                final CharacterHandler handler = getHandler(ctx.getCharacter());
                if(handler != null) {
                    handler.handle(ctx);
                }
            }});
        enterState('-', argState);
        setDefaultHandler(new LineBreakHandler(false){
            @Override
            protected void doHandle(ParsingContext ctx) throws CommandFormatException {
                ctx.enterState(valueState);
            }
        });
        OperatorState.registerLeaveHandlers(this);
        setIgnoreWhitespaces(true);
        setReturnHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(ctx.isEndOfContent()) {
                    ctx.leaveState();
                } else {
                    getHandler(ctx.getCharacter()).handle(ctx);
                }
            }});
    }
}
