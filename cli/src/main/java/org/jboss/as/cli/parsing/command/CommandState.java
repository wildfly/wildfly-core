/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.command;


import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.EnterStateCharacterHandler;
import org.jboss.as.cli.parsing.LineBreakHandler;
import org.jboss.as.cli.parsing.OperatorState;
import org.jboss.as.cli.parsing.ParsingContext;


/**
 *
 * @author Alexey Loubyansky
 */
public class CommandState extends DefaultParsingState {

    public static final CommandState INSTANCE = new CommandState();
    public static final String ID = "CMD";

    CommandState() {
        this(AddressCommandSeparatorState.INSTANCE, ArgumentListState.INSTANCE);
    }

    CommandState(AddressCommandSeparatorState addrCmdSeparator, final ArgumentListState argList) {
        super(ID);
        setEnterHandler(new EnterStateCharacterHandler(addrCmdSeparator));
        setDefaultHandler(new LineBreakHandler(false, false){
            @Override
            protected void doHandle(ParsingContext ctx) throws CommandFormatException {
                ctx.enterState(argList);
            }
        });
        this.setReturnHandler(new CharacterHandler() {
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(ctx.isEndOfContent()) {
                    ctx.leaveState();
                    return;
                }
                final CharacterHandler handler = getHandler(ctx.getCharacter());
                if(handler != null) {
                    handler.handle(ctx);
                }
            }});
        OperatorState.registerEnterStates(this);
    }
}
