/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl;

import java.util.Collection;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.ArgumentValueConverter.DMRWithFallbackConverter;
import org.jboss.as.cli.operation.ParsedOperationRequestHeader;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.ParserUtil;
import org.jboss.as.cli.parsing.operation.HeaderListState;
import org.jboss.dmr.ModelNode;

/**
*
* @author Alexey Loubyansky
*/
public final class HeadersArgumentValueConverter extends DMRWithFallbackConverter {

    public static final HeadersArgumentValueConverter INSTANCE = new HeadersArgumentValueConverter();

    private final DefaultCallbackHandler callback = new DefaultCallbackHandler();
    private final DefaultParsingState initialState = new DefaultParsingState("INITIAL_STATE");
    {
        initialState.enterState('{', HeaderListState.INSTANCE);
    }

    @Override
    protected ModelNode fromNonDMRString(CommandContext ctx, String value) throws CommandFormatException {
        callback.reset();
        ParserUtil.parse(value, callback, initialState);
        final Collection<ParsedOperationRequestHeader> headers = callback.getHeaders();
        if(headers.isEmpty()) {
            throw new CommandFormatException("'" + value +
                    "' doesn't follow format {[rollout server_group_list [rollback-across-groups];] (<header_name>=<header_value>;)*}");
        }
        final ModelNode node = new ModelNode();
        for(ParsedOperationRequestHeader header : headers) {
            header.addTo(ctx, node);
        }
        return node;
    }
}