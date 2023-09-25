/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing;

/**
 *
 * @author Alexey Loubyansky
 */
public class QuotesState extends DefaultParsingState {

    public static final String ID = "QUOTES";

    public static final QuotesState QUOTES_EXCLUDED = new QuotesState(false);
    public static final QuotesState QUOTES_INCLUDED = new QuotesState(true);
    public static final QuotesState QUOTES_INCLUDED_KEEP_ESCAPES = new QuotesState(true, EscapeCharacterState.KEEP_ESCAPE);

    public QuotesState(boolean quotesInContent) {
        this(quotesInContent, true);
    }

    public QuotesState(boolean quotesInContent, boolean escapeEnabled) {
        this(quotesInContent, escapeEnabled ? EscapeCharacterState.INSTANCE : null);
    }

    public QuotesState(boolean quotesInContent, EscapeCharacterState escape) {
        super(ID, quotesInContent);

        this.setEndContentHandler(new ErrorCharacterHandler("The closing '\"' is missing."));
        this.putHandler('"', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        if(escape != null) {
            this.enterState('\\', escape);
        }
        this.setDefaultHandler(GlobalCharacterHandlers.CONTENT_CHARACTER_HANDLER);
    }

}
