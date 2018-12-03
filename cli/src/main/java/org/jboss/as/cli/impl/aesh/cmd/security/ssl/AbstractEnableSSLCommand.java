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
package org.jboss.as.cli.impl.aesh.cmd.security.ssl;

import org.jboss.as.cli.impl.aesh.cmd.RelativeFile;
import org.jboss.as.cli.impl.aesh.cmd.RelativeFilePathConverter;
import java.io.File;
import java.io.IOException;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.impl.completer.FileOptionCompleter;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_CA_ACCOUNT;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_INTERACTIVE;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_PASSWORD;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_PATH;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_PATH_RELATIVE_TO;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_TYPE;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_LETS_ENCRYPT;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NEW_KEY_MANAGER_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NEW_KEY_STORE_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NEW_SSL_CONTEXT_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NEW_TRUST_MANAGER_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NEW_TRUST_STORE_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NO_RELOAD;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NO_TRUSTED_CERTIFICATE_VALIDATION;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_TRUSTED_CERTIFICATE_PATH;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_TRUST_STORE_FILE_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_TRUST_STORE_FILE_PASSWORD;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_TRUST_STORE_NAME;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OptionCompleters;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.formatOption;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ElytronUtil;
import org.jboss.as.cli.impl.aesh.cmd.security.model.InteractiveSecurityBuilder;
import org.jboss.as.cli.impl.aesh.cmd.security.model.KeyStoreNameSecurityBuilder;
import org.jboss.as.cli.impl.aesh.cmd.security.model.KeyStorePathSecurityBuilder;
import org.jboss.as.cli.impl.aesh.cmd.security.model.SSLSecurityBuilder;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.DMRCommand;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 * Base class for any command that enables SSL.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "abstract-ssl-enable", description = "")
public abstract class AbstractEnableSSLCommand implements Command<CLICommandInvocation>, DMRCommand {

    @Option(name = OPT_KEY_STORE_NAME, completer = OptionCompleters.KeyStoreNameCompleter.class,
            activator = OptionActivators.KeyStoreNameActivator.class)
    String keystoreName;

    @Option(name = OPT_KEY_STORE_PATH, activator = OptionActivators.KeyStorePathActivator.class,
            converter = RelativeFilePathConverter.class, completer = FileOptionCompleter.class)
    RelativeFile keystorePath;

    @Option(name = OPT_KEY_STORE_PATH_RELATIVE_TO, activator = OptionActivators.KeyStorePathDependentActivator.class)
    String keystorePathRelativeTo;

    @Option(name = OPT_KEY_STORE_PASSWORD, activator = OptionActivators.KeyStorePathDependentActivator.class)
    String keystorePassword;

    @Option(name = OPT_TRUSTED_CERTIFICATE_PATH, activator = OptionActivators.TrustedCertificateActivator.class)
    File trustedCertificatePath;

    @Option(name = OPT_NO_TRUSTED_CERTIFICATE_VALIDATION, activator = OptionActivators.ValidateTrustedCertificateActivator.class, hasValue = false)
    boolean noTrustedCertificateValidation;

    @Option(name = OPT_TRUST_STORE_NAME, completer = OptionCompleters.KeyStoreNameCompleter.class,
            activator = OptionActivators.TrustStoreNameActivator.class)
    String trustStoreName;

    @Option(name = OPT_TRUST_STORE_FILE_NAME, activator = OptionActivators.TrustStoreFileNameActivator.class)
    String trustStoreFileName;

    @Option(name = OPT_NEW_TRUST_STORE_NAME, activator = OptionActivators.NewTrustStoreNameActivator.class)
    String newTrustStoreName;

    @Option(name = OPT_NEW_TRUST_MANAGER_NAME, activator = OptionActivators.NewTrustManagerNameActivator.class)
    String newTrustManagerName;

    @Option(name = OPT_TRUST_STORE_FILE_PASSWORD, activator = OptionActivators.TrustStoreFilePasswordActivator.class)
    String trustStoreFilePassword;

    @Option(name = OPT_KEY_STORE_TYPE, activator = OptionActivators.KeyStorePathDependentActivator.class,
            completer = OptionCompleters.KeyStoreTypeCompleter.class)
    String keyStoreType;

