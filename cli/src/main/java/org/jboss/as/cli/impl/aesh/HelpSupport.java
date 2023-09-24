/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh;

import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import org.aesh.terminal.utils.Config;
import org.aesh.command.option.Argument;
import org.aesh.command.CommandDefinition;
import org.aesh.command.GroupCommandDefinition;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.aesh.command.impl.internal.ProcessedCommandBuilder;
import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.impl.internal.ProcessedOptionBuilder;
import org.aesh.command.impl.parser.CommandLineParser;
import org.aesh.command.Command;
import org.aesh.command.option.Arguments;
import org.aesh.command.parser.CommandLineParserException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.logging.Logger;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.activator.DomainOptionActivator;
import org.wildfly.core.cli.command.aesh.activator.StandaloneOptionActivator;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Entry point for help handling. Aesh based commands have their help generated.
 * For legacy command, static help text files are used.
 *
 * @author jdenise@redhat.com
 */
public class HelpSupport {

    private static final String TAB = "    ";
    private static final String OPTION_PREFIX = "--";
    private static final String OPTION_SUFFIX = "  - ";

    public static final String NULL_DESCRIPTION = "WARNING: No Description. Please Fix it";

    public static void printHelp(CommandContext ctx) {
        ctx.printLine(printHelp(ctx, getLegacyHelpPath("help")));
    }

