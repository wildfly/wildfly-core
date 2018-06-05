/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.aesh.readline.terminal.formatting.CharacterType;
import org.aesh.readline.terminal.formatting.Color;
import org.aesh.readline.terminal.formatting.TerminalColor;
import org.aesh.readline.terminal.formatting.TerminalString;
import org.aesh.readline.terminal.formatting.TerminalTextStyle;


import org.jboss.as.cli.CommandContext.Scope;
import org.jboss.as.cli.handlers.FilenameTabCompleter;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.OperationRequestAddress.Node;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.operation.ParsedOperationRequestHeader;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.as.cli.parsing.CommandSubstitutionException;
import org.jboss.as.cli.parsing.operation.OperationFormat;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.logging.Logger;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.xnio.http.RedirectException;

/**
 *
 * @author Alexey Loubyansky
 */
public class Util {

    public static final String LINE_SEPARATOR = WildFlySecurityManager.getPropertyPrivileged("line.separator", null);

    public static final String ACCESS_CONTROL = "access-control";
    public static final String ACCESS_TYPE = "access-type";
    public static final String ADD = "add";
    public static final String ADDRESS = "address";
    public static final String ADMIN_ONLY = "ADMIN_ONLY";
    public static final String ALGORITHM = "algorithm";
    public static final String ALIAS = "alias";
    public static final String ALIAS_FILTER = "alias-filter";
    public static final String ALLOWED = "allowed";
    public static final String ALLOW_RESOURCE_SERVICE_RESTART = "allow-resource-service-restart";
    public static final String ALTERNATIVES = "alternatives";
    public static final String APPLICATION_REALM = "ApplicationRealm";
    public static final String APPLICATION_SECURITY_DOMAIN = "application-security-domain";
    public static final String ARCHIVE = "archive";
    public static final String ATTACHED_STREAMS = "attached-streams";
    public static final String ATTRIBUTES = "attributes";
    public static final String AVAILABLE_MECHANISMS = "available-mechanisms";
    public static final String AUTHENTICATION_OPTIONAL = "authentication-optional";
    public static final String BLOCKING_TIMEOUT = "blocking-timeout";
    public static final String BROWSE_CONTENT = "browse-content";
    public static final String BYTES = "bytes";
    public static final String CAPABILITY_REFERENCE = "capability-reference";
    public static final String CAPABILITY_REGISTRY = "capability-registry";
    public static final String CHILDREN = "children";
    public static final String CHILD_TYPE = "child-type";
    public static final String CLEAR_TEXT = "clear-text";
    public static final String COMBINED_DESCRIPTIONS = "combined-descriptions";
    public static final String COMPOSITE = "composite";
    public static final String CONCURRENT_GROUPS = "concurrent-groups";
    public static final String CONFIGURED = "configured";
    public static final String CONSTANT_REALM_MAPPER = "constant-realm-mapper";
    public static final String CONSTANT_ROLE_MAPPER = "constant-role-mapper";
    public static final String CONTENT = "content";
    public static final String CORE_SERVICE = "core-service";
    public static final String CREDENTIAL_REFERENCE = "credential-reference";
    public static final String CIPHER_SUITE_FILTER = "cipher-suite-filter";
    public static final String DATASOURCES = "datasources";
    public static final String DEFAULT = "default";
    public static final String DEFAULT_PERMISSION_MAPPER = "default-permission-mapper";
    public static final String DEPLOY = "deploy";
    public static final String DEPLOYMENT = "deployment";
    public static final String DEPLOYMENTS = "deployments";
    public static final String DEPLOYMENT_NAME = "deployment-name";
    public static final String DEPLOYMENT_OVERLAY = "deployment-overlay";
    public static final String DEPTH = "depth";
    public static final String DESCRIPTION = "description";
    public static final String DIGEST_REALM_NAME = "digest-realm-name";
    public static final String DISTINGUISHED_NAME = "distinguished-name";
    public static final String DOMAIN_FAILURE_DESCRIPTION = "domain-failure-description";
    public static final String DOMAIN_RESULTS = "domain-results";
    public static final String DRIVER_MODULE_NAME = "driver-module-name";
    public static final String DRIVER_NAME = "driver-name";
    public static final String ELYTRON = "elytron";
    public static final String ENABLED = "enabled";
    public static final String EXECUTE = "execute";
    public static final String EXPORT_CERTIFICATE = "export-certificate";
    public static final String EXPRESSIONS_ALLOWED = "expressions-allowed";
    public static final String EXTENSION = "extension";
    public static final String FAILURE_DESCRIPTION = "failure-description";
    public static final String FILESYSTEM_PATH = "filesystem-path";
    public static final String FILESYSTEM_REALM = "filesystem-realm";
    public static final String FINAL_PRINCIPAL_TRANSFORMER = "final-principal-transformer";
    public static final String POST_REALM_PRINCIPAL_TRANSFORMER = "post-realm-principal-transformer";
    public static final String PROVIDER_NAME = "provider-name";
    public static final String PRE_REALM_PRINCIPAL_TRANSFORMER = "pre-realm-principal-transformer";
    public static final String FULL_REPLACE_DEPLOYMENT = "full-replace-deployment";
    public static final String FALSE = "false";
    public static final String GENERATE_KEY_PAIR = "generate-key-pair";
    public static final String GENERATE_CERTIFICATE_SIGNING_REQUEST = "generate-certificate-signing-request";
    public static final String GET_PROVIDER_POINTS = "get-provider-points";
    public static final String GLOBAL = "global";
    public static final String GROUPS = "groups";
    public static final String GROUPS_ATTRIBUTE = "groups-attribute";
    public static final String GROUPS_PROPERTIES = "groups-properties";
    public static final String GROUPS_TO_ROLES = "groups-to-roles";
    public static final String HEAD_COMMENT_ALLOWED = "head-comment-allowed";
    public static final String HOST = "host";
    public static final String HTTP_AUTHENTICATION_FACTORY = "http-authentication-factory";
    public static final String HTTP_INTERFACE = "http-interface";
    public static final String HTTP_SERVER_MECHANISM_FACTORY = "http-server-mechanism-factory";
    public static final String HTTP_UPGRADE = "http-upgrade";
    public static final String HTTPS = "https";
    public static final String HTTPS_LISTENER = "https-listener";
    public static final String ID = "id";
    public static final String IDENTITY_REALM = "identity-realm";
    public static final String IMPORT_CERTIFICATE = "import-certificate";
    public static final String IN_SERIES = "in-series";
    public static final String INCLUDE_DEFAULTS = "include-defaults";
    public static final String INCLUDE_RUNTIME = "include-runtime";
    public static final String INCLUDE_SINGLETONS = "include-singletons";
    public static final String INPUT_STREAM_INDEX = "input-stream-index";
    public static final String INSTALLED_DRIVERS_LIST = "installed-drivers-list";
    public static final String JBOSS_SERVER_CONFIG_DIR = "jboss.server.config.dir";
    public static final String KEY_MANAGER = "key-manager";
    public static final String KEY_SIZE = "key-size";
    public static final String KEY_STORE = "key-store";
    public static final String KEY_STORE_REALM = "key-store-realm";
    public static final String LOCAL = "local";
    public static final String LOCAL_HOST_NAME = "local-host-name";
    public static final String MANAGEMENT = "management";
    public static final String MANAGEMENT_CLIENT_CONTENT = "management-client-content";
    public static final String MANAGEMENT_HTTPS = "management-https";
    public static final String MANAGEMENT_INTERFACE = "management-interface";
    public static final String MASTER = "master";
    public static final String MAX_FAILED_SERVERS = "max-failed-servers";
    public static final String MAX_FAILURE_PERCENTAGE = "max-failure-percentage";
    public static final String MAX_OCCURS = "max-occurs";
    public static final String MECHANISM_CONFIGURATIONS = "mechanism-configurations";
    public static final String MECHANISM_NAME = "mechanism-name";
    public static final String MECHANISM_REALM_CONFIGURATIONS = "mechanism-realm-configurations";
    public static final String METRIC = "metric";
    public static final String MIN_OCCURS = "min-occurs";
    public static final String MODULE = "module";
    public static final String MODULE_SLOT = "module-slot";
    public static final String NAME = "name";
    public static final String NATIVE_INTERFACE = "native-interface";
    public static final String NEED_CLIENT_AUTH = "need-client-auth";
    public static final String NILLABLE = "nillable";
    public static final String NORMAL = "normal";
    public static final String OPERATION = "operation";
    public static final String OPERATIONS = "operations";
    public static final String OPERATION_HEADERS = "operation-headers";
    public static final String OUTCOME = "outcome";
    public static final String PATH = "path";
    public static final String PEM = "pem";
    public static final String PERMISSION_MAPPER = "permission-mapper";
    public static final String PERSISTENT = "persistent";
    public static final String PROBLEM = "problem";
    public static final String PRODUCT_NAME = "product-name";
    public static final String PRODUCT_VERSION = "product-version";
    public static final String PROFILE = "profile";
    public static final String PROPERTIES_REALM = "properties-realm";
    public static final String PROTOCOLS = "protocols";
    public static final String PROVIDERS = "providers";
    public static final String READ = "read";
    public static final String READ_ATTRIBUTE = "read-attribute";
    public static final String READ_CHILDREN_NAMES = "read-children-names";
    public static final String READ_CHILDREN_RESOURCES = "read-children-resources";
    public static final String READ_CHILDREN_TYPES = "read-children-types";
    public static final String READ_ONLY = "read-only";
    public static final String READ_OPERATION_DESCRIPTION = "read-operation-description";
    public static final String READ_OPERATION_NAMES = "read-operation-names";
    public static final String READ_WRITE = "read-write";
    public static final String READ_RESOURCE = "read-resource";
    public static final String READ_RESOURCE_DESCRIPTION = "read-resource-description";
    public static final String REALMS = "realms";
    public static final String REALM = "realm";
    public static final String REALM_NAME = "realm-name";
    public static final String REALM_MAPPER = "realm-mapper";
    public static final String REDEPLOY = "redeploy";
    public static final String REDEPLOY_AFFECTED = "redeploy-affected";
    public static final String REDEPLOY_LINKS = "redeploy-links";
    public static final String RELATIVE_TO = "relative-to";
    public static final String RELEASE_CODENAME = "release-codename";
    public static final String RELEASE_VERSION = "release-version";
    public static final String RELOAD = "reload";
    public static final String REMOVE = "remove";
    public static final String REPLY_PROPERTIES = "reply-properties";
    public static final String REQUEST_PROPERTIES = "request-properties";
    public static final String REQUIRED = "required";
    public static final String REQUIRES = "requires";
    public static final String RESOLVE_EXPRESSIONS = "resolve-expressions";
    public static final String RESPONSE_HEADERS = "response-headers";
    public static final String RESTART = "restart";
    public static final String RESTART_REQUIRED = "restart-required";
    public static final String RESULT = "result";
    public static final String ROLES = "roles";
    public static final String ROLE_DECODER = "role-decoder";
    public static final String ROLE_MAPPER = "role-mapper";
    public static final String ROLLED_BACK = "rolled-back";
    public static final String ROLLBACK_ACROSS_GROUPS = "rollback-across-groups";
    public static final String ROLLBACK_FAILURE_DESCRIPTION = "rollback-failure-description";
    public static final String ROLLBACK_ON_RUNTIME_FAILURE = "rollback-on-runtime-failure";
    public static final String ROLLING_TO_SERVERS = "rolling-to-servers";
    public static final String ROLLOUT_PLAN = "rollout-plan";
    public static final String ROLLOUT_PLANS = "rollout-plans";
    public static final String RUNTIME_NAME = "runtime-name";
    public static final String RUNNING_MODE = "running-mode";
    public static final String SASL_AUTHENTICATION_FACTORY = "sasl-authentication-factory";
    public static final String SASL_SERVER_FACTORY = "sasl-server-factory";
    public static final String SECURE_SOCKET_BINDING = "secure-socket-binding";
    public static final String SECURITY_DOMAIN = "security-domain";
    public static final String SECURITY_REALM = "security-realm";
    public static final String SERVER = "server";
    public static final String SERVER_GROUP = "server-group";
    public static final String SERVER_SSL_CONTEXT = "server-ssl-context";
    public static final String SHUTDOWN = "shutdown";
    public static final String SIMPLE_ROLE_DECODER = "simple-role-decoder";
    public static final String SOCKET_BINDING = "socket-binding";
    public static final String SOCKET_BINDING_GROUP = "socket-binding-group";
    public static final String SSL_CONTEXT = "ssl-context";
    public static final String STANDARD_SOCKETS = "standard-sockets";
    public static final String START_MODE = "start-mode";
    public static final String STATUS = "status";
    public static final String STEP_1 = "step-1";
    public static final String STEP_2 = "step-2";
    public static final String STEP_3 = "step-3";
    public static final String STEPS = "steps";
    public static final String STORAGE = "storage";
    public static final String STORE = "store";
    public static final String SUBDEPLOYMENT = "subdeployment";
    public static final String SUBSYSTEM = "subsystem";
    public static final String SUCCESS = "success";
    public static final String SUPER_USER_MAPPER = "super-user-mapper";
    public static final String SUSPEND_TIMEOUT = "suspend-timeout";
    public static final String TAIL_COMMENT_ALLOWED = "tail-comment-allowed";
    public static final String TIMEOUT = "timeout";
    public static final String TRIM_DESCRIPTIONS = "trim-descriptions";
    public static final String TRUE = "true";
    public static final String TRUST_MANAGER = "trust-manager";
    public static final String TRUST_CACERTS = "trust-cacerts";
    public static final String TYPE = "type";
    public static final String UNDEFINE_ATTRIBUTE = "undefine-attribute";
    public static final String UNDEPLOY = "undeploy";
    public static final String UNDERTOW = "undertow";
    public static final String UPLOAD_DEPLOYMENT_STREAM = "upload-deployment-stream";
    public static final String URL = "url";
    public static final String USERS_PROPERTIES = "users-properties";
    public static final String USE_CIPHER_SUITES_ORDER = "use-cipher-suites-order";
    public static final String UUID = "uuid";
    public static final String VALID = "valid";
    public static final String VALIDATE = "validate";
    public static final String VALIDITY = "validity";
    public static final String VALIDATE_ADDRESS = "validate-address";
    public static final String VALUE = "value";
    public static final String VALUE_TYPE = "value-type";
    public static final String WANT_CLIENT_AUTH = "want-client-auth";
    public static final String WRITE = "write";
    public static final String WRITE_ATTRIBUTE = "write-attribute";

