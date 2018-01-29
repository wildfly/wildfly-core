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

    public static class UrlActivator implements OptionActivator {

        @Override
        public boolean isActivated(ParsedCommand pc) {
            CommandWithPermissions cmd = (CommandWithPermissions) pc.command();
            return cmd.getPermissions().getAddOrReplacePermission().
                    isSatisfied(cmd.getCommandContext());
        }
    }

    public static class NameActivator implements OptionActivator {

        @Override
        public boolean isActivated(ParsedCommand pc) {
            CommandWithPermissions cmd = (CommandWithPermissions) pc.command();
            return cmd.getPermissions().getDeployPermission().
                    isSatisfied(cmd.getCommandContext());
        }

    }

    public static class UndeployNameActivator implements OptionActivator {

        @Override
        public boolean isActivated(ParsedCommand pc) {
            CommandWithPermissions cmd = (CommandWithPermissions) pc.command();
            return cmd.getPermissions().getRemoveOrUndeployPermission().
                    isSatisfied(cmd.getCommandContext());
        }

    }

    public static class UndeployArchiveActivator extends UndeployNameActivator {
    }

    public static class FileActivator implements OptionActivator {

        @Override
        public boolean isActivated(ParsedCommand pc) {
            CommandWithPermissions cmd = (CommandWithPermissions) pc.command();
            return cmd.getPermissions().getAddOrReplacePermission().
                    isSatisfied(cmd.getCommandContext());
        }
    }

    public static class UnmanagedActivator implements OptionActivator {

        @Override
        public boolean isActivated(ParsedCommand pc) {
            CommandWithPermissions cmd = (CommandWithPermissions) pc.command();
            return cmd.getPermissions().getMainAddPermission().
                    isSatisfied(cmd.getCommandContext());
        }
    }

    public static class RuntimeNameActivator implements OptionActivator {

        @Override
        public boolean isActivated(ParsedCommand pc) {
            CommandWithPermissions cmd = (CommandWithPermissions) pc.command();
            return cmd.getPermissions().getAddOrReplacePermission().
                    isSatisfied(cmd.getCommandContext());
        }
    }

    public static class ReplaceActivator extends AbstractDependRejectOptionActivator {

        private static final Set<String> EXPECTED = new HashSet<>();
        private static final Set<String> NOT_EXPECTED = new HashSet<>();
        static {
            // Argument.
            EXPECTED.add(DependOptionActivator.ARGUMENT_NAME);
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

    public static class EnabledActivator extends AbstractDependRejectOptionActivator
            implements StandaloneOptionActivator {

        private static final Set<String> EXPECTED = new HashSet<>();
        private static final Set<String> NOT_EXPECTED = new HashSet<>();

        static {
            // Argument.
            EXPECTED.add(DependOptionActivator.ARGUMENT_NAME);
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
            return super.isActivated(processedCommand);
        }
    }

    public static class DisabledActivator extends AbstractDependRejectOptionActivator
            implements StandaloneOptionActivator {

        private static final Set<String> EXPECTED = new HashSet<>();
        private static final Set<String> NOT_EXPECTED = new HashSet<>();

        static {
            // Argument.
            EXPECTED.add(DependOptionActivator.ARGUMENT_NAME);
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
            return super.isActivated(processedCommand);
        }
    }

    public static class ServerGroupsActivator extends AbstractDependRejectOptionActivator
            implements DomainOptionActivator {

        private static final Set<String> EXPECTED = new HashSet<>();
        private static final Set<String> NOT_EXPECTED = new HashSet<>();

        static {
            // Argument.
            EXPECTED.add(DependOptionActivator.ARGUMENT_NAME);
            NOT_EXPECTED.add("all-server-groups");
            NOT_EXPECTED.add("replace");
        }
        public ServerGroupsActivator() {
            super(false, EXPECTED, NOT_EXPECTED);
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

    public static class InfoServerGroupsActivator extends AbstractRejectOptionActivator
            implements DomainOptionActivator {

        public InfoServerGroupsActivator() {
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

    public static class InfoNameActivator extends AbstractRejectOptionActivator {

        public InfoNameActivator() {
            super("server-group");
        }
    }

    public static class AllServerGroupsActivator extends AbstractDependRejectOptionActivator
            implements DomainOptionActivator {

        private static final Set<String> EXPECTED = new HashSet<>();
        private static final Set<String> NOT_EXPECTED = new HashSet<>();

        static {
            // Argument.
            EXPECTED.add(DependOptionActivator.ARGUMENT_NAME);
            NOT_EXPECTED.add("server-groups");
            NOT_EXPECTED.add("replace");
        }

        public AllServerGroupsActivator() {
            super(false, EXPECTED, NOT_EXPECTED);
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

    public static class AllRelevantServerGroupsActivator extends AbstractDependRejectOptionActivator
            implements DomainOptionActivator {

        public AllRelevantServerGroupsActivator() {
            super(false, EXPECTED, NOT_EXPECTED);
        }

        private static final Set<String> EXPECTED = new HashSet<>();
        private static final Set<String> NOT_EXPECTED = new HashSet<>();

        static {
            // Argument.
            EXPECTED.add(DependOptionActivator.ARGUMENT_NAME);
            NOT_EXPECTED.add("server-groups");
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

    public static class UndeployServerGroupsActivator extends AbstractDependRejectOptionActivator
            implements DomainOptionActivator {

        public UndeployServerGroupsActivator() {
            super(false, EXPECTED, NOT_EXPECTED);
        }

        private static final Set<String> EXPECTED = new HashSet<>();
        private static final Set<String> NOT_EXPECTED = new HashSet<>();

        static {
            // Argument.
            EXPECTED.add(DependOptionActivator.ARGUMENT_NAME);
            NOT_EXPECTED.add("all-relevant-server-groups");
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
