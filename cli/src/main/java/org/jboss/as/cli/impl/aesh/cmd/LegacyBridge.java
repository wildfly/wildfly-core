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
package org.jboss.as.cli.impl.aesh.cmd;

import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.jboss.as.cli.CommandContext;

/**
 * Interface exposed to legacy commands by Aesh based command. This interface
 * allows to simplify legacy command refactoring.
 *
 * @author jdenise@redhat.com
 */
@Deprecated
public interface LegacyBridge {
    CommandResult execute(CommandContext ctx) throws CommandException;
}