    public static final String DESCRIPTION_RESPONSE = "DESCRIPTION_RESPONSE";

    public static final String NOT_OPERATOR = "!";

    private static TerminalColor ERROR_COLOR;
    private static TerminalColor SUCCESS_COLOR;
    private static TerminalColor WARN_COLOR;
    private static TerminalColor REQUIRED_COLOR;
    private static TerminalColor WORKFLOW_COLOR;
    private static TerminalColor PROMPT_COLOR;
    private static TerminalTextStyle BOLD_STYLE = new TerminalTextStyle(CharacterType.BOLD);
    private static String ENCODING_EXCEPTION_MESSAGE = "Encoding exception.";

    private static Logger LOG = Logger.getLogger(Util.class);

    public static final String formatErrorMessage(String message) {
        return new TerminalString(message, ERROR_COLOR, BOLD_STYLE).toString();
    }

    public static final String formatSuccessMessage(String message) {
        return new TerminalString(message, SUCCESS_COLOR).toString();
    }

    public static final String formatWarnMessage(String message) {
        return new TerminalString(message, WARN_COLOR, BOLD_STYLE).toString();
    }

    public static TerminalString formatRequired(TerminalString name) {
        return new TerminalString(name.toString(), REQUIRED_COLOR, BOLD_STYLE);
    }

    public static String formatWorkflowPrompt(String prompt) {
        return new TerminalString(prompt, WORKFLOW_COLOR).toString();
    }

