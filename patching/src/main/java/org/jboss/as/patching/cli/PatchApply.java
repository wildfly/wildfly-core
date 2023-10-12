/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.patching.cli;

import java.io.File;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.option.Argument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.patching.tool.PatchOperationBuilder;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "apply", description = "", activator = PatchCommand.PatchCommandActivator.class)
public class PatchApply extends PatchOverrideCommand {

    // Argument comes first, aesh behavior.
    @Argument(required = true)
    private File file;

    public PatchApply(String action) {
        super("apply");
    }

    @Override
    protected PatchOperationBuilder createUnconfiguredOperationBuilder(CommandContext ctx) throws CommandException {
        if (file == null) {
            throw new CommandException("A path is required.");
        }

        if (!file.exists()) {
            // i18n is never used for CLI exceptions
            throw new CommandException("Path " + file.getAbsolutePath() + " doesn't exist.");
        }
        if (file.isDirectory()) {
            throw new CommandException(file.getAbsolutePath() + " is a directory.");
        }
        return PatchOperationBuilder.Factory.patch(file);
    }

}
