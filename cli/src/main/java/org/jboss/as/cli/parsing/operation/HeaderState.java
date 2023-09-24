/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.operation;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.EnterStateCharacterHandler;
import org.jboss.as.cli.parsing.GlobalCharacterHandlers;
import org.jboss.as.cli.parsing.LineBreakHandler;
import org.jboss.as.cli.parsing.ParsingContext;


/**
 *
 * @author Alexey Loubyansky
 */
public class HeaderState extends DefaultParsingState {

    public static final HeaderState INSTANCE = new HeaderState();
    public static final String ID = "HEADER";

    HeaderState() {
        this(HeaderNameState.INSTANCE, HeaderValueState.INSTANCE);
    }

    HeaderState(HeaderNameState headerName, final HeaderValueState headerValue) {
        super(ID);
        this.setIgnoreWhitespaces(true);
        setEnterHandler(new EnterStateCharacterHandler(headerName));
        putHandler(';', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        putHandler('}', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        final NameValueSeparatorState nameValueSep = new NameValueSeparatorState(headerValue);
        enterState('=', nameValueSep);
        setDefaultHandler(new EnterStateCharacterHandler(headerValue));
        setReturnHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(ctx.isEndOfContent()) {
                    return;
                }
                final char ch = ctx.getCharacter();
                if(ch == '=') {
                    ctx.enterState(nameValueSep);
                } else if (!Character.isWhitespace(ch) && ch != '\\') {
                    ctx.leaveState();
                }
            }});
    }

    private static class NameValueSeparatorState extends DefaultParsingState {
        NameValueSeparatorState(final HeaderValueState valueState) {
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
