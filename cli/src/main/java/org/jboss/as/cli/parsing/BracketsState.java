/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing;

/**
 *
 * @author Alexey Loubyansky
 */
public class BracketsState extends DefaultParsingState {

    static final BracketsState QUOTES_EXCLUDED = new BracketsState(false);
    static final BracketsState QUOTES_INCLUDED = new BracketsState(true);

    public BracketsState(boolean quotesInContent) {
        super("BRACKETS", quotesInContent);

        this.setEndContentHandler(new ErrorCharacterHandler("The closing ']' is missing."));
        this.putHandler(']', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        this.enterState('\\', EscapeCharacterState.INSTANCE);
        this.setDefaultHandler(GlobalCharacterHandlers.CONTENT_CHARACTER_HANDLER);
    }

}
