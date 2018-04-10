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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.aesh.command.activator.OptionActivator;
import org.aesh.command.impl.internal.ParsedCommand;
import org.aesh.command.impl.internal.ParsedOption;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependOptionActivator;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_MECHANISM;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_USER_PROPERTIES_FILE;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ElytronUtil;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_FILE_SYSTEM_REALM_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_REALM_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_PROPERTIES_REALM_NAME;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependRejectOptionActivator;

/**
 * Option activators used by all commands.
 *
 * @author jdenise@redhat.com
 */
public class OptionActivators {

    public static class MechanismWithRealmActivator extends AbstractDependOptionActivator {

        public MechanismWithRealmActivator() {
            super(false, OPT_MECHANISM);
        }
        @Override
        public boolean isActivated(ParsedCommand parsedCommand) {
            if (!super.isActivated(parsedCommand)) {
                return false;
            }
            ParsedOption opt = parsedCommand.findLongOptionNoActivatorCheck(OPT_MECHANISM);
            return ElytronUtil.getMechanismsWithRealm().contains(opt.value());
        }

    }

    public static class NewSecurityRealmActivator extends AbstractDependOptionActivator {

        public NewSecurityRealmActivator() {
            super(false, OPT_MECHANISM);
        }
        @Override
        public boolean isActivated(ParsedCommand parsedCommand) {
            if (!super.isActivated(parsedCommand)) {
                return false;
            }
            ParsedOption mechOption = parsedCommand.findLongOptionNoActivatorCheck(OPT_MECHANISM);
            if (ElytronUtil.getMechanismsLocalUser().contains(mechOption.value())) {
                return false;
            }
            // Provide a name fpr realm tha twe are going to generate.
            ParsedOption optProps = parsedCommand.findLongOptionNoActivatorCheck(OPT_USER_PROPERTIES_FILE);
            if (optProps != null && optProps.value() != null) {
                return true;
            }
            ParsedOption extCert = parsedCommand.findLongOptionNoActivatorCheck(OPT_KEY_STORE_NAME);
            if (extCert != null && extCert.value() != null) {
                return true;
            }
            return false;
        }

    }

    public static class FilesystemRealmActivator extends AbstractDependRejectOptionActivator {

        private static final Set<String> EXPECTED = new HashSet<>();
        private static final Set<String> REJECTED = new HashSet<>();

        static {
            REJECTED.addAll(Arrays.asList(OPT_USER_PROPERTIES_FILE, OPT_PROPERTIES_REALM_NAME, OPT_KEY_STORE_NAME, OPT_KEY_STORE_REALM_NAME));
            EXPECTED.add(OPT_MECHANISM);
        }

        public FilesystemRealmActivator() {
            super(false, EXPECTED, REJECTED);
        }

        @Override
        public boolean isActivated(ParsedCommand parsedCommand) {
            if (!super.isActivated(parsedCommand)) {
                return false;
            }
            ParsedOption opt = parsedCommand.findLongOptionNoActivatorCheck(OPT_MECHANISM);
            return ElytronUtil.getMechanismsWithRealm().contains(opt.value());
        }

    }

    public static class PropertiesRealmActivator extends AbstractDependRejectOptionActivator {

        private static final Set<String> EXPECTED = new HashSet<>();
        private static final Set<String> REJECTED = new HashSet<>();

        static {
            REJECTED.addAll(Arrays.asList(OPT_USER_PROPERTIES_FILE, OPT_FILE_SYSTEM_REALM_NAME, OPT_KEY_STORE_NAME, OPT_KEY_STORE_REALM_NAME));
            EXPECTED.add(OPT_MECHANISM);
        }
        public PropertiesRealmActivator() {
            super(false, EXPECTED, REJECTED);
        }

        @Override
        public boolean isActivated(ParsedCommand parsedCommand) {
            if (!super.isActivated(parsedCommand)) {
                return false;
            }
            ParsedOption opt = parsedCommand.findLongOptionNoActivatorCheck(OPT_MECHANISM);
            return ElytronUtil.getMechanismsWithRealm().contains(opt.value());
        }

    }

    public static class PropertiesFileRealmActivator extends AbstractDependRejectOptionActivator {

        private static final Set<String> EXPECTED = new HashSet<>();
        private static final Set<String> REJECTED = new HashSet<>();

