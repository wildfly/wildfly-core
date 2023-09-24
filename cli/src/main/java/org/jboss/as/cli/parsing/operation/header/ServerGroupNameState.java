/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.operation.header;

import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.GlobalCharacterHandlers;
import org.jboss.as.cli.parsing.WordCharacterHandler;


/**
 *
 * @author Alexey Loubyansky
 */
public class ServerGroupNameState extends DefaultParsingState {

    public static final ServerGroupNameState INSTANCE = new ServerGroupNameState();
    public static final String ID = "SG_NAME";

    ServerGroupNameState() {
        super(ID);
        setEnterHandler(GlobalCharacterHandlers.CONTENT_CHARACTER_HANDLER);
        setDefaultHandler(WordCharacterHandler.LB_LEAVE_ESCAPE_ON);
        putHandler('(', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        putHandler(',', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        putHandler('^', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        putHandler('}', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        putHandler(';', GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        setLeaveOnWhitespace(true);
    }
}
