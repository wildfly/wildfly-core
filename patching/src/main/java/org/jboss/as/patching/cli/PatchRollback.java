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
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.patching.tool.PatchOperationBuilder;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "rollback", description = "", activator = PatchRollbackActivator.class)
public class PatchRollback extends PatchOverrideCommand {

    @Option(name = "reset-configuration", hasValue = true, required = true)
    private boolean resetConfiguration;

    @Option(name = "rollback-to", hasValue = false, required = false)
    private boolean rollbackTo;

    @Option(name = "patch-stream", hasValue = true, required = false)
    private String patchStream;

    @Option(name = "patch-id", required = false, activator = HideOptionActivator.class)
    private String patchId;

    @Argument(completer = PatchIdCompleter.class)
    private String patchIdArg;

    public PatchRollback(String action) {
        super("rollback");
    }

    private String getPatchId() throws CommandException {
        if (patchId != null && patchIdArg != null) {
            throw new CommandException("patch-id argument and options can't be set al together.");
        }
        if (patchId != null) {
            return patchId;
        }
        if (patchIdArg != null) {
            return patchIdArg;
        }
        return null;
    }


    @Override
    protected PatchOperationBuilder createUnconfiguredOperationBuilder(CommandContext ctx) throws CommandException {
        PatchOperationBuilder builder;
        String patchId = getPatchId();
        if (patchId != null) {
            builder = PatchOperationBuilder.Factory.rollback(patchStream, patchId, rollbackTo, resetConfiguration);
        } else {
            builder = PatchOperationBuilder.Factory.rollbackLast(patchStream, resetConfiguration);
        }
        return builder;
    }

    @Override
    String getPatchStream() {
        return patchStream;
    }

}
