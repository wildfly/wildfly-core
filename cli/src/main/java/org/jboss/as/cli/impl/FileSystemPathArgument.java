/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.impl;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.CommandHandlerWithArguments;
import org.jboss.as.cli.handlers.FilenameTabCompleter;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.parsing.ExpressionBaseState;
import org.jboss.as.cli.parsing.ParsingState;
import org.jboss.as.cli.parsing.WordCharacterHandler;

/**
*
* @author Alexey Loubyansky
*/
public class FileSystemPathArgument extends ArgumentWithValue {

    private final FilenameTabCompleter completer;

    public FileSystemPathArgument(CommandHandlerWithArguments handler, FilenameTabCompleter completer, int index, String name) {
        super(handler, completer, index, name);
        this.completer = completer;
    }

    public FileSystemPathArgument(CommandHandlerWithArguments handler, FilenameTabCompleter completer, String name) {
        super(handler, completer, name);
        this.completer = completer;
    }

    @Override
    protected ParsingState initParsingState() {
        final ExpressionBaseState state = new ExpressionBaseState("EXPR", true, false);
        if(Util.isWindows()) {
            // to not require escaping FS name separator
            state.setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_OFF);
        } else {
            state.setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_ON);
        }
        return state;
    }

    @Override
    public String getValue(ParsedCommandLine args, boolean required) throws CommandFormatException {
        return translatePath(super.getValue(args, required));
    }

    private String translatePath(String value) {
        if(value != null) {
            if(value.length() >= 0 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
                value = value.substring(1, value.length() - 1);
            }
            if(completer != null) {
                value = completer.translatePath(value);
            }
        }
        return value;
    }
}
