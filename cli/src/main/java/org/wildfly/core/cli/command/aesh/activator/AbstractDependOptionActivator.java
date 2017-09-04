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
package org.wildfly.core.cli.command.aesh.activator;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.aesh.command.impl.internal.ProcessedOption;

/**
 *
 * Use this activator to make an option available if some options are already
 * present.
 *
 * @author jdenise@redhat.com
 */
public abstract class AbstractDependOptionActivator implements DependOptionActivator {

    private final Set<String> options;
    private final boolean lax;

    protected AbstractDependOptionActivator(boolean lax, String... opts) {
        options = new HashSet<>(Arrays.asList(opts));
        this.lax = lax;
    }

    protected AbstractDependOptionActivator(boolean lax, Set<String> opts) {
        this.lax = lax;
        options = opts;
    }

    @Override
    public boolean isActivated(ProcessedCommand processedCommand) {
        boolean found = true;
        for (String opt : options) {
            if (ARGUMENT_NAME.equals(opt)) {
                found &= processedCommand.getArgument() != null && (lax || processedCommand.getArgument().getValue() != null);
            } else {
                ProcessedOption processedOption = processedCommand.findLongOptionNoActivatorCheck(opt);
                found &= processedOption != null && (lax || processedOption.getValue() != null);
            }
        }
        return found;
    }

    @Override
    public Set<String> getDependsOn() {
        return Collections.unmodifiableSet(options);
    }
}