    @Option(name = OPT_NEW_KEY_MANAGER_NAME, activator = OptionActivators.NewKeyManagerNameActivator.class)
    String newKeyManagerName;

    @Option(name = OPT_NEW_SSL_CONTEXT_NAME, activator = OptionActivators.NewSSLContextNameActivator.class)
    String newSslContextName;

    @Option(name = OPT_NEW_KEY_STORE_NAME,
            activator = OptionActivators.NewKeyStoreNameActivator.class)
    String newKeystoreName;

    @Option(name = OPT_NO_RELOAD, hasValue = false, activator = OptionActivators.NoReloadActivator.class)
    boolean noReload;

    @Option(name = OPT_INTERACTIVE, hasValue = false, activator = OptionActivators.InteractiveActivator.class)
    boolean interactive;

    @Option(name = OPT_LETS_ENCRYPT, hasValue = false, activator = OptionActivators.LetsEncryptActivator.class)
    boolean useLetsEncrypt;

    @Option(name = OPT_CA_ACCOUNT,  completer = OptionCompleters.CaAccountNameCompleter.class,
            activator = OptionActivators.CaAccountActivator.class)
    String caAccount;

    protected abstract void secure(CommandContext ctx, SSLSecurityBuilder ssl) throws CommandException;

    private final CommandContext initCtx;

    protected AbstractEnableSSLCommand(CommandContext initCtx) {
        this.initCtx = initCtx;
    }

    CommandContext getCommandContext() {
        return initCtx;
    }

    @Override
    public ModelNode buildRequest(CommandContext context) throws CommandFormatException {
        try {
            return buildSecurityRequest(context, null).buildExecutableRequest(context);
        } catch (Exception ex) {
            throw new CommandFormatException(ex.getLocalizedMessage() == null
                    ? ex.toString() : ex.getLocalizedMessage());
        }
    }

    private SSLSecurityBuilder buildSecurityRequest(CommandContext context, CLICommandInvocation commandInvocation) throws Exception {
        SSLSecurityBuilder builder = validateOptions(context);
        if (builder instanceof InteractiveSecurityBuilder) {
            ((InteractiveSecurityBuilder) builder).setCommandInvocation(commandInvocation);
        }

        builder.buildRequest(context, commandInvocation == null);
        secure(context, builder);
        return builder;
    }

    protected abstract boolean isSSLEnabled(CommandContext ctx) throws Exception;

    protected abstract String getTarget(CommandContext ctx);

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        CommandContext ctx = commandInvocation.getCommandContext();
        String target = getTarget(ctx);
        try {
            if (isSSLEnabled(ctx)) {
                throw new CommandException("SSL is already enabled for " + target);
            }
        } catch (Exception ex) {
            throw new CommandException(ex.getLocalizedMessage(), ex);
        }

        SSLSecurityBuilder builder;
        try {
            builder = buildSecurityRequest(ctx, commandInvocation);
        } catch (Exception ex) {
            throw new CommandException(ex.getLocalizedMessage());
        }
        try {
            SecurityCommand.execute(ctx, builder.buildExecutableRequest(ctx), builder, noReload);
        } catch (Exception ex) {
            if (ex instanceof CommandException) {
                throw (CommandException) ex;
            } else {
                throw new CommandException(ex.getLocalizedMessage());
            }
        }
        commandInvocation.getCommandContext().printLine("SSL enabled for " + target);
        commandInvocation.getCommandContext().printLine("ssl-context is " + builder.getServerSSLContext().getName());
        commandInvocation.getCommandContext().printLine("key-manager is " + builder.getServerSSLContext().getKeyManager().getName());
        commandInvocation.getCommandContext().printLine("key-store   is " + builder.getServerSSLContext().getKeyManager().getKeyStore().getName());

