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
package org.jboss.as.cli.impl.aesh.cmd.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.aesh.command.Command;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.GroupCommand;
import org.aesh.command.GroupCommandDefinition;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.embedded.EmbeddedProcessLaunch;
import org.jboss.as.cli.impl.aesh.cmd.AbstractCompleter;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ElytronUtil;
import org.jboss.as.cli.impl.aesh.cmd.security.ssl.HTTPServerDisableSSLCommand;
import org.jboss.as.cli.impl.aesh.cmd.security.ssl.HTTPServerEnableSSLCommand;
import org.jboss.as.cli.impl.aesh.cmd.security.ssl.ManagementDisableSSLCommand;
import org.jboss.as.cli.impl.aesh.cmd.security.ssl.ManagementEnableSSLCommand;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;

/**
 * Parent command of all security related commands. Contains definitions of all
 * option names and option completers. This is the class to evolve when adding
 * new sub commands. Contains utility methods to execute remote requests and
 * reload the server.
 *
 * @author jdenise@redhat.com
 */
@GroupCommandDefinition(name = "security", description = "")
public class SecurityCommand implements GroupCommand<CLICommandInvocation, Command> {

    public static final FailureConsumer DEFAULT_FAILURE_CONSUMER = new FailureConsumer() {

        @Override
        public void failureOccured(CommandContext ctx, ModelNode reply) throws CommandException {
            throw new CommandException(Util.getFailureDescription(reply));
        }
    };

    public interface FailureConsumer {
        void failureOccured(CommandContext ctx, ModelNode reply) throws CommandException;
    }

    public static final String OPT_FILE_SYSTEM_REALM = "file-system-realm";
    public static final String OPT_USER_ROLE_DECODER = "user-role-decoder";
    public static final String OPT_USER_PROPERTIES_FILE = "user-properties-file";
    public static final String OPT_GROUP_PROPERTIES_FILE = "group-properties-file";
    public static final String OPT_RELATIVE_TO = "relative-to";
    public static final String OPT_NO_RELOAD = "no-reload";
    public static final String OPT_EXPOSED_REALM_NAME = "exposed-realm-name";

    public static final String OPT_NEW_SECURITY_DOMAIN_NAME = "new-security-domain-name";
    public static final String OPT_NEW_AUTH_FACTORY_NAME = "new-auth-factory-name";
    public static final String OPT_NEW_SECURITY_REALM_NAME = "new-security-realm-name";

    public static final String OPT_MECHANISM = "mechanism";
    public static final String OPT_SUPER_USER = "super-user";

    public static final String OPT_INTERACTIVE = "interactive";

    public static final String OPT_EXT_TRUST_STORE = "external-trust-store";

    public static final String OPT_KEY_STORE_NAME = "key-store-name";
    public static final String OPT_KEY_STORE_PATH = "key-store-path";

    public static final String OPT_TRUSTED_CERTIFICATE_PATH = "trusted-certificate-path";
    public static final String OPT_TRUST_STORE_NAME = "trust-store-name";
    public static final String OPT_NEW_TRUST_STORE_NAME = "new-trust-store-name";
    public static final String OPT_NEW_TRUST_MANAGER_NAME = "new-trust-manager-name";
    public static final String OPT_TRUST_STORE_FILE_NAME = "trust-store-file-name";
    public static final String OPT_TRUST_STORE_FILE_PASSWORD = "trust-store-file-password";
    public static final String OPT_NO_TRUSTED_CERTIFICATE_VALIDATION = "no-trusted-certificate-validation";

    public static final String OPT_KEY_STORE_PATH_RELATIVE_TO = "key-store-path-relative-to";
    public static final String OPT_KEY_STORE_PASSWORD = "key-store-password";
    public static final String OPT_KEY_STORE_TYPE = "key-store-type";

    public static final String OPT_MANAGEMENT_INTERFACE = "management-interface";

    public static final String OPT_HTTP_SECURE_SOCKET_BINDING = "http-secure-socket-binding";