    public static boolean isWindows() {
        return WildFlySecurityManager.getPropertyPrivileged("os.name", null).toLowerCase(Locale.ENGLISH).indexOf("windows") >= 0;
    }

    public static boolean isSolaris() {
        String osName = System.getProperty("os.name");
        if (osName == null) {
            return false;
        }
        return (osName.indexOf("Solaris") > -1) || (osName.indexOf("solaris") > -1) || (osName.indexOf("SunOS") > -1);
    }

    public static boolean isSuccess(ModelNode operationResponse) {
        if (operationResponse != null) {
            return operationResponse.hasDefined(Util.OUTCOME) && operationResponse.get(Util.OUTCOME).asString().equals(Util.SUCCESS);
        }
        return false;
    }

    public static void formatPrompt(StringBuilder buffer) {
        if (buffer.toString().contains("@")) {
            int at = buffer.indexOf("@");
            int space = buffer.lastIndexOf(" ");
            String preAt = buffer.substring(1, at);
            String postAt = buffer.substring(at + 1, space + 1);
            buffer.delete(1, space + 1);
            buffer.append(
                    new TerminalString(preAt, PROMPT_COLOR).toString());
            buffer.append("@");
            buffer.append(
                    new TerminalString(postAt, PROMPT_COLOR).toString());
        } else if (buffer.toString().contains("disconnected")) {
            String prompt = buffer.substring(1);
            buffer.replace(1, buffer.lastIndexOf(" ") + 1,
                           new TerminalString(prompt, ERROR_COLOR).toString());
        } else {
            String prompt = buffer.substring(1);
            buffer.replace(1, buffer.lastIndexOf(" ") + 1,
                    new TerminalString(prompt, PROMPT_COLOR).toString());
        }
    }

    public static void configureColors(CommandContext ctx) {
        ColorConfig colorConfig = ctx.getConfig().getColorConfig();
        if (colorConfig != null) {
            ERROR_COLOR = new TerminalColor((colorConfig.getErrorColor() != null) ? colorConfig.getErrorColor() : Color.RED,
                    Color.DEFAULT, Color.Intensity.BRIGHT);

            if (!isWindows()) {
                WARN_COLOR = new TerminalColor((colorConfig.getWarnColor() != null) ? colorConfig.getWarnColor() : Color.YELLOW,
                        Color.DEFAULT, Color.Intensity.NORMAL);
            } else {
                WARN_COLOR = new TerminalColor((colorConfig.getWarnColor() != null) ? colorConfig.getWarnColor() : Color.YELLOW,
                        Color.DEFAULT, Color.Intensity.BRIGHT);
            }

            SUCCESS_COLOR = new TerminalColor(
                    (colorConfig.getSuccessColor() != null) ? colorConfig.getSuccessColor() : Color.DEFAULT, Color.DEFAULT,
                    Color.Intensity.NORMAL);
            REQUIRED_COLOR = new TerminalColor(
                    (colorConfig.getRequiredColor() != null) ? colorConfig.getRequiredColor() : Color.CYAN, Color.DEFAULT,
                    Color.Intensity.BRIGHT);
            WORKFLOW_COLOR = new TerminalColor(
                    (colorConfig.getWorkflowColor() != null) ? colorConfig.getWorkflowColor() : Color.GREEN, Color.DEFAULT,
                    Color.Intensity.BRIGHT);
            PROMPT_COLOR = new TerminalColor(
                    (colorConfig.getWorkflowColor() != null) ? colorConfig.getPromptColor() : Color.BLUE, Color.DEFAULT,
                    Color.Intensity.BRIGHT);
        } else {
            ERROR_COLOR = new TerminalColor(Color.RED, Color.DEFAULT, Color.Intensity.BRIGHT);

            if (!isWindows()) {
                WARN_COLOR = new TerminalColor(Color.YELLOW, Color.DEFAULT, Color.Intensity.NORMAL);
            } else {
                WARN_COLOR = new TerminalColor(Color.YELLOW, Color.DEFAULT, Color.Intensity.BRIGHT);
            }

            SUCCESS_COLOR = new TerminalColor(Color.DEFAULT, Color.DEFAULT, Color.Intensity.NORMAL);
            REQUIRED_COLOR = new TerminalColor(Color.MAGENTA, Color.DEFAULT, Color.Intensity.BRIGHT);
            WORKFLOW_COLOR = new TerminalColor(Color.GREEN, Color.DEFAULT, Color.Intensity.BRIGHT);
            PROMPT_COLOR = new TerminalColor(Color.BLUE, Color.DEFAULT, Color.Intensity.BRIGHT);
        }
    }

    public static String getFailureDescription(ModelNode operationResponse) {
        if(operationResponse == null) {
            return null;
        }
        ModelNode descr = operationResponse.get(Util.FAILURE_DESCRIPTION);
        if(descr == null) {
            return null;
        }
        if(descr.hasDefined(Util.DOMAIN_FAILURE_DESCRIPTION)) {
            descr = descr.get(Util.DOMAIN_FAILURE_DESCRIPTION);
        }
        if(descr.hasDefined(Util.ROLLED_BACK)) {
            final StringBuilder buf = new StringBuilder();
            buf.append(descr.asString());
            if(descr.get(Util.ROLLED_BACK).asBoolean()) {
                buf.append("(The operation was rolled back)");
            } else if(descr.hasDefined(Util.ROLLBACK_FAILURE_DESCRIPTION)){
                buf.append(descr.get(Util.ROLLBACK_FAILURE_DESCRIPTION).toString());
            } else {
                buf.append("(The operation also failed to rollback, failure description is not available.)");
            }
        } else {
            return descr.asString();
        }
        return descr.asString();
    }

    public static List<String> getList(ModelNode operationResult) {
        if(!operationResult.hasDefined(RESULT))
            return Collections.emptyList();
        List<ModelNode> nodeList = operationResult.get(RESULT).asList();
        if(nodeList.isEmpty())
            return Collections.emptyList();
        List<String> list = new ArrayList<String>(nodeList.size());
        for(ModelNode node : nodeList) {
            list.add(node.asString());
        }
        return list;
    }

