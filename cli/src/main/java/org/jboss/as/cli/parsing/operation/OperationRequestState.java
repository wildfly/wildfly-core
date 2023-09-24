/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.operation;

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
public class OperationRequestState extends DefaultParsingState {

    public static final String ID = "OP_REQ";
    public static final OperationRequestState INSTANCE = new OperationRequestState();

    public OperationRequestState() {
        this(NodeState.INSTANCE, AddressOperationSeparatorState.INSTANCE, PropertyListState.INSTANCE, HeaderListState.INSTANCE);
    }

    public OperationRequestState(final NodeState nodeState, final AddressOperationSeparatorState addrOpSep, final PropertyListState propList,
            final HeaderListState headerList) {
        super(ID);
        //setDefaultHandler(new EnterStateCharacterHandler(nodeState));
        setDefaultHandler(new LineBreakHandler(false){
            @Override
            public void doHandle(ParsingContext ctx) throws CommandFormatException {
                final CharacterHandler handler = enterStateHandlers.getHandler(ctx.getCharacter());
                if(handler == null) {
                    ctx.enterState(nodeState);
                } else {
                    handler.handle(ctx);
                }
            }});
        enterState(':', addrOpSep);
        enterState('(', propList);
        enterState('{', headerList);
        OperatorState.registerEnterStates(this);
        setReturnHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(ctx.isEndOfContent()) {
                    return;
                }
                CharacterHandler handler = enterStateHandlers.getHandler(ctx.getCharacter());
                if(handler != null) {
                    handler.handle(ctx);
                }
            }});
        setIgnoreWhitespaces(true);
    }
}
