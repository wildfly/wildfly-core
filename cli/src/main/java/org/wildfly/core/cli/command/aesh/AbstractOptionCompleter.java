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
package org.wildfly.core.cli.command.aesh;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.aesh.command.completer.OptionCompleter;
import org.jboss.as.cli.CommandContext;

/**
 *
 * @author jdenise@redhat.com
 */
public abstract class AbstractOptionCompleter implements OptionCompleter<CLICompleterInvocation> {

    @Override
    public void complete(CLICompleterInvocation completerInvocation) {
        List<String> values = new ArrayList<>();
        Collection<String> candidates = getCandidates(completerInvocation.getCommandContext());
        String opBuffer = completerInvocation.getGivenCompleteValue();
        if (opBuffer.isEmpty()) {
            values.addAll(candidates);
        } else {
            for (String name : candidates) {
                if (name.startsWith(opBuffer)) {
                    values.add(name);
                }
            }
            Collections.sort(values);
        }
        completerInvocation.addAllCompleterValues(values);
    }

    public abstract Collection<String> getCandidates(CommandContext ctx);
}
