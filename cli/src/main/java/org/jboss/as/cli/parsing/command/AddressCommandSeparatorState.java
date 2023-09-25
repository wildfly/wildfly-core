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
import org.jboss.as.cli.parsing.ParsingContext;

/**
 *
 * @author Alexey Loubyansky
 */
public class AddressCommandSeparatorState extends DefaultParsingState {

    public static final AddressCommandSeparatorState INSTANCE = new AddressCommandSeparatorState();

    public AddressCommandSeparatorState() {
        this(CommandNameState.INSTANCE);
    }

    public AddressCommandSeparatorState(final CommandNameState opName) {
        super("ADDR_OP_SEP");
        setEnterHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(ctx.isEndOfContent()) {
                    ctx.leaveState();
                }
                final char ch = ctx.getCharacter();
                if(!Character.isWhitespace(ch)) {
                    final CharacterHandler handler = getHandler(ch);
                    if(handler != null) {
                        handler.handle(ctx);
                    }
                }
            }});
        setDefaultHandler(new EnterStateCharacterHandler(opName));
        setReturnHandler(GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        setIgnoreWhitespaces(true);
    }

}
