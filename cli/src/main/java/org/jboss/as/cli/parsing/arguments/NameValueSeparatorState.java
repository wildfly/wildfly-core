/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.arguments;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.ParsingContext;

/**
 *
 * @author Alexey Loubyansky
 */
public class NameValueSeparatorState extends DefaultParsingState {

    public static final String ID = "NAME_VALUE_SEP";

    public static final NameValueSeparatorState INSTANCE = new NameValueSeparatorState();

    public NameValueSeparatorState() {
        super(ID);
        setEnterHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                try {
                    // check if it's native DMR '=>' instead of simple '='
                    if(ctx.getInput().charAt(ctx.getLocation() + 1) == '>') {
                        ctx.advanceLocation(1);
                    }
                } catch(IndexOutOfBoundsException e) {
                    // ok
                }
                ctx.leaveState(); // current state
                ctx.leaveState(); // argument value state
            }});
    }
}
