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
import org.jboss.as.cli.parsing.ParsingContext;


/**
 *
 * @author Alexey Loubyansky
 */
public class ServerGroupListState extends DefaultParsingState {

    public static final ServerGroupListState INSTANCE = new ServerGroupListState();
    public static final String ID = "SG_LIST";

    ServerGroupListState() {
        this(ServerGroupState.INSTANCE, ServerGroupSeparatorState.INSTANCE, ConcurrentSignState.INSTANCE);
    }

    ServerGroupListState(final ServerGroupState sg, final ServerGroupSeparatorState gs, final ConcurrentSignState cs) {
        super(ID);
        this.setIgnoreWhitespaces(true);
        setDefaultHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                ctx.leaveState();
            }});
        setEnterHandler(new EnterStateCharacterHandler(sg));
        putHandler('^', new EnterStateCharacterHandler(cs));
        putHandler(',', new EnterStateCharacterHandler(gs));
        putHandler('}', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        putHandler(';', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        setReturnHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(ctx.isEndOfContent()) {
                    return;
                }
/*                if(Character.isWhitespace(ctx.getCharacter())) {
                    ctx.leaveState();
                } else {
                    getHandler(ctx.getCharacter()).handle(ctx);
                }
*/
                if(Character.isWhitespace(ctx.getCharacter())) {
                    return;
                }

                switch(ctx.getCharacter()) {
                case '^':
                    ctx.enterState(cs);
                    break;
                case ',':
                    ctx.enterState(gs);
                    break;
//                case '}':
//                case ';':
//                    ctx.leaveState();
//                    break;
                default:
                    ctx.leaveState();
                }
            }});
    }
}
