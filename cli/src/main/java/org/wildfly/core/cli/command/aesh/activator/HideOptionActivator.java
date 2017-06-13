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

import org.aesh.command.impl.internal.ProcessedCommand;

/**
 *
 * Hides an option otherwise delegates to the provided Activator.
 *
 * @author jdenise@redhat.com
 */
public class HideOptionActivator extends AbstractOptionActivator {

    private static class HideActivator extends AbstractOptionActivator {

        @Override
        public boolean isActivated(ProcessedCommand processedCommand) {
            return false;
        }
    }

    private final CLIOptionActivator activator;

    public HideOptionActivator() {
        this(true, null);
    }

    public HideOptionActivator(boolean hidden, CLIOptionActivator activator) {
        this.activator = hidden ? new HideActivator() : activator;
    }

    @Override
    public boolean isActivated(ProcessedCommand processedCommand) {
        activator.setCommandContext(getCommandContext());
        return activator.isActivated(processedCommand);
    }

}
