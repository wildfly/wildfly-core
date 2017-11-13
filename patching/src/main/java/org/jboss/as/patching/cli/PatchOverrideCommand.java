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
