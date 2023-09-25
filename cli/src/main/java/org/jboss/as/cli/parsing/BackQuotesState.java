/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing;

/**
 *
 * @author Alexey Loubyansky
 */
public class BackQuotesState extends DefaultParsingState {

    public static final String ID = "BQUOTES";

    public static final BackQuotesState QUOTES_INCLUDED = new BackQuotesState(true);
    public static final BackQuotesState QUOTES_INCLUDED_KEEP_ESCAPES = new BackQuotesState(true, EscapeCharacterState.KEEP_ESCAPE);

    public BackQuotesState(boolean quotesInContent) {
        this(quotesInContent, true);
    }

    public BackQuotesState(boolean quotesInContent, boolean escapeEnabled) {
        this(quotesInContent, escapeEnabled ? EscapeCharacterState.INSTANCE : null);
    }

    public BackQuotesState(boolean quotesInContent, EscapeCharacterState escape) {
        super(ID, quotesInContent);

        this.setEndContentHandler(new ErrorCharacterHandler("The closing ` is missing."));
        this.putHandler('`', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        if(escape != null) {
            this.enterState('\\', escape);
        }
        this.setDefaultHandler(GlobalCharacterHandlers.CONTENT_CHARACTER_HANDLER);
    }
}
