/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.operation.header;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.EnterStateCharacterHandler;
import org.jboss.as.cli.parsing.GlobalCharacterHandlers;
import org.jboss.as.cli.parsing.LineBreakHandler;
import org.jboss.as.cli.parsing.ParsingContext;
import org.jboss.as.cli.parsing.operation.PropertyListState;


/**
 *
 * @author Alexey Loubyansky
 */
public class ServerGroupState extends DefaultParsingState {

    public static final ServerGroupState INSTANCE = new ServerGroupState();
    public static final String ID = "SG";

    ServerGroupState() {
        this(ServerGroupNameState.INSTANCE, PropertyListState.INSTANCE);
    }

    ServerGroupState(ServerGroupNameState name, final PropertyListState propList) {
        super(ID);
        this.setIgnoreWhitespaces(true);
        setEnterHandler(new EnterStateCharacterHandler(name));
        putHandler(',', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        putHandler('^', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        putHandler('}', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        putHandler(';', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        putHandler('(', new EnterStateCharacterHandler(propList));
        setDefaultHandler(new LineBreakHandler(false, false){
            @Override
            protected void doHandle(ParsingContext ctx) throws CommandFormatException {
                final char ch = ctx.getCharacter();
                if(ch == '(') {
                    ctx.enterState(propList);
                } else if(ch != ')') {
                    ctx.leaveState();
                }
            }});
        setReturnHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(ctx.isEndOfContent()) {
                    return;
                }
/*                if(Character.isWhitespace(ctx.getCharacter())) {
                    ctx.leaveState();
                } else*/ {
                    getHandler(ctx.getCharacter()).handle(ctx);
                }
            }});
    }
}
