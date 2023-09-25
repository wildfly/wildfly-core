/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.operation;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.GlobalCharacterHandlers;
import org.jboss.as.cli.parsing.LineBreakHandler;
import org.jboss.as.cli.parsing.ParsingContext;


/**
 *
 * @author Alexey Loubyansky
 */
public class HeaderListState extends DefaultParsingState {

    public static final HeaderListState INSTANCE = new HeaderListState();
    public static final String ID = "HEADER_LIST";

    HeaderListState() {
        this(HeaderState.INSTANCE);
    }

    HeaderListState(final HeaderState headerState) {
        super(ID);
        putHandler('}', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        setDefaultHandler(new LineBreakHandler(false, false){
            protected void doHandle(ParsingContext ctx) throws CommandFormatException {
                ctx.enterState(headerState);
            }
        });
        setReturnHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(ctx.getCharacter() == '}') {
                    GlobalCharacterHandlers.LEAVE_STATE_HANDLER.handle(ctx);
                }
            }});
        setIgnoreWhitespaces(true);
    }
}
