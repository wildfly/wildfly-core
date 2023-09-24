/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation.impl;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.operation.CommandLineParser;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.parsing.ParserUtil;

/**
 * Default implementation of CommandParser which expects the following command format:
 *
 * [node-type=node-name (, node-type=node-name)*] : operation-name ['(' name=value (, name=value)* ')' ]
 *
 * the whitespaces are insignificant. E.g.
 *
 * profile=production,subsystem=threads,bounded-queue-thread-pool=pool1:write-core-threads(count=0, per-cpu=20)
 *
 * Each node-type, node-name, operation-name and the argument name as strings are checked to be valid identifiers,
 * i.e. the Character.isJavaIdentifierStart(c) should return true for the first character and the rest should
 * satisfy (Character.isJavaIdentifierPart(c) || c == '-')
 *
 * This implementation is thread-safe. The same instance of this class can be re-used multiple times and
 * can be accessed from multiple threads concurrently w/o synchronization.
 *
 * @author Alexey Loubyansky
 */
public class DefaultOperationRequestParser implements CommandLineParser {

    public static final CommandLineParser INSTANCE = new DefaultOperationRequestParser();

/*    public static final String FORMAT = "[node-type=node-name (, node-type=node-name)*] : operation-name [ '(' name=value (, name=value)* ')' ]";
    public static final char NODE_TYPE_NAME_SEPARATOR = '=';
    public static final char NODE_SEPARATOR = '/';
    public static final char ADDRESS_OPERATION_NAME_SEPARATOR = ':';
    public static final char ARGUMENTS_LIST_START = '(';
    public static final char ARGUMENT_NAME_VALUE_SEPARATOR = '=';
    public static final char ARGUMENT_SEPARATOR = ',';
    public static final char ARGUMENTS_LIST_END = ')';
    public static final String ROOT_NODE = "/";
    public static final String PARENT_NODE = "..";
    public static final String NODE_TYPE = ".type";
*/
    private final CommandContext ctx;

    public DefaultOperationRequestParser() {
        this(null);
    }

    public DefaultOperationRequestParser(CommandContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void parse(String operationRequest, final CallbackHandler handler) throws OperationFormatException {

        if(operationRequest == null || operationRequest.isEmpty()) {
            return;
        }

        try {
            ParserUtil.parseOperationRequest(operationRequest, handler, ctx);
        } catch (CommandFormatException e) {
            throw new OperationFormatException("Failed to parse '" + operationRequest + "'", e);
        }
    }

}
