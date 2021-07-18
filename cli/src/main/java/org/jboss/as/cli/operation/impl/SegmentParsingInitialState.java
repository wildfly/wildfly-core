/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 *
 */

package org.jboss.as.cli.operation.impl;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.operation.OperationRequestCompleter;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.EscapeCharacterState;
import org.jboss.as.cli.parsing.GlobalCharacterHandlers;
import org.jboss.as.cli.parsing.ParsingContext;
import org.jboss.as.cli.parsing.ParsingStateCallbackHandler;
import org.jboss.as.cli.parsing.QuotesState;

/**
 * Parses operation address segments and counts quotes and leading spaces.
 *
 * @see SegmentParsingCallbackHandler
 * @author Tomas Hofman (thofman@redhat.com)
 */
public class SegmentParsingInitialState extends DefaultParsingState {

    public static final SegmentParsingInitialState INSTANCE = new SegmentParsingInitialState();

    public SegmentParsingInitialState() {
        super("SEGMENT");

        final LeadingWhitespaceState leadingWhitespaceState = new LeadingWhitespaceState();
        setEnterHandler(ctx -> {
            if (Character.isWhitespace(ctx.getCharacter())) {
                ctx.enterState(leadingWhitespaceState);
            }
        });

        setDefaultHandler(GlobalCharacterHandlers.CONTENT_CHARACTER_HANDLER);
        enterState('"', QuotesState.QUOTES_EXCLUDED);
        enterState('\\', EscapeCharacterState.INSTANCE);
    }

    private static class LeadingWhitespaceState extends DefaultParsingState {

        public static final String ID = "LEADING_WS";

        LeadingWhitespaceState() {
            super(ID);
            setDefaultHandler(ctx -> {
                if(!Character.isWhitespace(ctx.getCharacter())) {
                    ctx.leaveState();
                    ctx.resolveExpression(true, true);
                    ctx.getState().getHandler(ctx.getCharacter()).handle(ctx);
                } else {
                    ctx.getCallbackHandler().character(ctx);
                }
            });
        }
    }


    public static class SegmentParsingCallbackHandler implements ParsingStateCallbackHandler {

        private int offset = 0;
        private int unescapedInQuotes = 0;
        private int escapedInQuotes = 0;
        private int escapedOutsideQuotes = 0;
        private boolean openQuotes = false;

        @Override
        public void enteredState(ParsingContext ctx) throws CommandFormatException {
            // increment offset on start quote
            if (QuotesState.ID.equals(ctx.getState().getId())) {
                offset++;
                openQuotes = true;
            }
        }

        @Override
        public void leavingState(ParsingContext ctx) throws CommandFormatException {
            // increment offset on end quote
            if (QuotesState.ID.equals(ctx.getState().getId()) && !ctx.isEndOfContent()) {
                offset++;
                openQuotes = false;
            }
        }

        @Override
        public void character(ParsingContext ctx) throws CommandFormatException {
            char ch = ctx.getCharacter();

            // increment offset for every leading space
            if (LeadingWhitespaceState.ID.equals(ctx.getState().getId())) {
                offset++;
            } else if (EscapeCharacterState.ID.equals(ctx.getState().getId())) {
                // count unnecessarily escaped characters in quotes (only " and \ chars must be escaped in quotes)
                if (openQuotes && OperationRequestCompleter.ESCAPE_SELECTOR.isEscape(ch)
                        && !OperationRequestCompleter.ESCAPE_SELECTOR_INSIDE_QUOTES.isEscape(ch)) {
                    escapedInQuotes++;
                }
                // count escaped characters outside quotes
                else if (!openQuotes && OperationRequestCompleter.ESCAPE_SELECTOR.isEscape(ch)
                        && !OperationRequestCompleter.ESCAPE_SELECTOR_INSIDE_QUOTES.isEscape(ch)) {
                    escapedOutsideQuotes++;
                }
            }
            // count unescaped characters in quotes (only those that should be escaped normally)
            else if (QuotesState.ID.equals(ctx.getState().getId())
                    && OperationRequestCompleter.ESCAPE_SELECTOR.isEscape(ch)
                    && !OperationRequestCompleter.ESCAPE_SELECTOR_INSIDE_QUOTES.isEscape(ch)) {
                unescapedInQuotes++;
            }
        }

        public int getOffset() {
            if (openQuotes) {
                return offset + escapedInQuotes + escapedOutsideQuotes;
            } else {
                return offset - unescapedInQuotes;
            }
        }

        public boolean isOpenQuotes() {
            return openQuotes;
        }

        public void reset() {
            offset = 0;
            unescapedInQuotes = 0;
            escapedInQuotes = 0;
            escapedOutsideQuotes = 0;
            openQuotes = false;
        }
    }

}
