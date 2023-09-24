/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.auth;

import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_EXPOSED_REALM;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_FILE_SYSTEM_REALM_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_GROUP_PROPERTIES_FILE;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_REALM_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NEW_AUTH_FACTORY_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NEW_SECURITY_DOMAIN_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NEW_SECURITY_REALM_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NO_RELOAD;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_PLAIN_TEXT;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_PROPERTIES_REALM_NAME;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_RELATIVE_TO;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_ROLES;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_SUPER_USER;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_USER_PROPERTIES_FILE;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_USER_ROLE_DECODER;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.formatOption;

import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthFactorySpec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.impl.completer.FileOptionCompleter;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand;
import org.jboss.as.cli.impl.aesh.cmd.security.model.FileSystemRealmConfiguration;
import org.jboss.as.cli.impl.aesh.cmd.security.model.LocalUserConfiguration;
import org.jboss.as.cli.impl.aesh.cmd.security.model.MechanismConfiguration;
import org.jboss.as.cli.impl.aesh.cmd.security.model.PropertiesRealmConfiguration;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthSecurityBuilder;
import org.jboss.as.cli.impl.aesh.cmd.RelativeFile;
import org.jboss.as.cli.impl.aesh.cmd.RelativeFilePathConverter;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthFactory;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthMechanism;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ElytronUtil;
import org.jboss.as.cli.impl.aesh.cmd.security.model.EmptyConfiguration;
import org.jboss.as.cli.impl.aesh.cmd.security.model.KeyStoreConfiguration;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.DMRCommand;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ExistingPropertiesRealmConfiguration;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ExistingKeyStoreConfiguration;

