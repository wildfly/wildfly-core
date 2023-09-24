/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.arguments;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.LineBreakHandler;
import org.jboss.as.cli.parsing.ParsingContext;

/**
 *
 * @author Alexey Loubyansky
 */
public class CompositeState extends DefaultParsingState {

    public static final String LIST = "LIST";
    public static final String OBJECT = "OBJECT";

    public static final String ID = OBJECT;

    public CompositeState(final ArgumentValueState value) {
        this(false, value);
    }

    public CompositeState(boolean list, final ArgumentValueState value) {
        super(list ? LIST : OBJECT);

        setEnterHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                final char c = ctx.getCharacter();
                if(c == '{') {
                    ctx.lookFor('}');
                } else if(c == '[') {
                    ctx.lookFor(']');
                } else {
                    ctx.enterState(value);
                }
                ctx.activateControl('=');
            }});
        setDefaultHandler(new LineBreakHandler(false, false){
            @Override
            protected void doHandle(ParsingContext ctx) throws CommandFormatException {
                final char c = ctx.getCharacter();
                if((c == ']' || c == '}') && ctx.meetIfLookedFor(c)) {
                    ctx.leaveState();
                } else {
                    ctx.enterState(value);
                }
            }
        });
        setIgnoreWhitespaces(true);
        enterState(',', ListItemSeparatorState.INSTANCE);
        enterState('[', this);
        enterState('{', this);
        setReturnHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(ctx.isEndOfContent()) {
                    return;
                }
                final char c = ctx.getCharacter();
                if(c == '=' || c == '>' /*alternative equals =>*/) {
                    ctx.deactivateControl('=');
                    return;
                }
                ctx.activateControl('=');
                if(c == ',') {
                    return;
                }
                if(c == ']' || c == '}') {
                    if(ctx.meetIfLookedFor(c)) {
                        ctx.leaveState();
                    }
                    return;
                }
                getHandler(c).handle(ctx);
            }});
    }
}
