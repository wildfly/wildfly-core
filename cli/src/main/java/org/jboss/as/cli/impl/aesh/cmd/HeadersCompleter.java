/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd;

import java.util.ArrayList;
import java.util.List;
import org.aesh.command.completer.OptionCompleter;
import org.jboss.as.cli.CommandFormatException;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.jboss.as.cli.operation.OperationRequestCompleter;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;

/**
 *
 * Complete Headers.
 *
 * @author jdenise
 */
public class HeadersCompleter implements OptionCompleter<CLICompleterInvocation> {

    private final DefaultCallbackHandler handler = new DefaultCallbackHandler();

    @Override
    public void complete(CLICompleterInvocation cliCompleterInvocation) {
        List<String> candidates = new ArrayList<>();
        int pos = 0;
        if (cliCompleterInvocation.getGivenCompleteValue() != null) {
            pos = cliCompleterInvocation.getGivenCompleteValue().length();
        }
        if (pos == 0) {
            candidates.add("{");

        } else {
            try {
                handler.parseOperation(null, cliCompleterInvocation.getGivenCompleteValue(),
                        cliCompleterInvocation.getCommandContext());
            } catch (CommandFormatException e) {
                //e.printStackTrace();
                return;
            }
            int cursor = 0;
            if (handler.endsOnHeaderListStart() || handler.hasHeaders()) {
                cursor = OperationRequestCompleter.INSTANCE.complete(cliCompleterInvocation.
                        getCommandContext(), handler, cliCompleterInvocation.getGivenCompleteValue(), pos, candidates);
            }
            cliCompleterInvocation.setOffset(cliCompleterInvocation.getGivenCompleteValue().length() - cursor);
        }
        cliCompleterInvocation.addAllCompleterValues(candidates);
        cliCompleterInvocation.setAppendSpace(false);
    }

}
