/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation.impl;

import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.operation.OperationRequestCompleter;

/**
 *
 * @author Alexey Loubyansky
 */
public class HeadersCompleter implements CommandLineCompleter {

    public static final HeadersCompleter INSTANCE = new HeadersCompleter();

    private final DefaultCallbackHandler handler = new DefaultCallbackHandler();

    @Override
    public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {

        // if this is value completion of --headers, the parser trims values, so last spaces will be removed
        // which is not good here
        final String originalBuffer = ctx.getParsedCommandLine().getOriginalLine();
        int valueIndex = originalBuffer.lastIndexOf(buffer);
        if(valueIndex == -1) {
            return -1;
        }
        buffer = originalBuffer.substring(valueIndex);
        if (buffer.isEmpty()) {
            candidates.add("{");
            return 0;
        }
        try {
            handler.parseOperation(null, buffer, ctx);
        } catch (CommandFormatException e) {
            //e.printStackTrace();
            return -1;
        }
        if(handler.endsOnHeaderListStart() || handler.hasHeaders()) {
            return OperationRequestCompleter.INSTANCE.complete(ctx, handler, buffer, cursor, candidates);
        }
        return -1;
    }
}
