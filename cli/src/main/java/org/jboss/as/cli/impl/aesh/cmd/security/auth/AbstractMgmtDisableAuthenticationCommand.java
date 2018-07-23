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
package org.jboss.as.cli.impl.aesh.cmd.security.auth;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_MECHANISM;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthFactorySpec;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "abstract-management-auth-disable", description = "")
public abstract class AbstractMgmtDisableAuthenticationCommand extends AbstractDisableAuthenticationCommand {

    @Option(name = OPT_MECHANISM,
            completer = SecurityCommand.OptionCompleters.MechanismDisableCompleter.class)
    String mechanism;

    public AbstractMgmtDisableAuthenticationCommand(AuthFactorySpec factorySpec) {
        super(factorySpec);
    }

    @Override
    protected String getMechanism() {
        return mechanism;
    }

}