    public static String printHelp(CommandContext ctx, String filename) {
        InputStream helpInput = WildFlySecurityManager.getClassLoaderPrivileged(CommandHandlerWithHelp.class).getResourceAsStream(filename);
        if (helpInput != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(helpInput));
            try {
                /*                String helpLine = reader.readLine();
                while(helpLine != null) {
                    ctx.printLine(helpLine);
                    helpLine = reader.readLine();
                }
                 */
                return format(ctx, reader);
            } catch (java.io.IOException e) {
                return "Failed to read " + filename + ". " + e.getLocalizedMessage();
            } finally {
                StreamUtils.safeClose(reader);
            }
        } else {
            return "Failed to locate command description " + filename;
        }
    }

    public static String getLegacyHelpPath(String commandName) {
        return "help/" + commandName + ".txt";
    }

    public static String format(CommandContext ctx, BufferedReader reader) throws IOException {

        StringBuilder builder = new StringBuilder();
        int width = ctx.getTerminalWidth();
        if (width <= 0) {
            width = 80;
        }
        String line = reader.readLine();

        while (line != null) {
            final String next = reader.readLine();

            if (line.length() < width) {
                builder.append(line).append("\n");
            } else {
                int offset = 0;
                if (next != null && !next.isEmpty()) {
                    int i = 0;
                    while (i < next.length()) {
                        if (!Character.isWhitespace(next.charAt(i))) {
                            offset = i;
                            break;
                        }
                        ++i;
                    }
                } else {
                    int i = 0;
                    while (i < line.length()) {
                        if (!Character.isWhitespace(line.charAt(i))) {
                            offset = i;
                            break;
                        }
                        ++i;
                    }
                }

                final char[] offsetArr;
                if (offset == 0) {
                    offsetArr = null;
                } else {
                    offsetArr = new char[offset];
                    Arrays.fill(offsetArr, ' ');
                }

                int endLine = width;
                while (endLine >= 0) {
                    if (Character.isWhitespace(line.charAt(endLine - 1))) {
                        break;
                    }
                    --endLine;
                }
                if (endLine < 0) {
                    endLine = width;
                }

                builder.append(line.substring(0, endLine)).append("\n");

                int lineIndex = endLine;
                while (lineIndex < line.length()) {
                    int startLine = lineIndex;
                    endLine = Math.min(startLine + width - offset, line.length());

                    while (startLine < endLine) {
                        if (!Character.isWhitespace(line.charAt(startLine))) {
                            break;
                        }
                        ++startLine;
                    }
                    if (startLine == endLine) {
                        startLine = lineIndex;
                    }

                    endLine = startLine + width - offset;
                    if (endLine > line.length()) {
                        endLine = line.length();
                    } else {
                        while (endLine > startLine) {
                            if (Character.isWhitespace(line.charAt(endLine - 1))) {
                                --endLine;
                                break;
                            }
                            --endLine;
                        }
                        if (endLine == startLine) {
                            endLine = Math.min(startLine + width - offset, line.length());
                        }
                    }
                    lineIndex = endLine;

                    if (offsetArr != null) {
                        final StringBuilder lineBuf = new StringBuilder();
                        lineBuf.append(offsetArr);
                        lineBuf.append(line.substring(startLine, endLine));
                        builder.append(lineBuf.toString()).append("\n");
                    } else {
                        builder.append(line.substring(startLine, endLine)).append("\n");
                    }
                }
            }

            line = next;
        }
        return builder.toString();
    }

    static final Map<Class<?>, String> VALUES = new HashMap<>();

    static {
        VALUES.put(BigDecimal.class, "a big decimal");
        VALUES.put(Boolean.class, "true|false");
        VALUES.put(BigInteger.class, "a big integer");
        VALUES.put(byte[].class, "bytes array");
        VALUES.put(Double.class, "a double");
        VALUES.put(Expression.class, "an expression");
        VALUES.put(Integer.class, "an integer");
        VALUES.put(List.class, "a list");
        VALUES.put(Long.class, "a long");
        VALUES.put(Object.class, "an object");
        VALUES.put(Property.class, "a property");
        VALUES.put(String.class, "a string");
        VALUES.put(ModelType.class, "a type");
    }

    private static class Expression {

    }

    public static Class<?> getClassFromType(ModelType mt) {
        Class<?> clazz;
        switch (mt) {
            case BIG_DECIMAL: {
                clazz = BigDecimal.class;
                break;
            }
            case BOOLEAN: {
                clazz = Boolean.class;
                break;
            }
            case BIG_INTEGER: {
                clazz = BigInteger.class;
                break;
            }
            case BYTES: {
                clazz = byte[].class;
                break;
            }
            case DOUBLE: {
                clazz = Double.class;
                break;
            }
            case EXPRESSION: {
                clazz = Expression.class;
                break;
            }
            case INT: {
                clazz = Integer.class;
                break;
            }
            case LIST: {
                clazz = List.class;
                break;
            }
            case LONG: {
                clazz = Long.class;
                break;
            }
            case OBJECT: {
                clazz = Object.class;
                break;
            }
            case PROPERTY: {
                clazz = Property.class;
                break;
            }
            case STRING: {
                clazz = String.class;
                break;
            }
            case TYPE: {
                clazz = ModelType.class;
                break;
            }
            default: {
                clazz = String.class;
                break;
            }
        }
        return clazz;
    }

    public static String printHelp(CommandContext ctx, ModelNode mn,
            OperationRequestAddress address) {
        try {
            // Build a ProcessedCommand from the ModelNode
            String commandName = mn.get("operation-name").asString();
            String desc = mn.get(Util.DESCRIPTION).asString();
            desc += Config.getLineSeparator() + Config.getLineSeparator()
                    + "NB: to retrieve operation full description call the following operation: "
                    + buildAddress(address) + ":"
                    + Util.READ_OPERATION_DESCRIPTION + "(name=" + commandName + ")";
            ModelNode props = mn.get(Util.REQUEST_PROPERTIES);
            ProcessedCommand pcommand = ProcessedCommandBuilder.builder().
                    name(commandName).description(desc).create();
            for (String prop : props.keys()) {
                ModelNode p = props.get(prop);
                Class<?> clazz = getClassFromType(getAdaptedArgumentType(p));
                boolean required = p.hasDefined(Util.REQUIRED) ? p.get(Util.REQUIRED).asBoolean() : false;
                ProcessedOption opt = ProcessedOptionBuilder.builder().name(prop).
                        required(required).
                        hasValue(true).
                        description(buildOperationArgumentDescription(p)).
                        type(clazz).build();
                pcommand.addOption(opt);
            }

            String content = getCommandHelp(null, Collections.emptyList(), null, Collections.emptyList(),
                    pcommand.getOptions(), pcommand,
                    null, commandName, pcommand, true);
            if (mn.hasDefined("reply-properties")) {
                ModelNode reply = mn.get("reply-properties");
                // Add response value
                StringBuilder builder = new StringBuilder();
                builder.append(content);

                builder.append("RETURN VALUE");

                builder.append(Config.getLineSeparator());
                builder.append(Config.getLineSeparator());

                if (reply.hasDefined("type")) {
                    builder.append(reply.get("type").asString()).append(". ");
                }
                if (reply.hasDefined("description")) {
                    builder.append(reply.get("description").asString());
                }
                builder.append(Config.getLineSeparator());
                builder.append(Config.getLineSeparator());
                content = builder.toString();
            }
            return content;
        } catch (Exception ex) {
            // XXX OK.
            return null;
        }
    }

    private static String buildOperationArgumentDescription(ModelNode p) {
        StringBuilder builder = new StringBuilder();
        builder.append(buildOperationArgumentType(p)).append(", ");
        builder.append(p.get(Util.DESCRIPTION).asString());
        if (p.hasDefined(Util.VALUE_TYPE)) {
            boolean isList = p.get(Util.TYPE).asType() == ModelType.LIST;
            if (isList) {
                builder.append(" List items are ");
            }
            ModelNode vt = p.get(Util.VALUE_TYPE);
            if (isObject(vt)) {
                if (isList) {
                    builder.append("OBJECT instances with the following properties:").
                            append(Config.getLineSeparator());
                } else {
                    builder.append("OBJECT properties:").append(Config.getLineSeparator());
                }
                for (String prop : vt.keys()) {
                    ModelNode mn = vt.get(prop);
                    builder.append(Config.getLineSeparator()).append("- ").
                            append(prop).append(": ").
                            append(buildOperationArgumentType(mn)).append(", ").
                            append(getAdaptedArgumentDescription(mn));
                    builder.append(Config.getLineSeparator());
                }
            } else {
                builder.append(vt.asType());
            }
        }
        return builder.toString();
    }

    private static String buildAddress(OperationRequestAddress address) {
        StringBuilder builder = new StringBuilder();
        if (address != null && !address.isEmpty()) {
            for (OperationRequestAddress.Node node : address) {
                builder.append("/" + node.getType() + "=" + node.getName());
            }
        }
        return builder.toString();
    }

    private static String buildOperationArgumentType(ModelNode p) {
        StringBuilder builder = new StringBuilder();
        ModelType mt = getAdaptedArgumentType(p);
        boolean isList = mt == ModelType.LIST;
        builder.append(mt);
        boolean isObject = false;
        if (isList) {
            String t = null;
            if (p.hasDefined(Util.VALUE_TYPE)) {
                ModelNode vt = p.get(Util.VALUE_TYPE);
                isObject = isObject(vt);
            }
            if (isObject) {
                t = "OBJECT";
            } else {
                t = p.get(Util.VALUE_TYPE).asType().name();
            }
            builder.append(" of ").append(t);
        }
        return builder.toString();
    }

    private static boolean isObject(ModelNode vt) {
        try {
            vt.asType();
        } catch (Exception ex) {
            return true;
        }
        return false;
    }

    private static ModelType getAdaptedArgumentType(ModelNode mn) {
        ModelType type = mn.get(Util.TYPE).asType();
        if (mn.hasDefined(Util.FILESYSTEM_PATH) && mn.hasDefined(Util.ATTACHED_STREAMS)) {
            type = ModelType.STRING;
        }
        return type;
    }

    private static String getAdaptedArgumentDescription(ModelNode mn) {
        String desc = mn.get(Util.DESCRIPTION).asString();
        if (mn.hasDefined(Util.FILESYSTEM_PATH) && mn.hasDefined(Util.ATTACHED_STREAMS)) {
            desc = "The path to the file to attach."
                    + " The CLI deals directly with file paths and doesn't "
                    + "require index manipulation." + Config.getLineSeparator()
                    + "NB: The actual argument type is "
                    + mn.get(Util.TYPE).asType();
        }
        return desc;
    }

    public static String getSubCommandHelp(String parentCommand,
            CommandLineParser<CLICommandInvocation> parser) {
        String commandName = parser.getProcessedCommand().name();
        return getCommandHelp(parentCommand, commandName, parser);
    }

    public static String getCommandHelp(CommandLineParser<CLICommandInvocation> parser) {
        String commandName = parser.getProcessedCommand().name();
        return getCommandHelp(null, commandName, parser);
    }

    private static String getCommandHelp(String parentName, String commandName,
            CommandLineParser<CLICommandInvocation> parser) {

        // First retrieve deprecated options.
        Set<String> deprecated = new HashSet<>();
        // All inherited names (used to resolve bndle keys)
        List<String> superNames = new ArrayList<>();
        retrieveDeprecated(deprecated, parser.getCommand().getClass(), superNames);
        retrieveHidden(deprecated, parser.getProcessedCommand());

        List<CommandLineParser<CLICommandInvocation>> parsers = parser.getAllChildParsers();

        ResourceBundle bundle = getBundle(parser.getCommand());

        ProcessedCommand<?, ?> pcommand = retrieveDescriptions(bundle, parentName,
                parser.getProcessedCommand(), superNames, deprecated);

        List<ProcessedOption> opts = new ArrayList<>();
        for (ProcessedOption o : pcommand.getOptions()) {
            if (!deprecated.contains(o.name())) {
                opts.add(o);
            }
        }
        Collections.sort(opts, (ProcessedOption o1, ProcessedOption o2) -> {
            return o1.name().compareTo(o2.name());
        });
        ProcessedOption arg = deprecated.contains("") ? null
                : (pcommand.getArgument() == null ? pcommand.getArguments() : pcommand.getArgument());

        return getCommandHelp(bundle, superNames, arg, parsers, opts, pcommand,
                parentName, commandName, parser.getProcessedCommand(), false);
    }

    private static String getCommandHelp(ResourceBundle bundle, List<String> superNames,
            ProcessedOption arg,
            List<CommandLineParser<CLICommandInvocation>> parsers,
            List<ProcessedOption> opts,
            ProcessedCommand<?, ?> pcommand,
            String parentName,
            String commandName,
            ProcessedCommand<?, ?> origCommand, boolean isOperation) {
        StringBuilder builder = new StringBuilder();
        builder.append(Config.getLineSeparator());

        // Compute synopsis.
        builder.append("SYNOPSIS").append(Config.getLineSeparator());
        builder.append(Config.getLineSeparator());
        String synopsis = getValue(bundle, parentName, commandName, superNames, "synopsis", true);
        //Synopsis option tab
        StringBuilder tabBuilder = new StringBuilder();
        if (parentName != null) {
            tabBuilder.append(parentName).append(" ");
        }
        tabBuilder.append(commandName).append(" ");
        StringBuilder synopsisTab = new StringBuilder();
        for (int i = 0; i < tabBuilder.toString().length() + TAB.length(); i++) {
            synopsisTab.append(" ");
        }
        if (synopsis == null) {
            // 2 cases, no standAlone nor Domain only opts or standalone and/or domain only
            List<ProcessedOption> standalone = retrieveNoContextOptions(opts);
            if (standalone.size() == opts.size()
                    && (arg == null || !(arg.activator() instanceof DomainOptionActivator))) {
                synopsis = generateSynopsis(bundle, parentName, commandName, opts,
                        arg, parsers != null && !parsers.isEmpty(), superNames, isOperation, false);
                builder.append(splitAndFormat(synopsis, 80, TAB, 0, synopsisTab.toString()));
            } else {
                List<ProcessedOption> standaloneOnly = retrieveStandaloneOptions(opts);
                builder.append("Standalone mode:").append(Config.getLineSeparator());
                builder.append(Config.getLineSeparator());
                synopsis = generateSynopsis(bundle, parentName, commandName, standaloneOnly,
                        arg, parsers != null && !parsers.isEmpty(), superNames, isOperation, false);
                builder.append(splitAndFormat(synopsis, 80, TAB, 0, synopsisTab.toString()));
                builder.append(Config.getLineSeparator());
                builder.append(Config.getLineSeparator());
                builder.append("Domain mode:").append(Config.getLineSeparator());
                builder.append(Config.getLineSeparator());
                List<ProcessedOption> domain = retrieveDomainOptions(opts);
                synopsis = generateSynopsis(bundle, parentName, commandName, domain,
                        arg, parsers != null && !parsers.isEmpty(), superNames, isOperation, true);
                builder.append(splitAndFormat(synopsis, 80, TAB, 0, synopsisTab.toString()));
            }
        } else {
            builder.append(splitAndFormat(synopsis, 80, TAB, 0, synopsisTab.toString()));
        }
        builder.append(Config.getLineSeparator());
        builder.append(Config.getLineSeparator());
        builder.append("DESCRIPTION").append(Config.getLineSeparator());
        builder.append(Config.getLineSeparator());
        builder.append(HelpSupport.splitAndFormat(pcommand.description(), 80, TAB, 0, TAB));
        builder.append(Config.getLineSeparator());

        if (origCommand.getAliases() != null
                && !origCommand.getAliases().isEmpty()) {
            builder.append("ALIASES").append(Config.getLineSeparator());
            builder.append(Config.getLineSeparator());
            for (String a : origCommand.getAliases()) {
                builder.append(HelpSupport.TAB);
                builder.append(a).append(Config.getLineSeparator());
            }
            builder.append(Config.getLineSeparator());
        }

        builder.append(printOptions(opts,
                arg, isOperation)).
                append(Config.getLineSeparator());

        // Sub Commands
        builder.append(printActions(bundle, parentName, commandName, parsers,
                superNames));
        return builder.toString();
    }

    private static String printActions(ResourceBundle bundle,
            String parentName,
            String commandName,
            List<CommandLineParser<CLICommandInvocation>> parsers,
            List<String> superNames) {
        StringBuilder builder = new StringBuilder();
        if (parsers != null && !parsers.isEmpty()) {
            builder.append("ACTIONS")
                    .append(Config.getLineSeparator()).
                    append(Config.getLineSeparator());
            builder.append("Type \"help ").append(commandName).append(" <action>\" for more details.")
                    .append(Config.getLineSeparator()).
                    append(Config.getLineSeparator());
            List<ProcessedCommand> actions = new ArrayList<>();
            for (CommandLineParser child : parsers) {
                ResourceBundle childBundle = getBundle(child.getCommand());
                ProcessedCommandBuilder pcBuilder = retrieveDescriptionBuilder(childBundle, commandName,
                        child.getProcessedCommand(), superNames);
                if (pcBuilder == null) {
                    actions.add(child.getProcessedCommand());
                } else {
                    try {
                        actions.add(pcBuilder.create());
                    } catch (CommandLineParserException ex) {
                        Logger.getLogger(HelpSupport.class).warn("Error building description " + ex);
                        if (testMode) {
                            throw new RuntimeException(ex);
                        }
                        // fallback to ProcessedCommand.
                    }
                }
            }
            // Retrieve the tab length
            int maxActionName = 0;
            for (ProcessedCommand pc : actions) {
                String name = createActionName(pc.name(), pc.name().length());
                if (name.length() > maxActionName) {
                    maxActionName = name.length();
                }
            }
            StringBuilder tabBuilder = new StringBuilder();
            for (int i = 0; i < maxActionName; i++) {
                tabBuilder.append(" ");
            }
            String tab = tabBuilder.toString();
            Collections.sort(actions, (o1, o2) -> o1.name().compareTo(o2.name()));
            for (ProcessedCommand pc : actions) {
                String name = createActionName(pc.name(), maxActionName);
                builder.append(name);
                // Extract first line...
                int length = 77 - tab.length();
                String line = extractFirstLine(pc.description(), length);
                builder.append(line).append("...").append(Config.getLineSeparator()).
                        append(Config.getLineSeparator());
            }
        }
        return builder.toString();
    }

    private static void retrieveHidden(Set<String> deprecated, ProcessedCommand<Command<CLICommandInvocation>, CLICommandInvocation> cmd) {
        if ((cmd.getArgument() != null && cmd.getArgument().activator() instanceof HideOptionActivator)
                || (cmd.getArguments() != null && cmd.getArguments().activator() instanceof HideOptionActivator)) {
            deprecated.add("");
        }
        for (ProcessedOption po : cmd.getOptions()) {
            if (po.activator() instanceof HideOptionActivator) {
                deprecated.add(po.name());
            }
        }
    }

    private static void retrieveDeprecated(Set<String> deprecated, Class clazz, List<String> superNames) {
        for (Field field : clazz.getDeclaredFields()) {
            processField(deprecated, field);
        }

        if (clazz.getSuperclass() != null) {
            Class<?> sup = clazz.getSuperclass();
            if (sup.getAnnotation(CommandDefinition.class) != null) {
                CommandDefinition cd = (CommandDefinition) sup.getAnnotation(CommandDefinition.class);
                superNames.add(cd.name());
            }
            if (sup.getAnnotation(GroupCommandDefinition.class) != null) {
                GroupCommandDefinition gcd = (GroupCommandDefinition) sup.getAnnotation(GroupCommandDefinition.class);
                superNames.add(gcd.name());
            }
            retrieveDeprecated(deprecated, sup, superNames);
        }
    }

    private static void processField(Set<String> deprecated, Field field) {
        Deprecated dep;
        if ((dep = field.getAnnotation(Deprecated.class)) != null) {
            Option o;
            if ((o = field.getAnnotation(Option.class)) != null) {
                String name = o.name();
                if (name == null || name.isEmpty()) {
                    name = field.getName();
                }
                deprecated.add(name);
            } else {
                OptionList ol;
                if ((ol = field.getAnnotation(OptionList.class)) != null) {
                    String name = ol.name();
                    if (name == null || name.isEmpty()) {
                        name = field.getName();
                    }
                    deprecated.add(name);
                } else {
                    if (field.getAnnotation(Argument.class) != null || field.getAnnotation(Arguments.class) != null) {
                        deprecated.add("");
                    }
                }
            }
        }
    }

    private static String generateSynopsis(ResourceBundle bundle,
            String parentName,
            String commandName,
            List<ProcessedOption> opts,
            ProcessedOption arg,
            boolean hasActions,
            List<String> superNames,
            boolean isOperation, boolean domain) {
        SynopsisGenerator generator = new SynopsisGenerator(bundle, parentName,
                commandName, opts, arg, hasActions, superNames, isOperation, domain);
        return generator.generateSynopsis();
    }

    private static String printOptions(List<ProcessedOption> opts,
            ProcessedOption arg, boolean isOperation) {
        int width = 80;
        StringBuilder sb = new StringBuilder();
        if (!opts.isEmpty()) {
            sb.append(Config.getLineSeparator()).append("OPTIONS").append(Config.getLineSeparator());
            sb.append(Config.getLineSeparator());
        }
        // Retrieve the tab length
        int maxOptionName = 0;
        for (ProcessedOption o : opts) {
            String name = createOptionName(o.name(), o.name().length(), o.shortName(), isOperation);
            if (name.length() > maxOptionName) {
                maxOptionName = name.length();
            }
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < maxOptionName; i++) {
            builder.append(" ");
        }
        String tab = builder.toString();
        for (ProcessedOption o : opts) {
            String name = createOptionName(o.name(), maxOptionName, o.shortName(), isOperation);
            sb.append(name);
            sb.append(HelpSupport.splitAndFormat(o.description(), width, "", name.length(), tab));
            sb.append(Config.getLineSeparator());
        }
        if (arg != null) {
            sb.append(Config.getLineSeparator()).append("ARGUMENT").append(Config.getLineSeparator());
            sb.append(Config.getLineSeparator());
            sb.append(HelpSupport.splitAndFormat(arg.description(), width, HelpSupport.TAB, 0, HelpSupport.TAB));
        }
        return sb.toString();
    }

    private static String createOptionName(String name, int maxOptionName, String shortName, boolean isOperation) {
        if (shortName != null) {
            name = name + " or (-" + shortName + ")";
        }
        StringBuilder builder = new StringBuilder(name);
        String prefix = isOperation ? "" : OPTION_PREFIX;
        while (builder.length() < (maxOptionName - TAB.length()
                - prefix.length() - OPTION_SUFFIX.length())) {
            builder.append(" ");
        }
        return TAB + prefix + builder.toString() + OPTION_SUFFIX;
    }

    private static String createActionName(String name, int maxOptionName) {
        StringBuilder builder = new StringBuilder(name);
        while (builder.length() < (maxOptionName - TAB.length()
                - OPTION_SUFFIX.length())) {
            builder.append(" ");
        }
        return HelpSupport.TAB + builder.toString() + OPTION_SUFFIX;
    }

    private static ResourceBundle getBundle(Command c) {
        Class<? extends Command> clazz = c.getClass();
        String s = clazz.getPackage().getName() + "." + "command_resources";
        ResourceBundle bundle = null;
        try {
            bundle = ResourceBundle.getBundle(s, Locale.getDefault(),
                    c.getClass().getClassLoader());
        } catch (MissingResourceException ex) {
            // Ok, will fallback on null.
        }
        return bundle;
    }

    static String getValue(ResourceBundle bundle, String parentName,
            String commandName, List<String> superNames, String key, boolean acceptNull) {
        if (bundle == null) {
            return null;
        }
        String value = null;
        try {
            String k = parentName == null ? commandName + "." + key : parentName + "." + commandName
                    + "." + key;
            value = bundle.getString(k);
        } catch (MissingResourceException ex) {
            //OK, try inherited option/arguments
            for (String superName : superNames) {
                try {
                    String k = parentName == null ? superName + "." + key : parentName + "." + superName
                            + "." + key;
                    value = bundle.getString(k);
                } catch (MissingResourceException ex2) {
                    // Ok, no key.
                    continue;
                }
                break;
            }
        }
        if (value != null) { // could be a reference to another option
            if (value.startsWith("${") && value.endsWith("}")) {
                String k = value.substring(2, value.length() - 1);
                try {
                    value = bundle.getString(k);
                } catch (MissingResourceException ex2) {
                    // Ok, missing key
                }
            }
        }
        if (value == null && !acceptNull) {
            if (testMode) {
                throw new RuntimeException("Invalid help for command, no value for property "
                        + (parentName == null ? commandName + "." + key : parentName + "." + commandName
                                + "." + key));
            }
            value = NULL_DESCRIPTION;
        }
        return value;
    }

    private static ProcessedCommandBuilder retrieveDescriptionBuilder(ResourceBundle bundle,
            String parentName,
            ProcessedCommand<?, ?> pc, List<String> superNames) {
        if (bundle == null) {
            if (testMode) {
                throw new RuntimeException("Invalid help for command, no bundle");
            }
            return null;
        }
        String desc = pc.description();
        String bdesc = getValue(bundle, parentName, pc.name(), superNames, "description", false);
        if (bdesc != null) {
            desc = bdesc;
        }
        ProcessedCommandBuilder builder = ProcessedCommandBuilder.builder().name(pc.name()).description(desc);
        return builder;
    }

    private static ProcessedCommand retrieveDescriptions(ResourceBundle bundle,
            String parentName,
            ProcessedCommand<?, ?> pc, List<String> superNames, Set<String> deprecated) {
        try {
            ProcessedCommandBuilder builder = retrieveDescriptionBuilder(bundle, parentName, pc, superNames);
            if (builder == null) {
                return pc;
            }
            if (pc.getArgument() != null && !deprecated.contains(pc.getArgument().name())) {
                String argDesc = pc.getArgument().description();
                String bargDesc = getValue(bundle, parentName, pc.name(), superNames,
                        "arguments.description", false);
                if (bargDesc != null) {
                    argDesc = bargDesc;
                }
                ProcessedOption newArg = ProcessedOptionBuilder.builder().name("").
                        optionType(pc.getArgument().getOptionType()).
                        type(String.class).
                        activator(pc.getArgument().activator()).
                        valueSeparator(pc.getArgument().getValueSeparator()).
                        required(pc.getArgument().isRequired()).description(argDesc).build();
                builder.argument(newArg);
            } else if (pc.getArguments() != null && !deprecated.contains(pc.getArguments().name())) {
                String argDesc = pc.getArguments().description();
                String bargDesc = getValue(bundle, parentName, pc.name(), superNames,
                        "arguments.description", false);
                if (bargDesc != null) {
                    argDesc = bargDesc;
                }
                ProcessedOption newArg = ProcessedOptionBuilder.builder().name("").
                        optionType(pc.getArguments().getOptionType()).
                        type(String.class).
                        activator(pc.getArguments().activator()).
                        valueSeparator(pc.getArguments().getValueSeparator()).
                        required(pc.getArguments().isRequired()).description(argDesc).build();
                builder.argument(newArg);
            }


            for (ProcessedOption opt : pc.getOptions()) {
                if (!deprecated.contains(opt.name())) {
                    String optDesc = opt.description();
                    String boptDesc = getValue(bundle, parentName, pc.name(), superNames,
                            "option." + opt.name() + ".description", false);
                    if (boptDesc != null) {
                        optDesc = boptDesc;
                    }
                    ProcessedOption newOption = ProcessedOptionBuilder.builder().name(opt.name()).
                            optionType(opt.getOptionType()).
                            type(String.class).
                            activator(opt.activator()).
                            valueSeparator(opt.getValueSeparator()).
                            shortName(opt.shortName() == null ? 0 : opt.shortName().charAt(0)).
                            required(opt.isRequired()).
                            description(optDesc).build();
                    builder.addOption(newOption);
                }
            }
            return builder.create();
        } catch (Exception ex) {
            Logger.getLogger(HelpSupport.class).warn("Error building description " + ex);
            if (testMode) {
                throw ex instanceof RuntimeException ? (RuntimeException) ex : new RuntimeException(ex);
            }
            // fallback to ProcessedCommand.
        }
        return pc;
    }

    private static String extractFirstLine(String content, int width) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        if (content.length() <= width) {
            return content;
        }
        String line = null;
        StringBuilder builder = new StringBuilder();
        for (char c : content.toCharArray()) {
            if (c == '\n') {
                line = builder.toString();
                line = removeLastBlanks(line);
                break;
            } else {
                builder.append(c);
                if (builder.length() == width) {
                    line = builder.toString();
                    if (!line.endsWith(" ")) { // Need to truncate after the last ' '
                        line = removeLastBlanks(line);
                        int index = line.lastIndexOf(" ");
                        index = index < 0 ? 0 : index;
                        line = line.substring(0, index);
                        break;
                    } else {
                        line = builder.toString();
                        line = removeLastBlanks(line);
                        break;
                    }
                }
            }
        }
        return line;
    }

    private static String splitAndFormat(String content, int width, String firstTab, int firstOffset, String otherTab) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        if (content.length() <= width - firstTab.length() - firstOffset) {
            return firstTab + content + "\n";
        }
        StringBuilder builder = new StringBuilder();

        StringBuilder b = new StringBuilder();
        boolean first = true;
        for (char c : content.toCharArray()) {
            if (c == '\n') {
                builder.append(first ? firstTab : otherTab);
                String line = b.toString();
                line = removeLastBlanks(line);
                builder.append(line);
                builder.append("\n");
                b = new StringBuilder();
                first = false;
            } else {
                b.append(c);
                String tab = first ? firstTab : otherTab;
                if (b.length() == width - tab.length() - (first ? firstOffset : 0)) {
                    builder.append(tab);
                    String line = b.toString();
                    if (!line.endsWith(" ")) { // Need to truncate after the last ' '
                        line = removeLastBlanks(line);
                        int index = line.lastIndexOf(" ");
                        index = index < 0 ? 0 : index;
                        String remain = line.substring(index);
                        remain = remain.trim();
                        builder.append(line.substring(0, index));
                        builder.append("\n");
                        b = new StringBuilder();
                        b.append(remain);
                        first = false;
                    } else {
                        builder.append(removeLastBlanks(line));
                        builder.append("\n");
                        b = new StringBuilder();
                        first = false;
                    }
                }
            }
        }

        if (b.length() > 0) {
            builder.append(first ? firstTab : otherTab);
            String line = b.toString();
            line = removeLastBlanks(line);
            builder.append(line).append("\n");
        }

        return builder.toString();
    }

    private static String removeLastBlanks(String line) {
        int num = 0;
        for (int i = line.length() - 1; i > 0; i--) {
            if (line.charAt(i) == ' ') {
                num += 1;
            } else {
                break;
            }
        }
        return line.substring(0, line.length() - num);
    }


    private static List<ProcessedOption> retrieveNoContextOptions(List<ProcessedOption> opts) {
        List<ProcessedOption> standalone = new ArrayList<>();
        for (ProcessedOption opt : opts) {
            if (!(opt.activator() instanceof DomainOptionActivator)
                    && !(opt.activator() instanceof StandaloneOptionActivator)) {
                standalone.add(opt);
            }
        }
        return standalone;
    }

    private static List<ProcessedOption> retrieveStandaloneOptions(List<ProcessedOption> opts) {
        List<ProcessedOption> standalone = new ArrayList<>();
        for (ProcessedOption opt : opts) {
            if (!(opt.activator() instanceof DomainOptionActivator)) {
                standalone.add(opt);
            }
        }
        return standalone;
    }

    private static List<ProcessedOption> retrieveDomainOptions(List<ProcessedOption> opts) {
        List<ProcessedOption> domain = new ArrayList<>();
        for (ProcessedOption opt : opts) {
            if ((opt.activator() instanceof DomainOptionActivator
                    || !(opt.activator() instanceof StandaloneOptionActivator))) {
                domain.add(opt);
            }
        }
        return domain;
    }

    private static boolean testMode = false;

    static void testMode(boolean mode) {
        testMode = mode;
    }

    public static void checkCommand(CommandLineParser<CLICommandInvocation> parent,
            CommandLineParser<CLICommandInvocation> child) throws Exception {
        boolean currentMode = testMode;
        testMode(true);
        try {
            String fullHelp = parent == null ? getCommandHelp(child)
                    : getSubCommandHelp(parent.getProcessedCommand().name(), child);
        } finally {
            testMode(currentMode);
        }
    }
}
