/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.ssl;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.aesh.command.impl.internal.ParsedCommand;
import org.jboss.as.cli.Util;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_ADD_HTTPS_LISTENER;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_INTERACTIVE;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_PATH;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_LETS_ENCRYPT;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_MANAGEMENT_INTERFACE;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_TRUSTED_CERTIFICATE_PATH;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_TRUST_STORE_NAME;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ElytronUtil;
import org.jboss.as.cli.impl.aesh.cmd.security.model.HTTPServer;
import org.jboss.as.cli.operation.OperationFormatException;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependOneOfOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.AbstractDependRejectOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.AbstractRejectOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.DependOneOfOptionActivator;

/**
 * Option activators in use in the SSL commands. Activators are controlling the
 * visibility of options during completion. They are the input of help synopsis
 * generator.
 *
 * @author jdenise@redhat.com
 */
public interface OptionActivators {

    public static class InteractiveActivator extends AbstractRejectOptionActivator {

        public InteractiveActivator() {
            super(OPT_KEY_STORE_NAME, OPT_KEY_STORE_PATH);
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            AbstractEnableSSLCommand cmd = (AbstractEnableSSLCommand) processedCommand.command();
            try {
                if (!ElytronUtil.isKeyStoreManagementSupported(cmd.getCommandContext())) {
                    return false;
                }
            } catch (Exception ex) {
                return false;
            }
            return super.isActivated(processedCommand);
        }
    }

    public static class KeyStorePathActivator extends AbstractRejectOptionActivator {

        public KeyStorePathActivator() {
            super(OPT_KEY_STORE_NAME, OPT_INTERACTIVE);
        }
    }

    public static class KeyStoreNameActivator extends AbstractRejectOptionActivator {

        public KeyStoreNameActivator() {
            super(OPT_KEY_STORE_PATH, OPT_INTERACTIVE);
        }
    }

    public static class TrustStoreNameActivator extends AbstractRejectOptionActivator implements DependOneOfOptionActivator {
        private static class DependsOnOf extends AbstractDependOneOfOptionActivator {

            DependsOnOf() {
                super(OPT_KEY_STORE_NAME, OPT_KEY_STORE_PATH);
            }
        }
        private static DependsOnOf dof = new DependsOnOf();
        public TrustStoreNameActivator() {
            super(OPT_TRUSTED_CERTIFICATE_PATH);
        }

        @Override
        public Set<String> getOneOfDependsOn() {
            return dof.getOneOfDependsOn();
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            if (!dof.isActivated(processedCommand)) {
                return false;
            }
            return super.isActivated(processedCommand);
        }
    }

    public static class TrustStoreFileNameActivator extends AbstractDependRejectOptionActivator {

        private static final Set<String> DEPEND = new HashSet<>();
        private static final Set<String> REJECT = new HashSet<>();

        static {
            DEPEND.add(OPT_TRUSTED_CERTIFICATE_PATH);
            REJECT.add(OPT_TRUST_STORE_NAME);
        }

        public TrustStoreFileNameActivator() {
            super(false, DEPEND, REJECT);
        }
    }

    public static class NewTrustStoreNameActivator extends AbstractDependOptionActivator {

        public NewTrustStoreNameActivator() {
            super(false, OPT_TRUSTED_CERTIFICATE_PATH);
        }
    }

    public static class NewTrustManagerNameActivator extends AbstractDependOneOfOptionActivator {

        public NewTrustManagerNameActivator() {
            super(OPT_TRUSTED_CERTIFICATE_PATH, OPT_TRUST_STORE_NAME);
        }
    }

    public static class TrustStoreFilePasswordActivator extends AbstractDependOptionActivator {

        public TrustStoreFilePasswordActivator() {
            super(false, OPT_TRUSTED_CERTIFICATE_PATH);
        }
    }

    public static class NewKeyStoreNameActivator extends AbstractDependOptionActivator {

        public NewKeyStoreNameActivator() {
            super(false, OPT_KEY_STORE_PATH);
        }
    }

    public static class NewSSLContextNameActivator extends AbstractDependOneOfOptionActivator {

