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
@CommandDefinition(name = "history", description = "", activator = PatchCommand.PatchCommandActivator.class)
public class PatchHistory extends AbstractDistributionCommand {

    @Option(name = "patch-stream", hasValue = true, required = false)
    private String patchStream;

    @Option(name = "exclude-aged-out", hasValue = false, required = false)
    private boolean excludeAgedOut;

    public PatchHistory() {
        super("history");
    }

    @Override
    protected PatchOperationBuilder createPatchOperationBuilder(CommandContext ctx) throws CommandException {
        return PatchOperationBuilder.Factory.history(patchStream, excludeAgedOut);
    }

}
