/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.arguments;

import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.GlobalCharacterHandlers;

/**
 *
 * @author Alexey Loubyansky
 */
public class ListItemSeparatorState extends DefaultParsingState {

    public static final String ID = "LIST_ITEM_SEP";

    public static final ListItemSeparatorState INSTANCE = new ListItemSeparatorState();

    public ListItemSeparatorState() {
        super(ID);
        setEnterHandler(GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
    }
}
