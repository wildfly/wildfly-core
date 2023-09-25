/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing;

import org.jboss.as.cli.CommandFormatException;


/**
 *
 * @author Alexey Loubyansky
 */
public class BasicInitialParsingState extends DefaultParsingState {

    public static final BasicInitialParsingState INSTANCE = new BasicInitialParsingState("DEFAULT");

    private static final ParsingState DEFAULT_STATE = new DefaultParsingState("STRING", false, GlobalCharacterHandlers.GLOBAL_ENTER_STATE_HANDLERS){
        {
            this.setEnterHandler(GlobalCharacterHandlers.CONTENT_CHARACTER_HANDLER);
            this.setDefaultHandler(GlobalCharacterHandlers.CONTENT_CHARACTER_HANDLER);
        }
    };

    BasicInitialParsingState(String id) {
        super(id, false, GlobalCharacterHandlers.GLOBAL_ENTER_STATE_HANDLERS);
        setDefaultHandler(new CharacterHandler() {
            @Override
            public void handle(ParsingContext ctx)
                    throws CommandFormatException {
                ctx.enterState(DEFAULT_STATE);
            }
        });
    }
}
