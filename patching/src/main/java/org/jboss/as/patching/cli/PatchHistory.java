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
@CommandDefinition(name = "history", description = "")
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