    public static final String OPT_NEW_KEY_MANAGER_NAME = "new-key-manager-name";
    public static final String OPT_NEW_SSL_CONTEXT_NAME = "new-ssl-context-name";
    public static final String OPT_NEW_KEY_STORE_NAME = "new-key-store-name";

    public static final String OPT_SERVER_NAME = "server-name";
    public static final String OPT_NO_OVERRIDE_SECURITY_REALM = "no-override-security-realm";
    public static final String OPT_SECURITY_DOMAIN = "security-domain";

    private final CommandContext ctx;
    private final AtomicReference<EmbeddedProcessLaunch> embeddedServerRef;

    public SecurityCommand(CommandContext ctx, AtomicReference<EmbeddedProcessLaunch> embeddedServerRef) {
        this.ctx = ctx;
        this.embeddedServerRef = embeddedServerRef;
    }

    @Override
    public List<Command> getCommands() {
        List<Command> commands = new ArrayList<>();
        commands.add(new ManagementEnableSSLCommand(ctx));
        commands.add(new ManagementDisableSSLCommand(embeddedServerRef));
        commands.add(new HTTPServerEnableSSLCommand(ctx));
        commands.add(new HTTPServerDisableSSLCommand());
        return commands;
    }

    public static class OptionCompleters {

        public static class KeyStoreNameCompleter extends AbstractCompleter {

            @Override
            protected List<String> getItems(CLICompleterInvocation completerInvocation) {
                return ElytronUtil.getKeyStoreNames(completerInvocation.getCommandContext().
                        getModelControllerClient());
            }
        }

        public static class KeyStoreTypeCompleter extends AbstractCompleter {

            @Override
            protected List<String> getItems(CLICompleterInvocation completerInvocation) {
                return Arrays.asList(ElytronUtil.JKS, ElytronUtil.PKCS12);
            }
        }

        public static class ServerNameCompleter extends AbstractCompleter {

            @Override
            protected List<String> getItems(CLICompleterInvocation completerInvocation) {
                return Util.getUndertowServerNames(completerInvocation.getCommandContext().getModelControllerClient());
            }
        }

        public static class ManagementInterfaceCompleter extends AbstractCompleter {

            @Override
            protected List<String> getItems(CLICompleterInvocation completerInvocation) {
                return Util.getManagementInterfaces(completerInvocation.getCommandContext().getModelControllerClient());
            }
        }

        public static class SecureSocketBindingCompleter extends AbstractCompleter {

            @Override
            protected List<String> getItems(CLICompleterInvocation completerInvocation) {
                return Util.getStandardSocketBindings(completerInvocation.getCommandContext().getModelControllerClient());
            }
        }
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation)
            throws CommandException, InterruptedException {
        throw new CommandException("Command action is missing.");
    }

    public static String formatOption(String name) {
        return "--" + name;
    }

    public static void execute(CommandContext ctx, ModelNode request,
            FailureConsumer consumer) throws CommandException {
        ModelNode response;
        try {
            response = ctx.getModelControllerClient().execute(request);
        } catch (IOException ex) {
            try {
                consumer.failureOccured(ctx, null);
            } catch (Exception ex2) {
                ex.addSuppressed(ex2);
            }
            throw new CommandException(ex);
        }
        if (!Util.isSuccess(response)) {
            try {
                consumer.failureOccured(ctx, response);
            } catch (Exception ex) {
                if (ex instanceof CommandException) {
                    throw (CommandException) ex;
                }
                throw new CommandException(ex);
            }
        }
    }

    public static void execute(CommandContext ctx,
            ModelNode request, FailureConsumer consumer,
            boolean noReload) throws CommandException {
        execute(ctx, request, consumer);
        if (!noReload) {
            try {
                String mode = Util.getRunningMode(ctx);
                ctx.handle("reload --admin-only=" + (Util.ADMIN_ONLY.equals(mode) ? true : false));
                ctx.printLine("Server reloaded.");
            } catch (Exception ex) {
                throw new CommandException(ex.getLocalizedMessage(), ex);
            }
        } else {
            ctx.printLine("Warning: server has not been reloaded. Call 'reload' to apply changes.");
        }
    }
}