        public NewSSLContextNameActivator() {
            super(OPT_KEY_STORE_PATH, OPT_KEY_STORE_NAME);
        }
    }

    public static class NewKeyManagerNameActivator extends AbstractDependOneOfOptionActivator {

        public NewKeyManagerNameActivator() {
            super(OPT_KEY_STORE_PATH, OPT_KEY_STORE_NAME);
        }
    }

    public static class ManagementInterfaceActivator extends AbstractDependOneOfOptionActivator {

        public ManagementInterfaceActivator() {
            super(OPT_KEY_STORE_PATH, OPT_KEY_STORE_NAME, OPT_INTERACTIVE);
        }
    }

    public static class TrustedCertificateActivator extends AbstractRejectOptionActivator implements DependOneOfOptionActivator {

        private static class DependsOnOf extends AbstractDependOneOfOptionActivator {

            DependsOnOf() {
                super(OPT_KEY_STORE_NAME, OPT_KEY_STORE_PATH);
            }
        }
        private static DependsOnOf dof = new DependsOnOf();

        public TrustedCertificateActivator() {
            super(OPT_TRUST_STORE_NAME);
        }

        @Override
        public Set<String> getOneOfDependsOn() {
            return dof.getOneOfDependsOn();
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            AbstractEnableSSLCommand cmd = (AbstractEnableSSLCommand) processedCommand.command();
            try {
                if (!ElytronUtil.isKeyStoreManagementSupported(cmd.getCommandContext())) {
                    return false;
                }
            } catch (Exception ex) {
                return false;
            }
            if (!dof.isActivated(processedCommand)) {
                return false;
            }
            return super.isActivated(processedCommand);
        }
    }

    public static class ValidateTrustedCertificateActivator extends AbstractDependOptionActivator {

        public ValidateTrustedCertificateActivator() {
            super(false, OPT_TRUSTED_CERTIFICATE_PATH);
        }
    }

    public static class NoReloadActivator extends AbstractDependOneOfOptionActivator {

        public NoReloadActivator() {
            super(OPT_KEY_STORE_PATH, OPT_KEY_STORE_NAME, OPT_INTERACTIVE);
        }
    }

    public static class DependsOnAddHttpsListenerActivator extends AbstractDependOneOfOptionActivator {

        public DependsOnAddHttpsListenerActivator() {
            super(OPT_ADD_HTTPS_LISTENER);
        }
    }

    public static class NoOverrideSecurityRealmActivator extends AbstractDependOneOfOptionActivator {

        public NoOverrideSecurityRealmActivator() {
            super(OPT_KEY_STORE_PATH, OPT_KEY_STORE_NAME);
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            HTTPServerEnableSSLCommand cmd = (HTTPServerEnableSSLCommand) processedCommand.command();
            try {
                if (!HTTPServer.isLegacySecurityRealmSupported(cmd.getCommandContext())) {
                    return false;
                }
            } catch (OperationFormatException | IOException ex) {
                return false;
            }
            return super.isActivated(processedCommand);
        }
    }

    public static class KeyStorePathDependentActivator extends AbstractDependOptionActivator {

        public KeyStorePathDependentActivator() {
            super(false, OPT_KEY_STORE_PATH);
        }
    }

    public static class SecureSocketBindingActivator extends AbstractDependOptionActivator {

        public SecureSocketBindingActivator() {
            super(false, OPT_MANAGEMENT_INTERFACE);
        }

        @Override
        public boolean isActivated(ParsedCommand processedCommand) {
            ManagementEnableSSLCommand cmd = (ManagementEnableSSLCommand) processedCommand.command();
            if (cmd.managementInterface == null) {
                return false;
            }
            if (Util.HTTP_INTERFACE.equals(cmd.managementInterface)) {
                return true;
            }
            return false;
        }
    }

    public static class LetsEncryptActivator extends AbstractDependOptionActivator {

        public LetsEncryptActivator() {
            super(false, OPT_INTERACTIVE);
        }
    }

    public static class CaAccountActivator extends AbstractDependOptionActivator {

        public CaAccountActivator() {
            super(false, OPT_INTERACTIVE, OPT_LETS_ENCRYPT);
        }
    }

}
