/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.impl.aesh.commands.deployment.security;

import java.util.HashSet;
import java.util.Set;
import org.aesh.command.activator.OptionActivator;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependRejectOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.DomainOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.DependOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.StandaloneOptionActivator;

/**
 *
 * @author jdenise@redhat.com
 */
public interface Activators {

    public static class UrlActivator implements OptionActivator {

        @Override
        public boolean isActivated(ProcessedCommand pc) {
            CommandWithPermissions cmd = (CommandWithPermissions) pc.getCommand();
            return cmd.getPermissions().getAddOrReplacePermission().
                    isSatisfied(cmd.getCommandContext());
        }
    }

    public static class NameActivator implements OptionActivator {

        @Override
        public boolean isActivated(ProcessedCommand pc) {
            CommandWithPermissions cmd = (CommandWithPermissions) pc.getCommand();
            return cmd.getPermissions().getDeployPermission().
                    isSatisfied(cmd.getCommandContext());
        }

    }

    public static class UndeployNameActivator implements OptionActivator {

        @Override
        public boolean isActivated(ProcessedCommand pc) {
            CommandWithPermissions cmd = (CommandWithPermissions) pc.getCommand();
            return cmd.getPermissions().getRemoveOrUndeployPermission().
                    isSatisfied(cmd.getCommandContext());
        }

    }

    public static class UndeployArchiveActivator extends UndeployNameActivator {
    }

    public static class FileActivator implements OptionActivator {

        @Override
        public boolean isActivated(ProcessedCommand pc) {
            CommandWithPermissions cmd = (CommandWithPermissions) pc.getCommand();
            return cmd.getPermissions().getAddOrReplacePermission().
                    isSatisfied(cmd.getCommandContext());
        }
    }

    public static class UnmanagedActivator implements OptionActivator {

        @Override
        public boolean isActivated(ProcessedCommand pc) {
            CommandWithPermissions cmd = (CommandWithPermissions) pc.getCommand();
            return cmd.getPermissions().getMainAddPermission().
                    isSatisfied(cmd.getCommandContext());
        }
    }

    public static class RuntimeNameActivator implements OptionActivator {

        @Override
        public boolean isActivated(ProcessedCommand pc) {
            CommandWithPermissions cmd = (CommandWithPermissions) pc.getCommand();
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
        public boolean isActivated(ProcessedCommand processedCommand) {
            CommandWithPermissions cmd = (CommandWithPermissions) processedCommand.getCommand();
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
        public boolean isActivated(ProcessedCommand processedCommand) {
            CommandWithPermissions cmd = (CommandWithPermissions) processedCommand.getCommand();
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
        public boolean isActivated(ProcessedCommand processedCommand) {
            CommandWithPermissions cmd = (CommandWithPermissions) processedCommand.getCommand();
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
        public boolean isActivated(ProcessedCommand processedCommand) {
            CommandWithPermissions cmd = (CommandWithPermissions) processedCommand.getCommand();
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
        public boolean isActivated(ProcessedCommand processedCommand) {
            CommandWithPermissions cmd
                    = (CommandWithPermissions) processedCommand.getCommand();
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
        public boolean isActivated(ProcessedCommand processedCommand) {
            CommandWithPermissions cmd
                    = (CommandWithPermissions) processedCommand.getCommand();
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
        public boolean isActivated(ProcessedCommand processedCommand) {
            CommandWithPermissions cmd = (CommandWithPermissions) processedCommand.getCommand();
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