    public static List<String> getList(ModelNode operationResult, String wildcardExpr) {
        if(!operationResult.hasDefined(RESULT))
            return Collections.emptyList();
        final List<ModelNode> nodeList = operationResult.get(RESULT).asList();
        if(nodeList.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> list = new ArrayList<String>(nodeList.size());
        final Pattern pattern = Pattern.compile(wildcardToJavaRegex(wildcardExpr));
        for(ModelNode node : nodeList) {
            final String candidate = node.asString();
            if(pattern.matcher(candidate).matches()) {
                list.add(candidate);
            }
        }
        return list;
    }

    public static String wildcardToJavaRegex(String expr) {
        if(expr == null) {
            throw new IllegalArgumentException("expr is null");
        }
        String regex = expr.replaceAll("([(){}\\[\\].+^$])", "\\\\$1"); // escape regex characters
        regex = regex.replaceAll("\\*", ".*"); // replace * with .*
        regex = regex.replaceAll("\\?", "."); // replace ? with .
        return regex;
    }

    public static boolean listContains(ModelNode operationResult, String item) {
        if(!operationResult.hasDefined(RESULT))
            return false;

        List<ModelNode> nodeList = operationResult.get(RESULT).asList();
        if(nodeList.isEmpty())
            return false;

        for(ModelNode node : nodeList) {
            if(node.asString().equals(item)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDeploymentInRepository(String name, ModelControllerClient client) {
        return getDeployments(client).contains(name);
    }

    public static boolean isDeployedAndEnabledInStandalone(String name, ModelControllerClient client) {

        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        ModelNode request;
        try {
            builder.setOperationName(Util.READ_CHILDREN_NAMES);
            builder.addProperty(Util.CHILD_TYPE, Util.DEPLOYMENT);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            ModelNode outcome = client.execute(request);
            if (isSuccess(outcome)) {
                if(!listContains(outcome, name)) {
                    return false;
                }
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }

        builder = new DefaultOperationRequestBuilder();
        builder.addNode(Util.DEPLOYMENT, name);
        builder.setOperationName(Util.READ_ATTRIBUTE);
        builder.addProperty(Util.NAME, Util.ENABLED);
        try {
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            ModelNode outcome = client.execute(request);
            if (isSuccess(outcome)) {
                if(!outcome.hasDefined(RESULT)) {
                    return false;
                }
                return outcome.get(RESULT).asBoolean();
            }
        } catch(Exception e) {
        }
        return false;
    }

    public static boolean isEnabledDeployment(String name,
            ModelControllerClient client, String serverGroup) throws
            OperationFormatException, IOException, CommandFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        if (serverGroup != null) {
            builder.addNode(Util.SERVER_GROUP, serverGroup);
        }
        builder.addNode(Util.DEPLOYMENT, name);
        builder.setOperationName(Util.READ_ATTRIBUTE);
        builder.addProperty(Util.NAME, Util.ENABLED);
        ModelNode request = builder.buildRequest();
        ModelNode outcome = client.execute(request);
        if (isSuccess(outcome)) {
            if (!outcome.hasDefined(RESULT)) {
                throw new CommandFormatException("No result for " + name);
            }
            return outcome.get(RESULT).asBoolean();
        }
        return false;
    }

    public static boolean isDeploymentPresent(String name, ModelControllerClient client, String serverGroup) throws OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();

        builder.addNode(Util.SERVER_GROUP, serverGroup);
        builder.addNode(Util.DEPLOYMENT, name);
        builder.setOperationName(Util.READ_RESOURCE);
        ModelNode request = builder.buildRequest();
        try {
            ModelNode outcome = client.execute(request);
            if (isSuccess(outcome)) {
                return outcome.hasDefined(RESULT);
            }
        } catch (Exception ex) {
            return false;
        }

        return false;
    }

    public static List<String> getAllEnabledServerGroups(String deploymentName, ModelControllerClient client) {

        List<String> serverGroups = getServerGroups(client);
        if(serverGroups.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<String>();
        for(String serverGroup : serverGroups) {
            DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
            ModelNode request;
            try {
                builder.setOperationName(Util.READ_CHILDREN_NAMES);
                builder.addNode(Util.SERVER_GROUP, serverGroup);
                builder.addProperty(Util.CHILD_TYPE, Util.DEPLOYMENT);
                request = builder.buildRequest();
            } catch (OperationFormatException e) {
                throw new IllegalStateException("Failed to build operation", e);
            }

            try {
                ModelNode outcome = client.execute(request);
                if (isSuccess(outcome)) {
                    if(!listContains(outcome, deploymentName)) {
                        continue;
                    }
                } else {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }

            builder = new DefaultOperationRequestBuilder();
            builder.addNode(SERVER_GROUP, serverGroup);
            builder.addNode(DEPLOYMENT, deploymentName);
            builder.setOperationName(READ_ATTRIBUTE);
            builder.addProperty(NAME, ENABLED);
            try {
                request = builder.buildRequest();
            } catch (OperationFormatException e) {
                throw new IllegalStateException("Failed to build operation", e);
            }

            try {
                ModelNode outcome = client.execute(request);
                if (isSuccess(outcome)) {
                    if(!outcome.hasDefined("result")) {
                        continue;
                    }
                    if(outcome.get("result").asBoolean()) {
                        result.add(serverGroup);
                    }
                }
            } catch(Exception e) {
                continue;
            }
        }

        return result;
    }

    public static List<String> getServerGroupsReferencingDeployment(String deploymentName, ModelControllerClient client)
            throws CommandLineException {
        final List<String> serverGroups = getServerGroups(client);
        if(serverGroups.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> groupNames = new ArrayList<String>();
        for(String serverGroup : serverGroups) {
            final ModelNode request = new ModelNode();
            request.get(Util.OPERATION).set(Util.VALIDATE_ADDRESS);
            request.get(Util.ADDRESS).setEmptyList();
            final ModelNode addr = request.get(Util.VALUE);
            addr.add(Util.SERVER_GROUP, serverGroup);
            addr.add(Util.DEPLOYMENT, deploymentName);

            final ModelNode response;
            try {
                response = client.execute(request);
            } catch (Exception e) {
                throw new CommandLineException("Failed to execute " + Util.VALIDATE_ADDRESS + " for " + request.get(Util.ADDRESS) , e);
            }
            if (response.has(Util.RESULT)) {
                final ModelNode result = response.get(Util.RESULT);
                if(result.has(Util.VALID)) {
                    if(result.get(Util.VALID).asBoolean()) {
                        groupNames.add(serverGroup);
                    }
                } else {
                    throw new CommandLineException("Failed to validate address " + request.get(Util.ADDRESS) + ": " + response);
                }
            } else {
                throw new CommandLineException(Util.getFailureDescription(response));
            }
        }
        return groupNames;
    }

    public static List<String> getServerGroupsReferencingOverlay(String overlayName, ModelControllerClient client)
            throws CommandLineException {
        final List<String> serverGroups = getServerGroups(client);
        if(serverGroups.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> groupNames = new ArrayList<String>();
        for(String serverGroup : serverGroups) {
            final ModelNode request = new ModelNode();
            request.get(Util.OPERATION).set(Util.VALIDATE_ADDRESS);
            request.get(Util.ADDRESS).setEmptyList();
            final ModelNode addr = request.get(Util.VALUE);
            addr.add(Util.SERVER_GROUP, serverGroup);
            addr.add(Util.DEPLOYMENT_OVERLAY, overlayName);

            final ModelNode response;
            try {
                response = client.execute(request);
            } catch (Exception e) {
                throw new CommandLineException("Failed to execute " + Util.VALIDATE_ADDRESS + " for " + request.get(Util.ADDRESS) , e);
            }
            if (response.has(Util.RESULT)) {
                final ModelNode result = response.get(Util.RESULT);
                if(result.has(Util.VALID)) {
                    if(result.get(Util.VALID).asBoolean()) {
                        groupNames.add(serverGroup);
                    }
                } else {
                    throw new CommandLineException("Failed to validate address " + request.get(Util.ADDRESS) + ": " + response);
                }
            } else {
                throw new CommandLineException(Util.getFailureDescription(response));
            }
        }
        return groupNames;
    }

    public static List<String> getManagementInterfaces(ModelControllerClient client) {

        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_CHILDREN_NAMES);
            builder.addNode(Util.CORE_SERVICE, Util.MANAGEMENT);
            builder.addProperty(Util.CHILD_TYPE, Util.MANAGEMENT_INTERFACE);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            final ModelNode outcome = client.execute(request);
            if (isSuccess(outcome)) {
                return getList(outcome);
            }
        } catch (Exception e) {
        }

        return Collections.emptyList();
    }

    public static List<String> getUndertowServerNames(ModelControllerClient client) {

        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_CHILDREN_NAMES);
            builder.addNode(Util.SUBSYSTEM, Util.UNDERTOW);
            builder.addProperty(Util.CHILD_TYPE, Util.SERVER);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            final ModelNode outcome = client.execute(request);
            if (isSuccess(outcome)) {
                return getList(outcome);
            }
        } catch (Exception e) {
        }

        return Collections.emptyList();
    }

    public static List<String> getStandardSocketBindings(ModelControllerClient client) {

        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_CHILDREN_NAMES);
            builder.addNode(Util.SOCKET_BINDING_GROUP, Util.STANDARD_SOCKETS);
            builder.addProperty(Util.CHILD_TYPE, Util.SOCKET_BINDING);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            final ModelNode outcome = client.execute(request);
            if (isSuccess(outcome)) {
                return getList(outcome);
            }
        } catch (Exception e) {
        }

        return Collections.emptyList();
    }

    public static List<String> getDeployments(ModelControllerClient client) {

        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_CHILDREN_NAMES);
            builder.addProperty(Util.CHILD_TYPE, Util.DEPLOYMENT);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            final ModelNode outcome = client.execute(request);
            if (isSuccess(outcome)) {
                return getList(outcome);
            }
        } catch (Exception e) {
        }

