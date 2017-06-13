/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli.impl.aesh.completer;

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
                handler.parseOperation(null, cliCompleterInvocation.getGivenCompleteValue());
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
