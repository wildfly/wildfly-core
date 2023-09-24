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
public class PropertyListState extends DefaultParsingState {

    public static final PropertyListState INSTANCE = new PropertyListState();
    public static final String ID = "PROP_LIST";

    PropertyListState() {
        this(PropertyState.INSTANCE);
    }

    PropertyListState(PropertyState propState) {
        this('(', ',', propState, ')');
    }

    public PropertyListState(char listStart, char propSeparator, char... listEnd) {
        this(listStart, propSeparator, new PropertyState(propSeparator, listEnd), listEnd);
    }

    PropertyListState(final char listStart, char propSeparator, final PropertyState propState, final char... listEnd) {
        super(ID);
        for(int i = 0; i < listEnd.length; ++i) {
            putHandler(listEnd[i], GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        }
        setEnterHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(ctx.getCharacter() != listStart) {
                    ctx.enterState(propState);
                }
            }});
        setDefaultHandler(new LineBreakHandler(false){
            @Override
            protected void doHandle(ParsingContext ctx) throws CommandFormatException {
                ctx.enterState(propState);
            }
        });
        putHandler(propSeparator, GlobalCharacterHandlers.NOOP_CHARACTER_HANDLER);
        setReturnHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(ctx.isEndOfContent()) {
                    return;
                }
                getHandler(ctx.getCharacter()).handle(ctx);
            }});
        //this.setEndContentHandler(new ErrorCharacterHandler("')' is missing"));
        setIgnoreWhitespaces(true);
    }
}