        static {
            REJECTED.addAll(Arrays.asList(OPT_FILE_SYSTEM_REALM_NAME, OPT_PROPERTIES_REALM_NAME, OPT_KEY_STORE_NAME, OPT_KEY_STORE_REALM_NAME));
            EXPECTED.add(OPT_MECHANISM);
        }
        public PropertiesFileRealmActivator() {
            super(false, EXPECTED, REJECTED);
        }

        @Override
        public boolean isActivated(ParsedCommand parsedCommand) {
            if (!super.isActivated(parsedCommand)) {
                return false;
            }
            ParsedOption opt = parsedCommand.findLongOptionNoActivatorCheck(OPT_MECHANISM);
            return ElytronUtil.getMechanismsWithRealm().contains(opt.value());
        }

    }

    public static class KeyStoreRealmActivator extends AbstractDependRejectOptionActivator {

        private static final Set<String> EXPECTED = new HashSet<>();
        private static final Set<String> REJECTED = new HashSet<>();

        static {
            REJECTED.addAll(Arrays.asList(OPT_KEY_STORE_NAME, OPT_FILE_SYSTEM_REALM_NAME, OPT_PROPERTIES_REALM_NAME, OPT_USER_PROPERTIES_FILE));
            EXPECTED.add(OPT_MECHANISM);
        }
        public KeyStoreRealmActivator() {
            super(false, EXPECTED, REJECTED);
        }

        @Override
        public boolean isActivated(ParsedCommand parsedCommand) {
            if (!super.isActivated(parsedCommand)) {
                return false;
            }
            ParsedOption opt = parsedCommand.findLongOptionNoActivatorCheck(OPT_MECHANISM);
            return ElytronUtil.getMechanismsWithTrustStore().contains(opt.value());
        }

    }

    public static class KeyStoreActivator extends AbstractDependRejectOptionActivator {

        private static final Set<String> EXPECTED = new HashSet<>();
        private static final Set<String> REJECTED = new HashSet<>();

        static {
            REJECTED.addAll(Arrays.asList(OPT_KEY_STORE_REALM_NAME, OPT_FILE_SYSTEM_REALM_NAME, OPT_PROPERTIES_REALM_NAME, OPT_USER_PROPERTIES_FILE));
            EXPECTED.add(OPT_MECHANISM);
        }
        public KeyStoreActivator() {
            super(false, EXPECTED, REJECTED);
        }
        @Override
        public boolean isActivated(ParsedCommand parsedCommand) {
            if (!super.isActivated(parsedCommand)) {
                return false;
            }
            ParsedOption opt = parsedCommand.findLongOptionNoActivatorCheck(OPT_MECHANISM);
            return ElytronUtil.getMechanismsWithTrustStore().contains(opt.value());
        }

    }

    public static class RolesActivator extends AbstractDependOptionActivator {

        public RolesActivator() {
            super(false, OPT_MECHANISM);
        }

        @Override
        public boolean isActivated(ParsedCommand parsedCommand) {
            if (!super.isActivated(parsedCommand)) {
                return false;
            }
            ParsedOption opt = parsedCommand.findLongOptionNoActivatorCheck(OPT_MECHANISM);
            return ElytronUtil.getMechanismsWithTrustStore().contains(opt.value());
        }

    }

    public static class DependsOnMechanism extends AbstractDependOptionActivator {

        public DependsOnMechanism() {
            super(false, OPT_MECHANISM);
        }
    }

    public static class SuperUserActivator implements OptionActivator {

        @Override
        public boolean isActivated(ParsedCommand parsedCommand) {
            ParsedOption opt = parsedCommand.findLongOptionNoActivatorCheck(OPT_MECHANISM);
            return opt != null && opt.value() != null && ElytronUtil.getMechanismsLocalUser().contains(opt.value());
        }
    }

    public static class GroupPropertiesFileActivator extends AbstractDependOptionActivator {

        public GroupPropertiesFileActivator() {
            super(false, OPT_USER_PROPERTIES_FILE);
        }
    }

    public static class RelativeToActivator extends AbstractDependOptionActivator {

        public RelativeToActivator() {
            super(false, OPT_USER_PROPERTIES_FILE);
        }
    }

    public static class FileSystemRoleDecoderActivator extends AbstractDependOptionActivator {

        public FileSystemRoleDecoderActivator() {
            super(false, OPT_FILE_SYSTEM_REALM_NAME);
        }
    }
}
