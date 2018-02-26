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
package org.jboss.as.cli.impl.aesh.cmd.deployment.security;

import java.util.HashSet;
import java.util.Set;
import org.aesh.command.activator.OptionActivator;
import org.aesh.command.impl.internal.ParsedCommand;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependRejectOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.AbstractRejectOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.DomainOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.DependOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.StandaloneOptionActivator;

/**
 * Deployment related option activators that depend on the command permissions.
 *
 * @author jdenise@redhat.com
 */
public interface OptionActivators {

    class UrlActivator implements OptionActivator {

        @Override
        public boolean isActivated(ParsedCommand pc) {
            CommandWithPermissions cmd = (CommandWithPermissions) pc.command();
            return cmd.getPermissions().getAddOrReplacePermission().
                    isSatisfied(cmd.getCommandContext());
        }
    }

    class NameActivator implements OptionActivator {

        @Override
        public boolean isActivated(ParsedCommand pc) {
            CommandWithPermissions cmd = (CommandWithPermissions) pc.command();
            return cmd.getPermissions().getDeployPermission().
                    isSatisfied(cmd.getCommandContext());
        }

    }

    class UndeployNameActivator implements OptionActivator {

        @Override
        public boolean isActivated(ParsedCommand pc) {
            CommandWithPermissions cmd = (CommandWithPermissions) pc.command();
            return cmd.getPermissions().getRemoveOrUndeployPermission().
                    isSatisfied(cmd.getCommandContext());
        }

    }

    class UndeployArchiveActivator extends UndeployNameActivator {
    }

    class FileActivator implements OptionActivator {

        @Override
        public boolean isActivated(ParsedCommand pc) {
            CommandWithPermissions cmd = (CommandWithPermissions) pc.command();
            return cmd.getPermissions().getAddOrReplacePermission().
                    isSatisfied(cmd.getCommandContext());
        }
    }

    class UnmanagedActivator implements OptionActivator {

        @Override
        public boolean isActivated(ParsedCommand pc) {
            CommandWithPermissions cmd = (CommandWithPermissions) pc.command();
            return cmd.getPermissions().getMainAddPermission().
                    isSatisfied(cmd.getCommandContext());
        }
    }

    class RuntimeNameActivator implements OptionActivator {

        @Override
        public boolean isActivated(ParsedCommand pc) {
            CommandWithPermissions cmd = (CommandWithPermissions) pc.command();
            return cmd.getPermissions().getAddOrReplacePermission().
                    isSatisfied(cmd.getCommandContext());
        }
    }

    class ReplaceActivator extends AbstractDependRejectOptionActivator {

        private static final Set<String> EXPECTED = new HashSet<>();
        private static final Set<String> NOT_EXPECTED = new HashSet<>();
        static {
            // Argument.
            NOT_EXPECTED.add("all-server-groups");
            NOT_EXPECTED.add("server-groups");
        }

        public ReplaceActivator() {
            super(false, EXPECTED, NOT_EXPECTED);
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            CommandWithPermissions cmd = (CommandWithPermissions) processedCommand.command();
            if (!cmd.getPermissions().getFullReplacePermission().
                    isSatisfied(cmd.getCommandContext())) {
                return false;
            }
            return super.isActivated(processedCommand);
        }
    }

    class EnabledActivator extends AbstractDependRejectOptionActivator
            implements StandaloneOptionActivator {

        private static final Set<String> EXPECTED = new HashSet<>();
        private static final Set<String> NOT_EXPECTED = new HashSet<>();

        static {
            // Argument.
            NOT_EXPECTED.add("disabled");
        }

        public EnabledActivator() {
            super(false, EXPECTED, NOT_EXPECTED);
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            CommandWithPermissions cmd = (CommandWithPermissions) processedCommand.command();
            if (!cmd.getPermissions().getMainAddPermission().
                    isSatisfied(cmd.getCommandContext())) {
                return false;
            }
            if (cmd.getCommandContext().isDomainMode()) {
                return false;
            }
            return super.isActivated(processedCommand);
        }
    }

    class DisabledActivator extends AbstractDependRejectOptionActivator
            implements StandaloneOptionActivator {

        private static final Set<String> EXPECTED = new HashSet<>();
        private static final Set<String> NOT_EXPECTED = new HashSet<>();

        static {
            // Argument.
            NOT_EXPECTED.add("enabled");
        }

        public DisabledActivator() {
            super(false, EXPECTED, NOT_EXPECTED);
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            CommandWithPermissions cmd = (CommandWithPermissions) processedCommand.command();
            if (!cmd.getPermissions().getMainAddPermission().
                    isSatisfied(cmd.getCommandContext())) {
                return false;
            }
            if (cmd.getCommandContext().isDomainMode()) {
                return false;
            }
            return super.isActivated(processedCommand);
        }
    }

    class ServerGroupsActivator extends AbstractRejectOptionActivator
            implements DomainOptionActivator {

        public ServerGroupsActivator() {
            super("all-server-groups", "replace");
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            CommandWithPermissions cmd = (CommandWithPermissions) processedCommand.command();
            if (!cmd.getPermissions().getDeployPermission().
                    isSatisfied(cmd.getCommandContext())) {
                return false;
            }
            if (!cmd.getCommandContext().isDomainMode()) {
                return false;
            }
            return super.isActivated(processedCommand);
        }
    }

    class InfoServerGroupActivator extends AbstractRejectOptionActivator
            implements DomainOptionActivator {

        public InfoServerGroupActivator() {
            super(DependOptionActivator.ARGUMENT_NAME);
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            CommandWithPermissions cmd = (CommandWithPermissions) processedCommand.command();
            if (!cmd.getCommandContext().isDomainMode()) {
                return false;
            }
            return super.isActivated(processedCommand);
        }
    }

    class InfoNameActivator extends AbstractRejectOptionActivator {

        public InfoNameActivator() {
            super("server-group");
        }
    }

    class AllServerGroupsActivator extends AbstractRejectOptionActivator
            implements DomainOptionActivator {

        public AllServerGroupsActivator() {
            super("server-groups", "replace");
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            CommandWithPermissions cmd
                    = (CommandWithPermissions) processedCommand.command();
            if (!cmd.getPermissions().getDeployPermission().
                    isSatisfied(cmd.getCommandContext())) {
                return false;
            }
            if (!cmd.getCommandContext().isDomainMode()) {
                return false;
            }
            return super.isActivated(processedCommand);
        }
    }

    class AllRelevantServerGroupsActivator extends AbstractRejectOptionActivator
            implements DomainOptionActivator {

        public AllRelevantServerGroupsActivator() {
            super("server-groups");
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            CommandWithPermissions cmd
                    = (CommandWithPermissions) processedCommand.command();
            if (!cmd.getPermissions().getUndeployPermission().
                    isSatisfied(cmd.getCommandContext())) {
                return false;
            }
            if (!cmd.getCommandContext().isDomainMode()) {
                return false;
            }
            return super.isActivated(processedCommand);
        }
    }

    class UndeployServerGroupsActivator extends AbstractRejectOptionActivator
            implements DomainOptionActivator {

        public UndeployServerGroupsActivator() {
            super("all-relevant-server-groups");
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            CommandWithPermissions cmd = (CommandWithPermissions) processedCommand.command();
            if (!cmd.getPermissions().getUndeployPermission().
                    isSatisfied(cmd.getCommandContext())) {
                return false;
            }
            if (!cmd.getCommandContext().isDomainMode()) {
                return false;
            }
            return super.isActivated(processedCommand);
        }
    }
}
