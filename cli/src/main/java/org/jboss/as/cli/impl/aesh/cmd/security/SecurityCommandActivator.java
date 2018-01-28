/*
Copyright 2018 Red Hat, Inc.

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
package org.jboss.as.cli.impl.aesh.cmd.security;

import org.aesh.command.impl.internal.ParsedCommand;
import org.jboss.as.cli.impl.aesh.cmd.ConnectedActivator;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ElytronUtil;

/**
 * Activator to control security related commands visibility and executability.
 *
 * @author jdenise@redhat.com
 */
public class SecurityCommandActivator extends ConnectedActivator {
    @Override
    public boolean isActivated(ParsedCommand command) {
        if (!super.isActivated(command)) {
            return false;
        }
        if (getCommandContext().isDomainMode()) {
            return false;
        }
        try {
            return ElytronUtil.isElytronSupported(getCommandContext());
        } catch (Exception ex) {
            return false;
        }
    }
}
