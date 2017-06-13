/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
 */
package org.jboss.as.cli.parsing.operation;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.CharacterHandler;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.GlobalCharacterHandlers;
import org.jboss.as.cli.parsing.LineBreakHandler;
import org.jboss.as.cli.parsing.ParsingContext;


/**
 *
 * @author Alexey Loubyansky
 */
public class HeaderListState extends DefaultParsingState {

    public static final HeaderListState INSTANCE = new HeaderListState();

    private static final CharacterHandler LEAVE_PARSING_STATE_HANDLER = new CharacterHandler() {

        @Override
        public void handle(ParsingContext ctx)
                throws CommandFormatException {
            ctx.leaveState();
            ctx.terminateParsing();
        }
    };

    public static final HeaderListState INSTANCE_LEAVE_PARSING = new HeaderListState(HeaderState.INSTANCE, LEAVE_PARSING_STATE_HANDLER);
    public static final String ID = "HEADER_LIST";

    HeaderListState() {
        this(HeaderState.INSTANCE, GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
    }

    HeaderListState(final HeaderState headerState, CharacterHandler leaveState) {
        super(ID);
        putHandler('}', leaveState);
        setDefaultHandler(new LineBreakHandler(false, false){
            protected void doHandle(ParsingContext ctx) throws CommandFormatException {
                ctx.enterState(headerState);
            }
        });
        setReturnHandler(new CharacterHandler(){
            @Override
            public void handle(ParsingContext ctx) throws CommandFormatException {
                if(ctx.getCharacter() == '}') {
                    leaveState.handle(ctx);
                }
            }});
        setIgnoreWhitespaces(true);
    }
}