/**
 * Base class for any command that enables Auth.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "abstract-auth-enable", description = "")
public abstract class AbstractEnableAuthenticationCommand implements Command<CLICommandInvocation>, DMRCommand {

    @Option(name = OPT_FILE_SYSTEM_REALM_NAME, activator = OptionActivators.FilesystemRealmActivator.class,
            completer = SecurityCommand.OptionCompleters.FileSystemRealmCompleter.class)
    String fileSystemRealmName;

    @Option(name = OPT_PROPERTIES_REALM_NAME, activator = OptionActivators.PropertiesRealmActivator.class,
            completer = SecurityCommand.OptionCompleters.PropertiesRealmCompleter.class)
    String propertiesRealmName;

    @Option(name = OPT_USER_ROLE_DECODER, activator = OptionActivators.FileSystemRoleDecoderActivator.class,
            completer = SecurityCommand.OptionCompleters.SimpleDecoderCompleter.class)
    String userRoleDecoder;

    @Option(name = OPT_USER_PROPERTIES_FILE, activator = OptionActivators.PropertiesFileRealmActivator.class,
            converter = RelativeFilePathConverter.class, completer = FileOptionCompleter.class)
    RelativeFile userPropertiesFile;

    @Option(name = OPT_GROUP_PROPERTIES_FILE, activator = OptionActivators.GroupPropertiesFileActivator.class,
            converter = RelativeFilePathConverter.class, completer = FileOptionCompleter.class)
    RelativeFile groupPropertiesFile;

    @Option(name = OPT_EXPOSED_REALM, activator = OptionActivators.MechanismWithRealmActivator.class)
    String exposedRealm;

    @Option(name = OPT_RELATIVE_TO, activator = OptionActivators.RelativeToActivator.class)
    String relativeTo;

    @Option(name = OPT_PLAIN_TEXT, hasValue = false, activator = OptionActivators.PlainTextActivator.class)
    boolean plaintext;

    @Option(name = OPT_NO_RELOAD, hasValue = false)
    boolean noReload;

    @Option(name = OPT_SUPER_USER, hasValue = false, activator = OptionActivators.SuperUserActivator.class)
    boolean superUser;

    @Option(name = OPT_NEW_SECURITY_DOMAIN_NAME, activator = OptionActivators.DependsOnMechanism.class)
    String newSecurityDomain;

    @Option(name = OPT_NEW_AUTH_FACTORY_NAME, activator = OptionActivators.DependsOnMechanism.class)
    String newAuthFactoryName;

    @Option(name = OPT_NEW_SECURITY_REALM_NAME, activator = OptionActivators.NewSecurityRealmActivator.class)
    String newRealmName;

    @Option(name = OPT_KEY_STORE_NAME, activator = OptionActivators.KeyStoreActivator.class,
            completer = SecurityCommand.OptionCompleters.KeyStoreNameCompleter.class)
    String keyStoreName;

    @Option(name = OPT_KEY_STORE_REALM_NAME, activator = OptionActivators.KeyStoreRealmActivator.class,
            completer = SecurityCommand.OptionCompleters.KeyStoreRealmCompleter.class)
    String keyStoreRealmName;

    @Option(name = OPT_ROLES, activator = OptionActivators.RolesActivator.class)
    String roles;

    private final AuthFactorySpec factorySpec;

    protected AbstractEnableAuthenticationCommand(AuthFactorySpec factorySpec) {
        this.factorySpec = factorySpec;
    }

    public AuthFactorySpec getFactorySpec() {
        return factorySpec;
    }

    protected abstract String getMechanism();

    protected abstract void secure(CommandContext ctx, AuthSecurityBuilder builder) throws Exception;

    protected abstract String getOOTBFactory(CommandContext ctx) throws Exception;

    protected abstract String getSecuredEndpoint(CommandContext ctx);

    protected abstract String getEnabledFactory(CommandContext ctx) throws Exception;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        CommandContext ctx = commandInvocation.getCommandContext();
        AuthSecurityBuilder builder;
        try {
            builder = buildSecurityRequest(ctx);
        } catch (Exception ex) {
            throw new CommandException(ex.getLocalizedMessage());
        }
        if (!builder.isEmpty()) {
            SecurityCommand.execute(ctx, builder.getRequest(), SecurityCommand.DEFAULT_FAILURE_CONSUMER, !shouldReload());
            commandInvocation.getCommandContext().printLine("Command success.");
            commandInvocation.getCommandContext().printLine("Authentication configured for "
                    + getSecuredEndpoint(commandInvocation.getCommandContext()));
            if (builder.getReferencedSecurityDomain() != null) {
                commandInvocation.getCommandContext().printLine("security domain=" + builder.getReferencedSecurityDomain());
            } else {
                commandInvocation.getCommandContext().printLine(factorySpec.getName()
                        + " authentication-factory=" + builder.getAuthFactory().getName());
                commandInvocation.getCommandContext().printLine("security-domain="
                        + builder.getAuthFactory().getSecurityDomain().getName());
            }
        } else {
            commandInvocation.getCommandContext().
                    printLine("Authentication is already enabled for " + getSecuredEndpoint(commandInvocation.getCommandContext()));
        }
        return CommandResult.SUCCESS;
    }

    @Override
    public ModelNode buildRequest(CommandContext context) throws CommandFormatException {
        try {
            return buildSecurityRequest(context).getRequest();
        } catch (Exception ex) {
            throw new CommandFormatException(ex.getLocalizedMessage() == null
                    ? ex.toString() : ex.getLocalizedMessage());
        }
    }

    protected AuthSecurityBuilder buildSecurityRequest(CommandContext context) throws Exception {
        AuthSecurityBuilder builder = buildSecurityBuilder(context);
        //OOTB
        if (builder == null) {
            String factoryName = getOOTBFactory(context);
            AuthFactory factory = ElytronUtil.getAuthFactory(factoryName, getFactorySpec(), context);
            if (factory == null) {
                throw new Exception("Can't enable " + factorySpec.getName() + " authentication, "
                        + factoryName + " doesn't exist");
            }
            builder = new AuthSecurityBuilder(factory);
        }
        builder.buildRequest(context);
        if (!builder.isFactoryAlreadySet()) {
            secure(context, builder);
        }
        return builder;
    }

    private AuthSecurityBuilder buildSecurityBuilder(CommandContext context) throws Exception {
        AuthMechanism mec = buildAuthMechanism(context);
        if (mec != null) {
            return buildSecurityBuilder(context, mec);
        }
        return null;
    }

    private AuthSecurityBuilder buildSecurityBuilder(CommandContext ctx, AuthMechanism mec) throws Exception {
        String existingFactory = getEnabledFactory(ctx);
        AuthSecurityBuilder builder = new AuthSecurityBuilder(mec, getFactorySpec());
        builder.setActiveFactoryName(existingFactory);
        configureBuilder(builder);
        return builder;
    }

    protected MechanismConfiguration buildLocalUserConfiguration(CommandContext ctx,
            boolean superUser) throws CommandException, IOException, OperationFormatException {
        if (!ElytronUtil.localUserExists(ctx)) {
            throw new CommandException("Can't configure 'local' user, no such identity.");
        }
        return new LocalUserConfiguration(superUser);
    }

    public static void throwInvalidOptions() throws CommandException {
        throw new CommandException("You must only set a single mechanism.");
    }

    protected static MechanismConfiguration buildExternalConfiguration(CommandContext ctx,
            String keyStore, String keyStoreRealmName, String roles) throws CommandException, IOException, OperationFormatException {
        if (keyStore == null && keyStoreRealmName == null) {
            throw new CommandException("A key-store name or key-store realm name must be set");
        }
        if (keyStore != null && keyStoreRealmName != null) {
            throw new CommandException("Only one of a key-store name or key-store realm name must be set");
        }

        List<String> parsedRoles = null;
        if (roles != null) {
            String[] lst = roles.split(",");
            parsedRoles = new ArrayList<>();
            for (String r : lst) {
                parsedRoles.add(r.trim());
            }
        }
        if (keyStore != null) {
            if (!ElytronUtil.keyStoreExists(ctx, keyStore)) {
                throw new CommandException("Can't configure 'certificate' authentication, no key-store " + keyStore);
            }
            return new KeyStoreConfiguration(keyStore, parsedRoles);
        } else {
            return new ExistingKeyStoreConfiguration(keyStoreRealmName, parsedRoles);
        }
    }

    protected static MechanismConfiguration buildUserPasswordConfiguration(RelativeFile userPropertiesFile,
            String fileSystemRealm, String userRoleDecoder, String exposedRealmName, RelativeFile groupPropertiesFile, String propertiesRealmName,
            String relativeTo, boolean plaintext) throws CommandException, IOException {
        if (userPropertiesFile == null && fileSystemRealm == null && propertiesRealmName == null) {
            throw new CommandException("A properties file or propertie-realm name or a filesystem-realm name must be provided");
        }
        int num = 0;
        if (userPropertiesFile != null) {
            num += 1;
        }
        if (propertiesRealmName != null) {
            num += 1;
        }
        if (fileSystemRealm != null) {
            num += 1;
        }
        if (num > 1) {
            throw new CommandException("Only one of properties file, propertie-realm name or filesystem-realm name must be provided");
        }
        if (userPropertiesFile != null) {
            if (exposedRealmName == null) {
                throw new CommandException(formatOption(OPT_EXPOSED_REALM) + " must be set when using a user properties file");
            }
            PropertiesRealmConfiguration config = new PropertiesRealmConfiguration(exposedRealmName,
                    userPropertiesFile,
                    groupPropertiesFile,
                    relativeTo,
                    plaintext);
            return config;
        } else if (propertiesRealmName != null) {
            if (exposedRealmName == null) {
                throw new CommandException(formatOption(OPT_EXPOSED_REALM) + " must be set when using a properties file realm");
            }
            return new ExistingPropertiesRealmConfiguration(propertiesRealmName, exposedRealmName);
        } else {
            FileSystemRealmConfiguration config = new FileSystemRealmConfiguration(exposedRealmName, fileSystemRealm, userRoleDecoder);
            return config;
        }
    }

    private AuthMechanism buildAuthMechanism(CommandContext context)
            throws Exception {
        AuthMechanism mec = null;
        if (getMechanism() == null) {
            return null;
        }
        List<String> available = ElytronUtil.getAvailableMechanisms(context,
                getFactorySpec());
        if (!available.contains(getMechanism())) {
            throw new CommandException("Unavailable mechanism " + getMechanism());
        }

        if (ElytronUtil.getMechanismsWithRealm().contains(getMechanism())) {
            MechanismConfiguration config = buildUserPasswordConfiguration(userPropertiesFile,
                    fileSystemRealmName, userRoleDecoder, exposedRealm,
                    groupPropertiesFile, propertiesRealmName, relativeTo, plaintext);
            mec = new AuthMechanism(getMechanism(), config);
        } else if (ElytronUtil.getMechanismsWithTrustStore().contains(getMechanism())) {
            MechanismConfiguration config = buildExternalConfiguration(context, keyStoreName, keyStoreRealmName, roles);
            mec = new AuthMechanism(getMechanism(), config);
        } else if (ElytronUtil.getMechanismsLocalUser().contains(getMechanism())) {
            MechanismConfiguration config = buildLocalUserConfiguration(context, superUser);
            mec = new AuthMechanism(getMechanism(), config);
        } else {
            mec = new AuthMechanism(getMechanism(), new EmptyConfiguration());
        }
        return mec;
    }

    private boolean shouldReload() {
        return !noReload;
    }

    private void configureBuilder(AuthSecurityBuilder builder) {
        builder.setNewRealmName(newRealmName).
                setAuthFactoryName(newAuthFactoryName).setSecurityDomainName(newSecurityDomain);
    }
}