        return Collections.emptyList();
    }

    public static List<String> getDeploymentRuntimeNames(ModelControllerClient client) {
        Objects.requireNonNull(client);
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_RESOURCE);
            builder.addNode(Util.DEPLOYMENT, "*");
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        List<String> names = new ArrayList<>();
        try {
            final ModelNode outcome = client.execute(request);
            if (isSuccess(outcome)) {
                List<ModelNode> deployments = outcome.get(RESULT).asList();
                for (ModelNode item : deployments) {
                    ModelNode deployment = item.get(RESULT);
                    String runtime = deployment.get(RUNTIME_NAME).asString();
                    if (!names.contains(runtime)) {
                        names.add(runtime);
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Got exception retrieving deployment runtime names", e);
        }
        return names;
    }

    public static List<String> getDeployments(ModelControllerClient client, String serverGroup) {

        final ModelNode request = new ModelNode();
        ModelNode address = request.get(ADDRESS);
        if(serverGroup != null) {
            address.add(SERVER_GROUP, serverGroup);
        }
        request.get(OPERATION).set(READ_CHILDREN_NAMES);
        request.get(CHILD_TYPE).set(DEPLOYMENT);
        try {
            final ModelNode outcome = client.execute(request);
            if (isSuccess(outcome)) {
                return getList(outcome);
            }
        } catch (Exception e) {
        }

        return Collections.emptyList();
    }

    public static List<String> getMatchingDeployments(ModelControllerClient client, String wildcardExpr, String serverGroup) {
        return getMatchingDeployments(client, wildcardExpr, serverGroup, false);
    }

    public static List<String> getMatchingDeployments(ModelControllerClient client, String wildcardExpr, String serverGroup,
            boolean matchSubdeployments) {

        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        ModelNode request;
        try {
            if(serverGroup != null) {
                builder.addNode(Util.SERVER_GROUP, serverGroup);
            }
            builder.setOperationName(Util.READ_CHILDREN_NAMES);
            builder.addProperty(Util.CHILD_TYPE, Util.DEPLOYMENT);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            ModelNode outcome = client.execute(request);
            if (isSuccess(outcome)) {
                if(!outcome.hasDefined(RESULT)) {
                    return Collections.emptyList();
                }
                final List<ModelNode> nodeList = outcome.get(RESULT).asList();
                if(nodeList.isEmpty()) {
                    return Collections.emptyList();
                }
                final List<String> list = new ArrayList<String>(nodeList.size());
                final Pattern pattern = Pattern.compile(wildcardToJavaRegex(wildcardExpr));
                for(ModelNode node : nodeList) {
                    final String candidate = node.asString();
                    if(pattern.matcher(candidate).matches()) {
                        list.add(candidate);
                    } else if(matchSubdeployments) {
                        // look into subdeployments
                        builder = new DefaultOperationRequestBuilder();
                        try {
                            if(serverGroup != null) {
                                builder.addNode(Util.SERVER_GROUP, serverGroup);
                            }
                            builder.addNode(Util.DEPLOYMENT, candidate);
                            builder.setOperationName(Util.READ_CHILDREN_NAMES);
                            builder.addProperty(Util.CHILD_TYPE, Util.SUBDEPLOYMENT);
                            request = builder.buildRequest();
                        } catch (OperationFormatException e) {
                            throw new IllegalStateException("Failed to build operation", e);
                        }

                        outcome = client.execute(request);
                        if (isSuccess(outcome) && outcome.hasDefined(RESULT)) {
                            final List<ModelNode> subdList = outcome.get(RESULT).asList();
                            if(!subdList.isEmpty()) {
                                for(ModelNode subd : subdList) {
                                    final String subdName = subd.asString();
                                    if(pattern.matcher(subdName).matches()) {
                                        list.add(candidate);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                return list;
            }
        } catch (Exception e) {
        }

        return Collections.emptyList();
    }

    public static List<String> getServerGroups(ModelControllerClient client) {

        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_CHILDREN_NAMES);
            builder.addProperty(Util.CHILD_TYPE, Util.SERVER_GROUP);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            ModelNode outcome = client.execute(request);
            if (isSuccess(outcome)) {
                return getList(outcome);
            }
        } catch (Exception e) {
        }

        return Collections.emptyList();
    }

    public static List<String> getHosts(ModelControllerClient client) {

        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_CHILDREN_NAMES);
            builder.addProperty(Util.CHILD_TYPE, Util.HOST);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            ModelNode outcome = client.execute(request);
            if (isSuccess(outcome)) {
                return getList(outcome);
            }
        } catch (Exception e) {
        }

        return Collections.emptyList();
    }

    public static List<String> getNodeTypes(ModelControllerClient client, OperationRequestAddress address) {
        if(client == null) {
            return Collections.emptyList();
        }

        if(address.endsOnType()) {
            throw new IllegalArgumentException("The prefix isn't expected to end on a type.");
        }

        ModelNode request;
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder(address);
        try {
            builder.setOperationName(Util.READ_CHILDREN_TYPES);
            request = builder.buildRequest();
        } catch (OperationFormatException e1) {
            throw new IllegalStateException("Failed to build operation", e1);
        }

        List<String> result;
        try {
            ModelNode outcome = client.execute(request);
            if (!Util.isSuccess(outcome)) {
                // TODO logging... exception?
                result = Collections.emptyList();
            } else {
                result = Util.getList(outcome);
            }
        } catch (Exception e) {
            result = Collections.emptyList();
        }
        return result;
    }

    public static List<String> getNodeNames(ModelControllerClient client, OperationRequestAddress address, String type) {
        if(client == null) {
            return Collections.emptyList();
        }

        if(address != null && address.endsOnType()) {
            throw new IllegalArgumentException("The address isn't expected to end on a type.");
        }

        final ModelNode request;
        DefaultOperationRequestBuilder builder = address == null ? new DefaultOperationRequestBuilder() : new DefaultOperationRequestBuilder(address);
        try {
            builder.setOperationName(Util.READ_CHILDREN_NAMES);
            builder.addProperty(Util.CHILD_TYPE, type);
            request = builder.buildRequest();
        } catch (OperationFormatException e1) {
            throw new IllegalStateException("Failed to build operation", e1);
        }

        List<String> result;
        try {
            ModelNode outcome = client.execute(request);
            if (!Util.isSuccess(outcome)) {
                // TODO logging... exception?
                result = Collections.emptyList();
            } else {
                result = Util.getList(outcome);
            }
        } catch (Exception e) {
            result = Collections.emptyList();
        }
        return result;
    }

    public static List<String> getOperationNames(CommandContext ctx, OperationRequestAddress prefix) {

        ModelControllerClient client = ctx.getModelControllerClient();
        if(client == null) {
            return Collections.emptyList();
        }

        if(prefix.endsOnType()) {
            throw new IllegalArgumentException("The prefix isn't expected to end on a type.");
        }

        ModelNode request;
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder(prefix);
        try {
            builder.setOperationName(Util.READ_OPERATION_NAMES);
            request = builder.buildRequest();
        } catch (OperationFormatException e1) {
            throw new IllegalStateException("Failed to build operation", e1);
        }

        if(ctx.getConfig().isAccessControl()) {
            request.get(Util.ACCESS_CONTROL).set(true);
        }

        List<String> result;
        try {
            ModelNode outcome = client.execute(request);
            if (!Util.isSuccess(outcome)) {
                // TODO logging... exception?
                result = Collections.emptyList();
            } else {
                result = Util.getList(outcome);
            }
        } catch (Exception e) {
            result = Collections.emptyList();
        }
        return result;
    }

    public static List<String> getJmsResources(ModelControllerClient client, String profile, String type) {

        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            if(profile != null) {
                builder.addNode("profile", profile);
            }
            builder.addNode("subsystem", "messaging");
            builder.setOperationName("read-children-names");
            builder.addProperty("child-type", type);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            ModelNode outcome = client.execute(request);
            if (isSuccess(outcome)) {
                return getList(outcome);
            }
        } catch (Exception e) {
        }

        return Collections.emptyList();
    }

    public static List<String> getDatasources(ModelControllerClient client, String profile, String dsType) {

        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            if(profile != null) {
                builder.addNode("profile", profile);
            }
            builder.addNode(Util.SUBSYSTEM, Util.DATASOURCES);
            builder.setOperationName(Util.READ_CHILDREN_NAMES);
            builder.addProperty(Util.CHILD_TYPE, dsType);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            ModelNode outcome = client.execute(request);
            if (isSuccess(outcome)) {
                return getList(outcome);
            }
        } catch (Exception e) {
        }

        return Collections.emptyList();
    }

    public static boolean isTopic(ModelControllerClient client, String name) {
        List<String> topics = getJmsResources(client, null, "jms-topic");
        return topics.contains(name);
    }

    public static boolean isQueue(ModelControllerClient client, String name) {
        List<String> queues = getJmsResources(client, null, "jms-queue");
        return queues.contains(name);
    }

    public static boolean isConnectionFactory(ModelControllerClient client, String name) {
        List<String> cf = getJmsResources(client, null, "connection-factory");
        return cf.contains(name);
    }

    public static ModelNode configureDeploymentOperation(String operationName, String uniqueName, String serverGroup) {
        ModelNode op = new ModelNode();
        op.get(OPERATION).set(operationName);
        if (serverGroup != null) {
            op.get(ADDRESS).add(Util.SERVER_GROUP, serverGroup);
        }
        op.get(ADDRESS).add(DEPLOYMENT, uniqueName);
        return op;
    }

    public static boolean isValidPath(ModelControllerClient client, String... node) throws CommandLineException {
        if(node == null) {
            return false;
        }
        if(node.length % 2 != 0) {
            return false;
        }
        final ModelNode op = new ModelNode();
        op.get(ADDRESS).setEmptyList();
        op.get(OPERATION).set(VALIDATE_ADDRESS);
        final ModelNode addressValue = op.get(VALUE);
        for(int i = 0; i < node.length; i += 2) {
            addressValue.add(node[i], node[i+1]);
        }
        final ModelNode response;
        try {
            response = client.execute(op);
        } catch (IOException e) {
            throw new CommandLineException("Failed to execute " + VALIDATE_ADDRESS, e);
        }
        final ModelNode result = response.get(Util.RESULT);
        if(!result.isDefined()) {
            return false;
        }
        final ModelNode valid = result.get(Util.VALID);
        if(!valid.isDefined()) {
            return false;
        }
        return valid.asBoolean();
    }

    public static String getCommonStart(List<String> list) {
        final int size = list.size();
        if(size == 0) {
            return null;
        }
        if(size == 1) {
            return list.get(0);
        }
        final String first = list.get(0);
        final String last = list.get(size - 1);

        int minSize = Math.min(first.length(), last.length());
        for(int i = 0; i < minSize; ++i) {
            if(first.charAt(i) != last.charAt(i)) {
                if(i == 0) {
                    return null;
                } else {
                    return first.substring(0, i);
                }
            }
        }
        return first.substring(0, minSize);
    }

    public static String escapeString(String name, EscapeSelector selector) {
        for(int i = 0; i < name.length(); ++i) {
            char ch = name.charAt(i);
            if(selector.isEscape(ch)) {
                StringBuilder builder = new StringBuilder();
                builder.append(name, 0, i);
                builder.append('\\').append(ch);
                for(int j = i + 1; j < name.length(); ++j) {
                    ch = name.charAt(j);
                    if(selector.isEscape(ch)) {
                        builder.append('\\');
                    }
                    builder.append(ch);
                }
                return builder.toString();
            }
        }
        return name;
    }

    public static void sortAndEscape(List<String> candidates, EscapeSelector selector) {
        Collections.sort(candidates);
        final String common = Util.getCommonStart(candidates);
        if (common != null) {
            final String escapedCommon = Util.escapeString(common, selector);
            if (common.length() != escapedCommon.length()) {
                for (int i = 0; i < candidates.size(); ++i) {
                    candidates.set(i, escapedCommon + candidates.get(i).substring(common.length()));
                }
            }
        }
    }

    public static void setRequestProperty(ModelNode request, String name, String value) {
        if(name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("The argument name is not specified: '" + name + "'");
        if(value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("The argument value is not specified: '" + value + "'");
        ModelNode toSet = null;
        try {
            toSet = ModelNode.fromString(value);
        } catch (Exception e) {
            // just use the string
            toSet = new ModelNode().set(value);
        }
        request.get(name).set(toSet);
    }

    public static ModelNode buildRequest(CommandContext ctx, final OperationRequestAddress address, String operation)
            throws CommandFormatException {
        final ModelNode request = new ModelNode();
        request.get(Util.OPERATION).set(operation);
        final ModelNode addressNode = request.get(Util.ADDRESS);
        if (address.isEmpty()) {
            addressNode.setEmptyList();
        } else {
            if(address.endsOnType()) {
                throw new CommandFormatException("The address ends on a type: " + address.getNodeType());
            }
            for(OperationRequestAddress.Node node : address) {
                addressNode.add(node.getType(), node.getName());
            }
        }
        return request;
    }

    public static ModelNode getRolloutPlan(ModelControllerClient client, String name) throws CommandFormatException {
        final ModelNode request = new ModelNode();
        request.get(OPERATION).set(READ_ATTRIBUTE);
        final ModelNode addr = request.get(ADDRESS);
        addr.add(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS);
        addr.add(ROLLOUT_PLAN, name);
        request.get(NAME).set(CONTENT);
        final ModelNode response;
        try {
            response = client.execute(request);
        } catch(IOException e) {
            throw new CommandFormatException("Failed to execute request: " + e.getMessage(), e);
        }
        if(!response.hasDefined(OUTCOME)) {
            throw new CommandFormatException("Operation response if missing outcome: " + response);
        }
        if(!response.get(OUTCOME).asString().equals(SUCCESS)) {
            throw new CommandFormatException("Failed to load rollout plan: " + response);
        }
        if(!response.hasDefined(RESULT)) {
            throw new CommandFormatException("Operation response is missing result.");
        }
        return response.get(RESULT);
    }

    private static final Map<Character,Character> wrappingPairs = new HashMap<Character, Character>();
    static {
        wrappingPairs.put('(', ')');
        wrappingPairs.put('{', '}');
        wrappingPairs.put('[', ']');
        wrappingPairs.put('\"', '\"');
    }

    public static List<String> splitCommands(String line) {

        List<String> commands = null;
        int nextOpIndex = 0;
        Character expectedClosing = null;
        Deque<Character> expectedClosingStack = null;
        int i = 0;
        while(i < line.length()) {
            final char ch = line.charAt(i);
            if(ch == '\\') {
                ++i;//escape
            } else if(expectedClosing != null && expectedClosing == ch) {
                if(expectedClosingStack != null && !expectedClosingStack.isEmpty()) {
                    expectedClosing = expectedClosingStack.pop();
                } else {
                    expectedClosing = null;
                }
            } else {
                final Character matchingClosing = wrappingPairs.get(ch);
                if(matchingClosing != null) {
                    if(expectedClosing == null) {
                        expectedClosing = matchingClosing;
                    } else {
                        if(expectedClosingStack == null) {
                            expectedClosingStack = new ArrayDeque<Character>();
                        }
                        expectedClosingStack.push(expectedClosing);
                        expectedClosing = matchingClosing;
                    }
                } else if(expectedClosing == null && ch == ',') {
                    if(commands == null) {
                        commands = new ArrayList<String>();
                    }
                    commands.add(line.substring(nextOpIndex, i));
                    nextOpIndex = i + 1;
                }
            }
            ++i;
        }

        if(commands == null) {
            commands = Collections.singletonList(line);
        } else {
            commands.add(line.substring(nextOpIndex, i));
        }
        return commands;
    }

    public static byte[] readBytes(File f) throws OperationFormatException {
        byte[] bytes;
        FileInputStream is = null;
        try {
            is = new FileInputStream(f);
            bytes = new byte[(int) f.length()];
            int read = is.read(bytes);
            if(read != bytes.length) {
                throw new OperationFormatException("Failed to read bytes from " + f.getAbsolutePath() + ": " + read + " from " + f.length());
            }
        } catch (Exception e) {
            throw new OperationFormatException("Failed to read file " + f.getAbsolutePath(), e);
        } finally {
            StreamUtils.safeClose(is);
        }
        return bytes;
    }

    public static String getResult(CommandContext cmdCtx, final String cmd) throws CommandSubstitutionException {
        final ModelNode request;
        try {
            request = cmdCtx.buildRequest(cmd);
        } catch(CommandFormatException e) {
            throw new CommandSubstitutionException(cmd, "Failed to substitute " + cmd, e);
        }
        final ModelControllerClient client = cmdCtx.getModelControllerClient();
        if(client == null) {
            throw new CommandSubstitutionException(cmd, "Substitution of " + cmd +
                    " requires connection to the controller.");
        }
        final ModelNode response;
        try {
            response = client.execute(request);
        } catch (IOException e) {
            throw new CommandSubstitutionException(cmd, "Failed to substitute " + cmd, e);
        }
        if(!Util.isSuccess(response)) {
            throw new CommandSubstitutionException(cmd, "Failed to substitute " + cmd +
                    ": " + Util.getFailureDescription(response));
        }
        return response.get(Util.RESULT).asString();
    }

    public static ModelNode toOperationRequest(CommandContext ctx,
            ParsedCommandLine parsedLine, Attachments attachments)
            throws CommandFormatException {
        return toOperationRequest(ctx, parsedLine, attachments, true);
    }

    public static ModelNode toOperationRequest(CommandContext ctx,
            ParsedCommandLine parsedLine)
            throws CommandFormatException {
        return toOperationRequest(ctx, parsedLine, new Attachments(), false);
    }

    private static ModelNode toOperationRequest(CommandContext ctx,
            ParsedCommandLine parsedLine, Attachments attachments,
            boolean description)
            throws CommandFormatException {
        if (parsedLine.getFormat() != OperationFormat.INSTANCE) {
            throw new OperationFormatException("The line does not follow the operation request format");
        }
        ModelNode request = new ModelNode();
        ModelNode addressNode = request.get(Util.ADDRESS);
        if(parsedLine.getAddress().isEmpty()) {
            addressNode.setEmptyList();
        } else {
            Iterator<Node> iterator = parsedLine.getAddress().iterator();
            while (iterator.hasNext()) {
                OperationRequestAddress.Node node = iterator.next();
                if (node.getName() != null) {
                    addressNode.add(node.getType(), node.getName());
                } else if (iterator.hasNext()) {
                    throw new OperationFormatException(
                            "The node name is not specified for type '"
                            + node.getType() + "'");
                }
            }
        }

        final String operationName = parsedLine.getOperationName();
        if(operationName == null || operationName.isEmpty()) {
            throw new OperationFormatException("The operation name is missing or the format of the operation request is wrong.");
        }
        request.get(Util.OPERATION).set(operationName);
        ModelNode outcome = null;
        if (description) {
            outcome = (ModelNode) ctx.get(Scope.REQUEST, DESCRIPTION_RESPONSE);
            if (outcome == null) {
                outcome = retrieveDescription(ctx, request, false);
                if (outcome != null) {
                    ctx.set(Scope.REQUEST, DESCRIPTION_RESPONSE, outcome);
                }
            }
            if (outcome != null) {
                if (!outcome.has(Util.RESULT)) {
                    throw new CommandFormatException("Failed to perform " + Util.READ_OPERATION_DESCRIPTION
                            + " to validate the request: result is not available.");
                } else {
                    outcome = outcome.get(Util.RESULT).get(Util.REQUEST_PROPERTIES);
                }
            }
        }

        for (String propName : parsedLine.getPropertyNames()) {
            String value = parsedLine.getPropertyValue(propName);
            if(propName == null || propName.trim().isEmpty())
                throw new OperationFormatException("The argument name is not specified: '" + propName + "'");
            if (value == null || value.trim().isEmpty()) {
                throw new OperationFormatException("The argument value is not specified for " + propName + ": '" + value + "'");
            }
            final ModelNode toSet = ArgumentValueConverter.DEFAULT.fromString(ctx, value);
            if (outcome != null && outcome.hasDefined(propName)) {
                try {
                    ModelNode p = outcome.get(propName);
                    if (p.hasDefined("type")) {
                        applyReplacements(ctx, propName, toSet, p, p.get("type").asType(), attachments);
                    }
                } catch (Throwable ex) {
                    //ex.printStackTrace();
                    //System.err.println("OUTCOME " + outcome);
                    //System.err.println("FAILED " + ex);
                    //System.err.println("FAULTY " + propName + " " + outcome.get(propName));
                    throw ex;
                }
            }
            request.get(propName).set(toSet);
        }

        if(parsedLine.getLastHeaderName() != null) {
            throw new OperationFormatException("Header '" + parsedLine.getLastHeaderName() + "' is not complete.");
        }
        final Collection<ParsedOperationRequestHeader> headers = parsedLine.getHeaders();
        if(headers != null && !headers.isEmpty()) {
            final ModelNode headersNode = request.get(Util.OPERATION_HEADERS);
            for(ParsedOperationRequestHeader header : headers) {
                header.addTo(ctx, headersNode);
            }
        }
        return request;
    }

    public static String getMessagesFromThrowable(Throwable t) {
        final StringBuilder buf = new StringBuilder();

        if (t.getLocalizedMessage() != null) {
            buf.append(t.getLocalizedMessage());
        } else {
            buf.append(t.getClass().getName());
        }

        boolean encodingSeen = false;
        if (t instanceof CharacterCodingException) {
            encodingSeen = true;
            buf.append(": " + ENCODING_EXCEPTION_MESSAGE);
        }

        Throwable t1 = t.getCause();
        while (t1 != null) {
            if (t1.getLocalizedMessage() != null) {
                buf.append(": ").append(t1.getLocalizedMessage());
            } else {
                buf.append(": ").append(t1.getClass().getName());
            }
            if (!encodingSeen && t1 instanceof CharacterCodingException) {
                encodingSeen = true;
                buf.append(": " + ENCODING_EXCEPTION_MESSAGE);
            }
            t1 = t1.getCause();
        }

        // Add the suppressed exceptions attached to the passed exception.
        // suppressed exceptions can contain valuable information to help trace
        // exception path.
        for (Throwable suppressed : t.getSuppressed()) {
            if (suppressed.getLocalizedMessage() != null) {
                buf.append("\n").append(suppressed.getLocalizedMessage());
            } else {
                buf.append("\n").append(suppressed.getClass().getName());
            }
        }
        return buf.toString();
    }

    private static ModelNode retrieveDescription(CommandContext ctx,
            ModelNode request, boolean strict) throws CommandFormatException {
        final ModelControllerClient client = ctx.getModelControllerClient();
        if (client == null) {
            throw new CommandFormatException("No connection to the controller.");
        }

        final Set<String> keys = request.keys();

        if (!keys.contains(Util.OPERATION)) {
            throw new CommandFormatException("Request is missing the operation name.");
        }
        final String operationName = request.get(Util.OPERATION).asString();

        if (!keys.contains(Util.ADDRESS)) {
            throw new CommandFormatException("Request is missing the address part.");
        }
        final ModelNode address = request.get(Util.ADDRESS);

        final ModelNode opDescrReq = new ModelNode();
        opDescrReq.get(Util.ADDRESS).set(address);
        opDescrReq.get(Util.OPERATION).set(Util.READ_OPERATION_DESCRIPTION);
        opDescrReq.get(Util.NAME).set(operationName);

        final ModelNode outcome;
        try {
            outcome = client.execute(opDescrReq);
        } catch (Exception e) {
            throw new CommandFormatException("Failed to perform " + Util.READ_OPERATION_DESCRIPTION, e);
        }
        if (!Util.isSuccess(outcome)) {
            if(strict) {
                throw new CommandFormatException("Failed to get the list of the operation properties: \"" + Util.getFailureDescription(outcome) + '\"');
            } else {
                return null;
            }
        }
        return outcome;
    }

    // returns the READ_OPERATION_DESCRIPTION outcome used to validate the request params
    // return null if the operation has no params to validate
    public static ModelNode validateRequest(CommandContext ctx, ModelNode request) throws CommandFormatException {

        final Set<String> keys = request.keys();
        if (keys.size() == 2) { // no props
            return null;
        }
        ModelNode outcome = (ModelNode) ctx.get(Scope.REQUEST, DESCRIPTION_RESPONSE);
        if (outcome == null) {
            outcome = retrieveDescription(ctx, request, true);
            if (outcome == null) {
                return null;
            } else {
                ctx.set(Scope.REQUEST, DESCRIPTION_RESPONSE, outcome);
            }
        }
        if(!outcome.has(Util.RESULT)) {
            throw new CommandFormatException("Failed to perform " + Util.READ_OPERATION_DESCRIPTION + " to validate the request: result is not available.");
        }

        final String operationName = request.get(Util.OPERATION).asString();

        final ModelNode result = outcome.get(Util.RESULT);
        final Set<String> definedProps = result.hasDefined(Util.REQUEST_PROPERTIES) ? result.get(Util.REQUEST_PROPERTIES).keys() : Collections.emptySet();
        if(definedProps.isEmpty()) {
            if(!(keys.size() == 3 && keys.contains(Util.OPERATION_HEADERS))) {
                throw new CommandFormatException("Operation '" + operationName + "' does not expect any property.");
            }
        } else {
            int skipped = 0;
            for(String prop : keys) {
                if(skipped < 2 && (prop.equals(Util.ADDRESS) || prop.equals(Util.OPERATION))) {
                    ++skipped;
                    continue;
                }
                if(!definedProps.contains(prop)) {
                    if(!Util.OPERATION_HEADERS.equals(prop)) {
                        throw new CommandFormatException("'" + prop + "' is not found among the supported properties: " + definedProps);
                    }
                }
            }
        }
        return outcome;
    }

    /**
     * Reconnect the context if the RedirectException is valid.
     */
    public static boolean reconnectContext(RedirectException re, CommandContext ctx) {
        boolean reconnected = false;
        try {
            ConnectionInfo info = ctx.getConnectionInfo();
            ControllerAddress address = null;
            if (info != null) {
                address = info.getControllerAddress();
            }
            if (address != null && isHttpsRedirect(re, address.getProtocol())) {
                LOG.debug("Trying to reconnect an http to http upgrade");
                try {
                    ctx.connectController();
                    reconnected = true;
                } catch (Exception ex) {
                    LOG.warn("Exception reconnecting", ex);
                    // Proper https redirect but error.
                    // Ignoring it.
                }
            }
        } catch (URISyntaxException ex) {
            LOG.warn("Invalid URI: ", ex);
            // OK, invalid redirect.
        }
        return reconnected;
    }

    public static boolean isHttpsRedirect(RedirectException re, String scheme) throws URISyntaxException {
        URI location = new URI(re.getLocation());
        return ("remote+http".equals(scheme)
                || "http-remoting".equals(scheme)) && "https".equals(location.getScheme());
    }

    // For any request params that are of type BYTES, replace the file path with the bytes from the file
    public static void replaceFilePathsWithBytes(ModelNode request, ModelNode opDescOutcome) throws CommandFormatException {
        ModelNode requestProps = opDescOutcome.get("result", "request-properties");
        for (Property prop : requestProps.asPropertyList()) {
            ModelNode typeDesc = prop.getValue().get("type");
            if (typeDesc.getType() == ModelType.TYPE && typeDesc.asType() == ModelType.BYTES
                    && request.hasDefined(prop.getName())) {
                String filePath = request.get(prop.getName()).asString();
                File localFile = new File(filePath);
                if (!localFile.exists())
                    continue;
                try {
                    request.get(prop.getName()).set(Util.readBytes(localFile));
                } catch (OperationFormatException e) {
                    throw new CommandFormatException(e);
                }
            }
        }
    }

    static void applyReplacements(CommandContext ctx, String name, ModelNode value,
            ModelNode description, ModelType mt, Attachments attachments) {
        if (value == null || !value.isDefined()) {
            return;
        }
        switch (mt) {
            case INT:
                // Server side can accept invalid content.
                if (!value.getType().equals(ModelType.STRING)) {
                    break;
                }
                if (isFileAttachment(description)) {
                    FilenameTabCompleter completer = FilenameTabCompleter.newCompleter(ctx);
                    value.set(attachments.addFileAttachment(completer.
                            translatePath(value.asString())));
                }
                break;
            case LIST: {
                // Server side can accept invalid content.
                if (!mt.equals(value.getType())) {
                    break;
                }
                ModelNode valueType = description.get("value-type");
                if (valueType.isDefined()) {
                    ModelType valueTypeType = valueType.getType();
                    // of Objects
                    if (ModelType.OBJECT.equals(valueTypeType)) {
                        for (int i = 0; i < value.asInt(); i++) {
                            applyReplacements(ctx, "value-type", value.get(i),
                                    valueType, ModelType.OBJECT, attachments);
                        }
                    // of INT
                    } else if (ModelType.INT.equals(valueType.asType())) {
                        if (isFileAttachment(description)) {
                            FilenameTabCompleter completer = FilenameTabCompleter.newCompleter(ctx);
                            for (int i = 0; i < value.asInt(); i++) {
                                value.get(i).set(attachments.addFileAttachment(completer.
                                        translatePath(value.get(i).asString())));
                            }
                        }

                    }
                }
                break;
            }
            case OBJECT: {
                // Server side can accept invalid content.
                if (!mt.equals(value.getType())) {
                    break;
                }
                ModelNode valueType = description.get("value-type");
                // This is a value-type value, use the description
                if (!valueType.isDefined()) {
                    valueType = description;
                }

                // If valueTypeType is an OBJECT, then we can consult the value-type.
                // If valueTypeType is not an OBJECT, then we are facing a Map<String, X>
                // where X is indicated by the valueTypeType enum value
                ModelType valueTypeType = valueType.getType();
                if (ModelType.OBJECT.equals(valueTypeType)) {
                    for (String k : value.keys()) {
                        if (value.get(k).isDefined() && valueType.hasDefined(k)) {
                            ModelNode p = valueType.get(k);
                            if (p.hasDefined("type")) {
                                applyReplacements(ctx, k, value.get(k), p,
                                        p.get("type").asType(), attachments);
                            }
                        }
                    }
                }
                break;
            }
            default:
        }
    }

    private static boolean isFileAttachment(ModelNode mn) {
        return mn.has(FILESYSTEM_PATH)
                && mn.get(FILESYSTEM_PATH).asBoolean()
                && mn.has(ATTACHED_STREAMS)
                && mn.get(ATTACHED_STREAMS).asBoolean();
    }

    public static String getRunningMode(CommandContext ctx) throws IOException, OperationFormatException {
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        builder.setOperationName(Util.READ_ATTRIBUTE);
        builder.addProperty(Util.NAME, Util.RUNNING_MODE);
        ModelNode response = ctx.getModelControllerClient().execute(builder.buildRequest());
        if (isSuccess(response)) {
            return response.get(Util.RESULT).asString();
        } else {
            return null;
        }
    }

    public static List<String> getUndertowSecurityDomains(ModelControllerClient client) {

        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        final ModelNode request;
        try {
            builder.setOperationName(Util.READ_CHILDREN_NAMES);
            builder.addNode(Util.SUBSYSTEM, Util.UNDERTOW);
            builder.addProperty(Util.CHILD_TYPE, APPLICATION_SECURITY_DOMAIN);
            request = builder.buildRequest();
        } catch (OperationFormatException e) {
            throw new IllegalStateException("Failed to build operation", e);
        }

        try {
            final ModelNode outcome = client.execute(request);
            if (isSuccess(outcome)) {
                return getList(outcome);
            }
        } catch (Exception e) {
        }

        return Collections.emptyList();
    }

    /**
     * Build a compact representation of the ModelNode.
     * @param node The model
     * @return A single line containing the multi lines ModelNode.toString() content.
     */
    public static String compactToString(ModelNode node) {
        Objects.requireNonNull(node);
        final StringWriter stringWriter = new StringWriter();
        final PrintWriter writer = new PrintWriter(stringWriter, true);
        node.writeString(writer, true);
        return stringWriter.toString();
    }

}
