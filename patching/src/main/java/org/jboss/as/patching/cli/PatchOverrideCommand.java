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
package org.jboss.as.patching.cli;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.patching.tool.PatchOperationBuilder;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "abstract-override-cmd", description = "")
abstract class PatchOverrideCommand extends AbstractDistributionCommand {

    @Option(name = "override-all", hasValue = false, required = false)
    boolean overrideAll;

    @Option(name = "override-modules", hasValue = false, required = false)
    boolean overrideModules;

    @Option(name = "override", hasValue = true, required = false)
    String overrideList;

    @Option(name = "preserve", hasValue = true, required = false)
    String preserveList;

    protected PatchOverrideCommand(String action) {
        super(action);
    }

    protected abstract PatchOperationBuilder createUnconfiguredOperationBuilder(CommandContext ctx) throws CommandException;

    @Override
    protected PatchOperationBuilder createPatchOperationBuilder(CommandContext ctx) throws CommandException {
        PatchOperationBuilder builder = createUnconfiguredOperationBuilder(ctx);
        configureBuilder(builder);
        return builder;
    }

    private void configureBuilder(PatchOperationBuilder builder) throws CommandException {
        if (overrideModules) {
            builder.ignoreModuleChanges();
        }
        if (overrideAll) {
            builder.overrideAll();
        }
        if (overrideList != null) {
            for (String path : overrideList.split(",+")) {
                builder.overrideItem(path);
            }
        }
        if (preserveList != null) {
            for (String path : preserveList.split(",+")) {
                builder.preserveItem(path);
            }
        }
    }


}
