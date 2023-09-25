/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
