/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
@CommandDefinition(name = "apply", description = "")
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
