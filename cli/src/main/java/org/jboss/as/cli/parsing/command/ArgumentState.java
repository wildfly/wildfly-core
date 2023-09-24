/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.command;


import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.EnterStateCharacterHandler;
import org.jboss.as.cli.parsing.GlobalCharacterHandlers;
import org.jboss.as.cli.parsing.LineBreakHandler;
import org.jboss.as.cli.parsing.ParsingContext;
import org.jboss.as.cli.parsing.WordCharacterHandler;


/**
 *
 * @author Alexey Loubyansky
 */
public class ArgumentState extends DefaultParsingState {

    public static final ArgumentState INSTANCE = new ArgumentState();
    public static final String ID = "PROP";

    ArgumentState() {
        this(ArgumentValueState.INSTANCE);
    }

    ArgumentState(ArgumentValueState valueState) {
        super(ID);
        setEnterHandler(GlobalCharacterHandlers.CONTENT_CHARACTER_HANDLER);
        final NameValueSeparatorState nvSep = new NameValueSeparatorState(valueState);
        enterState('=', nvSep);
        //setLeaveOnWhitespace(true);
        setDefaultHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                ctx.resolveExpression(true, true);
                WordCharacterHandler.IGNORE_LB_ESCAPE_ON.handle(ctx);
            }});
        setWhitespaceHandler(new EnterStateCharacterHandler(new WhitespaceState()));
        setReturnHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(ctx.isEndOfContent()) {
                    ctx.leaveState();
                } else if(ctx.getCharacter() == '=') {
                    ctx.enterState(nvSep);
                } else {
                    ctx.leaveState();
                }
            }});
    }

    private static class WhitespaceState extends DefaultParsingState {
        public WhitespaceState() {
            super("WS");
            setDefaultHandler(new CharacterHandler(){
                @Override
                public void handle(ParsingContext ctx) throws CommandFormatException {
                    if(!Character.isWhitespace(ctx.getCharacter())) {
                        ctx.leaveState();
                    }
                }});
        }
        @Override
        public boolean updateValueIndex() {
            return false;
        }
    }

    private static class NameValueSeparatorState extends DefaultParsingState {
        NameValueSeparatorState(final ArgumentValueState valueState) {
            super("NAME_VALUE_SEPARATOR");
            setDefaultHandler(new LineBreakHandler(false, false){
                @Override
                protected void doHandle(ParsingContext ctx) throws CommandFormatException {
                    ctx.enterState(valueState);
                }
            });
            setReturnHandler(GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
            setIgnoreWhitespaces(true);
        }
    }
}