        return CommandResult.SUCCESS;
    }

    abstract String getDefaultKeyStoreFileName(CommandContext ctx);

    abstract String getDefaultTrustStoreFileName(CommandContext ctx);

    private SSLSecurityBuilder validateOptions(CommandContext ctx) throws CommandException, IOException, OperationFormatException {
        if (keystoreName == null && keystorePath == null && !interactive) {
            throw new CommandException("One of " + formatOption(OPT_INTERACTIVE)
                    + ", " + formatOption(OPT_KEY_STORE_NAME) + ", " + formatOption(OPT_KEY_STORE_PATH) + " must be set");
        }

        SSLSecurityBuilder builder = null;

        if (keystorePath != null) {
            if (keystoreName != null) {
                throw new CommandException(formatOption(OPT_KEY_STORE_NAME) + " can't be used with " + formatOption(OPT_KEY_STORE_PATH));
            }
            File path;
            if (keystorePathRelativeTo != null) {
                path = new File(keystorePath.getOriginalPath());
            } else {
                path = keystorePath;
                if (!path.exists()) {
                    throw new CommandException("File " + path + " doesn't exist.");
                }
            }
            KeyStorePathSecurityBuilder kspBuilder = new KeyStorePathSecurityBuilder(path, keystorePassword);
            kspBuilder.setRelativeTo(keystorePathRelativeTo).setType(keyStoreType).
                    setName(newKeystoreName);
            builder = kspBuilder;
        }

        if (keystoreName != null) {
            if (builder != null) {
                invalidUseCase();
            }
            if (newKeystoreName != null || keystorePassword != null || keyStoreType != null || keystorePathRelativeTo != null || keystorePath != null) {
                throw new CommandException("key-store file related options can't be used with " + formatOption(OPT_KEY_STORE_NAME));
            }
            if (!ElytronUtil.keyStoreExists(ctx, keystoreName)) {
                throw new CommandException("key-store " + keystoreName + " doesn't exist");
            }
            builder = new KeyStoreNameSecurityBuilder(keystoreName);
        }

        if (interactive) { // Fully handled by prompting.
            if (builder != null) {
                invalidUseCase();
            }
            checkKeyStoreOperationsSupported(ctx, OPT_INTERACTIVE);
            builder = new InteractiveSecurityBuilder(getDefaultKeyStoreFileName(ctx),
                    getDefaultTrustStoreFileName(ctx),
                    useLetsEncrypt,
                    caAccount);
        }

        if (trustedCertificatePath != null) {
            checkKeyStoreOperationsSupported(ctx, OPT_TRUSTED_CERTIFICATE_PATH);
            if (!trustedCertificatePath.exists()) {
                throw new CommandException("The client certificate path " + trustedCertificatePath + " doesn't exist");
            }
            if (trustStoreName != null) {
                throw new CommandException(formatOption(OPT_TRUST_STORE_NAME) + " can't be used when " + formatOption(OPT_TRUSTED_CERTIFICATE_PATH) + " is in use");
            }
        }

        if (trustStoreName != null) {
            if (!ElytronUtil.keyStoreExists(ctx, trustStoreName)) {
                throw new CommandException("key-store " + trustStoreName + " doesn't exist");
            }
        }

        if (builder != null) {
            builder.setTrustedCertificatePath(trustedCertificatePath);
            builder.setValidateCertificate(!noTrustedCertificateValidation);
            builder.setTrustStoreFileName(trustStoreFileName);
            builder.setTrustStoreFilePassword(trustStoreFilePassword);
            builder.setTrustStoreName(trustStoreName);
            builder.setNewTrustStoreName(newTrustStoreName);
            builder.setNewTrustManagerName(newTrustManagerName);
            builder.setKeyManagerName(newKeyManagerName);
            builder.setSSLContextName(newSslContextName);
        }

        return builder;
    }

    private static void checkKeyStoreOperationsSupported(CommandContext ctx, String option)
            throws IOException, OperationFormatException, CommandException {
        if (!ElytronUtil.isKeyStoreManagementSupported(ctx)) {
            throw new CommandException("Operations to manage key-store are not available, the option " + formatOption(option) + " can't be used");
        }
    }

    private static void invalidUseCase() throws CommandException {
        throw new CommandException("Only one of " + formatOption(OPT_INTERACTIVE)
                + ", " + formatOption(OPT_KEY_STORE_NAME) + ", " + formatOption(OPT_KEY_STORE_PATH) + "must  be set");
    }
}
