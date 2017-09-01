/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.wildfly.core.cli.command.aesh;

import java.util.ArrayList;
import java.util.List;
import org.aesh.command.completer.OptionCompleter;
import org.jboss.as.cli.handlers.FilenameTabCompleter;

/**
 *
 * @author jdenise@redhat.com
 */
public class FileCompleter implements OptionCompleter<CLICompleterInvocation> {

    @Override
    public void complete(CLICompleterInvocation completerInvocation) {
        List<String> candidates = new ArrayList<>();
        int pos = 0;
        if (completerInvocation.getGivenCompleteValue() != null) {
            pos = completerInvocation.getGivenCompleteValue().length();
        }
        FilenameTabCompleter pathCompleter
                = FilenameTabCompleter.newCompleter(completerInvocation.getCommandContext());
        int cursor = pathCompleter.complete(completerInvocation.
                getCommandContext(),
                completerInvocation.getGivenCompleteValue(), pos, candidates);
        String input = completerInvocation.getGivenCompleteValue();
        boolean appendSpace = false;
        // Hacky for now.
        if (candidates.size() == 1) {
            if (input.endsWith(candidates.get(0))) {
                appendSpace = true;
            }

        }
        completerInvocation.addAllCompleterValues(candidates);
        completerInvocation.setOffset(completerInvocation.getGivenCompleteValue().length() - cursor);
        completerInvocation.setAppendSpace(appendSpace);
    }

}
