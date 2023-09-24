/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.operation.header;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.ParsingContext;


/**
 *
 * @author Alexey Loubyansky
 */
public class InSeriesState extends DefaultParsingState {

    public static final InSeriesState INSTANCE = new InSeriesState();
    public static final String ID = "IN_SERIES";

    InSeriesState() {
        super(ID);
        setDefaultHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(Character.isWhitespace(ctx.getCharacter())) {
                    ctx.leaveState();
                } else {
                    ctx.getCallbackHandler().character(ctx);
                }
            }});
    }
}
