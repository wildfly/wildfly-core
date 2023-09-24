/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.module;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.ModuleNameTabCompleter;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;
import org.jboss.as.cli.handlers.DefaultFilenameTabCompleter;
import org.jboss.as.cli.handlers.FilenameTabCompleter;
import org.jboss.as.cli.handlers.WindowsFilenameTabCompleter;
import org.jboss.as.cli.impl.ArgumentWithListValue;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.ArgumentWithoutValue;
import org.jboss.as.cli.impl.DefaultCompleter;
import org.jboss.as.cli.impl.DefaultCompleter.CandidatesProvider;
import org.jboss.as.cli.impl.FileSystemPathArgument;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.parsing.ExpressionBaseState;
import org.jboss.as.cli.parsing.ParsingState;
import org.jboss.as.cli.parsing.WordCharacterHandler;
import org.jboss.staxmapper.FormattingXMLStreamWriter;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 *
 * @author Alexey Loubyansky
 */
public class ASModuleHandler extends CommandHandlerWithHelp {

    private class AddModuleArgument extends ArgumentWithValue {
        private AddModuleArgument(String fullName) {
            super(ASModuleHandler.this, fullName);
        }

        private AddModuleArgument(String fullName, CommandLineCompleter completer) {
            super(ASModuleHandler.this, completer, fullName);
        }

        @Override
        public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
            final String actionValue = action.getValue(ctx.getParsedCommandLine());
            return ACTION_ADD.equals(actionValue) && name.isPresent(ctx.getParsedCommandLine()) && super.canAppearNext(ctx);
        }
    }

    private class AddModuleListArgument extends ArgumentWithListValue {
        private AddModuleListArgument(String fullname) {
            super(ASModuleHandler.this, fullname);
        }

        private AddModuleListArgument(String fullname, CommandLineCompleter completer) {
            super(ASModuleHandler.this, completer, fullname);
        }

        @Override
        public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
            final String actionValue = action.getValue(ctx.getParsedCommandLine());
            return ACTION_ADD.equals(actionValue) && name.isPresent(ctx.getParsedCommandLine()) && super.canAppearNext(ctx);
        }
    }

    private static final String JBOSS_HOME = "JBOSS_HOME";
    private static final String JBOSS_HOME_PROPERTY = "jboss.home.dir";

    private static final String PATH_SEPARATOR = File.pathSeparator;
    private static final String MODULE_SEPARATOR = ",";

    private static final String ACTION_ADD = "add";
    private static final String ACTION_REMOVE = "remove";

    private final ArgumentWithValue action = new ArgumentWithValue(this, new DefaultCompleter(new CandidatesProvider(){
        @Override
        public Collection<String> getAllCandidates(CommandContext ctx) {
            return Arrays.asList(new String[]{ACTION_ADD, ACTION_REMOVE});
        }}), 0, "--action");

    private final ArgumentWithValue name;
    private final ArgumentWithValue mainClass;
    private final ArgumentWithValue resources;
    private final ArgumentWithValue absoluteResources;
    private final ArgumentWithListValue dependencies;
    private final ArgumentWithListValue exportDependencies;
    private final ArgumentWithListValue props;
    private final ArgumentWithValue moduleArg;
    private final ArgumentWithValue slot;
    private final ArgumentWithValue resourceDelimiter;
    private final ArgumentWithValue moduleRootDir;
    private final ArgumentWithoutValue allowNonExistentResources;
    private File modulesDir;

    public ASModuleHandler(CommandContext ctx) {
        super("module", false);

        final FilenameTabCompleter pathCompleter = FilenameTabCompleter.newCompleter(ctx);

        moduleRootDir = new FileSystemPathArgument(this, pathCompleter, "--module-root-dir");

        name = new ArgumentWithValue(this, new CommandLineCompleter() {
            @Override
            public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
                try {
                    String currentAction = action.getValue(ctx.getParsedCommandLine());
                    // suggest only modules from user's repository, not system modules
                    final ModuleNameTabCompleter moduleNameCompleter = ModuleNameTabCompleter.completer(getModulesDir(ctx))
                            .excludeNonModuleFolders(ACTION_REMOVE.equals(currentAction))
                            .includeSystemModules(ACTION_ADD.equals(currentAction))
                            .build();

                    candidates.addAll(moduleNameCompleter.complete(buffer));
                    return 0;
                } catch (CommandLineException e) {
                    return -1;
                }
            }
        }, "--name") {
            @Override
            protected ParsingState initParsingState() {
                final ExpressionBaseState state = new ExpressionBaseState("EXPR", true, false);
                if(Util.isWindows()) {
                    // to not require escaping FS name separator
                    state.setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_OFF);
                } else {
                    state.setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_ON);
                }
                return state;
            }
        };
        name.addRequiredPreceding(action);

        mainClass = new AddModuleArgument("--main-class");

        resources = new AddModuleArgument("--resources", new CommandLineCompleter(){
            @Override
            public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
                final int lastSeparator = buffer.lastIndexOf(PATH_SEPARATOR);
                if(lastSeparator >= 0) {
                    return lastSeparator + 1 + pathCompleter.complete(ctx, buffer.substring(lastSeparator + 1), cursor, candidates);
                }
                return pathCompleter.complete(ctx, buffer, cursor, candidates);
            }}) {
            @Override
            public String getValue(ParsedCommandLine args) {
                String value = super.getValue(args);
                if(value != null) {
                    if(value.length() >= 0 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
                        value = value.substring(1, value.length() - 1);
                    }
                    value = pathCompleter.translatePath(value);
                }
                return value;
            }
            @Override
            protected ParsingState initParsingState() {
                final ExpressionBaseState state = new ExpressionBaseState("EXPR", true, false);
                if(Util.isWindows()) {
                    // to not require escaping FS name separator
                    state.setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_OFF);
                } else {
                    state.setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_ON);
                }
                return state;
            }
        };

        allowNonExistentResources = new ArgumentWithoutValue(this, "--allow-nonexistent-resources");
        allowNonExistentResources.addRequiredPreceding(resources);

        absoluteResources = new AddModuleArgument("--absolute-resources", new CommandLineCompleter(){
            @Override
            public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
                final int lastSeparator = buffer.lastIndexOf(PATH_SEPARATOR);
                if(lastSeparator >= 0) {
                    return lastSeparator + 1 + pathCompleter.complete(ctx, buffer.substring(lastSeparator + 1), cursor, candidates);
                }
                return pathCompleter.complete(ctx, buffer, cursor, candidates);
            }}) {
            @Override
            public String getValue(ParsedCommandLine args) {
                String value = super.getValue(args);
                if(value != null) {
                    if(value.length() >= 0 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
                        value = value.substring(1, value.length() - 1);
                    }
                    value = pathCompleter.translatePath(value);
                }
                return value;
            }
            @Override
            protected ParsingState initParsingState() {
                final ExpressionBaseState state = new ExpressionBaseState("EXPR", true, false);
                if(Util.isWindows()) {
                    // to not require escaping FS name separator
                    state.setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_OFF);
                } else {
                    state.setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_ON);
                }
                return state;
            }
        };

        resourceDelimiter = new AddModuleArgument("--resource-delimiter");

        dependencies = new AddModuleListArgument("--dependencies", new CommandLineCompleter(){
            @Override
            public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
                return doCompleteDependencies(ctx, buffer, cursor, candidates);
            }
        });

        exportDependencies = new AddModuleListArgument("--export-dependencies", new CommandLineCompleter() {
            @Override
            public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
                return doCompleteDependencies(ctx, buffer, cursor, candidates);
            }
        });

        props = new AddModuleListArgument("--properties");

        moduleArg = new FileSystemPathArgument(this, pathCompleter, "--module-xml") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                final String actionValue = action.getValue(ctx.getParsedCommandLine());
                return ACTION_ADD.equals(actionValue) && name.isPresent(ctx.getParsedCommandLine()) && super.canAppearNext(ctx);
            }
        };

        slot = new ArgumentWithValue(this, new DefaultCompleter(new CandidatesProvider() {
            @Override
            public Collection<String> getAllCandidates(CommandContext ctx) {
                final String moduleName = name.getValue(ctx.getParsedCommandLine());
                if(moduleName == null) {
                    return java.util.Collections.emptyList();
                }

                final File moduleDir;
                try {
                    moduleDir = new File(getModulesDir(ctx), moduleName.replace('.', File.separatorChar));
                } catch (CommandLineException e) {
                    return java.util.Collections.emptyList();
                }
                if(!moduleDir.exists()) {
                    return java.util.Collections.emptyList();
                }
                return Arrays.asList(moduleDir.list());
            }})
        , "--slot");

        moduleArg.addCantAppearAfter(mainClass);
        moduleArg.addCantAppearAfter(dependencies);
        moduleArg.addCantAppearAfter(exportDependencies);
        moduleArg.addCantAppearAfter(props);

        mainClass.addCantAppearAfter(moduleArg);
        dependencies.addCantAppearAfter(moduleArg);
        exportDependencies.addCantAppearAfter(moduleArg);
        props.addCantAppearAfter(moduleArg);
    }

    private int doCompleteDependencies(CommandContext ctx, String buffer,
            int cursor, List<String> candidates) {
        final int lastSeparator = buffer.lastIndexOf(MODULE_SEPARATOR);

        try {
            // any module (including system) can be a dependency
            final ModuleNameTabCompleter moduleNameCompleter
                    = ModuleNameTabCompleter.completer(getModulesDir(ctx))
                    .excludeNonModuleFolders(true)
                    .includeSystemModules(true)
                    .build();

            if (lastSeparator >= 0) {
                candidates.addAll(moduleNameCompleter.complete(buffer.substring(lastSeparator + 1)));
                return lastSeparator + 1;
            } else {
                candidates.addAll(moduleNameCompleter.complete(buffer));
                return 0;
            }
        } catch (CommandLineException e) {
            return -1;
        }
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        return !ctx.isDomainMode();
    }

    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {

        final ParsedCommandLine parsedCmd = ctx.getParsedCommandLine();
        final String actionValue = action.getValue(parsedCmd);
        if(actionValue == null) {
            throw new CommandFormatException("Action argument is missing: " + ACTION_ADD + " or " + ACTION_REMOVE);
        }

        if(ACTION_ADD.equals(actionValue)) {
            addModule(ctx, parsedCmd);
        } else if(ACTION_REMOVE.equals(actionValue)) {
            removeModule(parsedCmd, ctx);
        } else {
            throw new CommandFormatException("Unexpected action '" + actionValue + "', expected values: " + ACTION_ADD + ", " + ACTION_REMOVE);
        }
    }

    protected void addModule(CommandContext ctx, final ParsedCommandLine parsedCmd) throws CommandLineException {

        final String moduleName = name.getValue(parsedCmd, true);

        // resources required only if we are generating module.xml
        if(!moduleArg.isPresent(parsedCmd) && !(resources.isPresent(parsedCmd) || absoluteResources.isPresent(parsedCmd))) {
            throw new CommandFormatException("You must specify at least one resource: use --resources or --absolute-resources parameter");
        }
        final String resourcePaths = resources.getValue(parsedCmd);
        final String absoluteResourcePaths = absoluteResources.getValue(parsedCmd);

        String pathDelimiter = PATH_SEPARATOR;
        if (resourceDelimiter.isPresent(parsedCmd)) {
            pathDelimiter = resourceDelimiter.getValue(parsedCmd);
        }

        final FilenameTabCompleter pathCompleter = Util.isWindows() ? new WindowsFilenameTabCompleter(ctx) : new DefaultFilenameTabCompleter(ctx);
        final String[] resourceArr = (resourcePaths == null) ? new String[0] : resourcePaths.split(pathDelimiter);
        File[] resourceFiles = new File[resourceArr.length];

        boolean allowNonExistent = allowNonExistentResources.isPresent(parsedCmd);
        for(int i = 0; i < resourceArr.length; ++i) {
            final File f = new File(pathCompleter.translatePath(resourceArr[i]));
            if (!f.exists() && !allowNonExistent) {
                throw new CommandLineException("Failed to locate " + f.getAbsolutePath()
                        + ", if you defined a nonexistent resource on purpose you should "
                        + "use the " + allowNonExistentResources.getFullName() + " option");
            }
            resourceFiles[i] = f;
        }

        final String[] absoluteResourceArr = (absoluteResourcePaths == null) ? new String[0] : absoluteResourcePaths.split(pathDelimiter);
        File[] absoluteResourceFiles = new File[absoluteResourceArr.length];
        for(int i = 0; i < absoluteResourceArr.length; ++i) {
            final File f = new File(pathCompleter.translatePath(absoluteResourceArr[i]));
            if(!f.exists()) {
                throw new CommandLineException("Failed to locate " + f.getAbsolutePath());
            }
            absoluteResourceFiles[i] = f;
        }

        final File moduleDir = getModulePath(getModulesDir(ctx), moduleName, slot.getValue(parsedCmd));
        if(moduleDir.exists()) {
            throw new CommandLineException("Module " + moduleName + " already exists at " + moduleDir.getAbsolutePath());
        }

        if(!moduleDir.mkdirs()) {
            throw new CommandLineException("Failed to create directory " + moduleDir.getAbsolutePath());
        }

        final ModuleConfigImpl config;
        final String moduleXml = moduleArg.getValue(parsedCmd);
        if(moduleXml != null) {
            config = null;
            final File source = new File(moduleXml);
            if(!source.exists()) {
                throw new CommandLineException("Failed to locate the file on the filesystem: " + source.getAbsolutePath());
            }
            copy(source, new File(moduleDir, "module.xml"));
        } else {
            config = new ModuleConfigImpl(moduleName);
        }

        for(File f : resourceFiles) {
            copyResource(f, new File(moduleDir, f.getName()), ctx, this);
            if(config != null) {
                config.addResource(new ResourceRoot(f.getName()));
            }
        }

        for(File f : absoluteResourceFiles) {
            if(config != null) {
                try {
                    config.addResource(new ResourceRoot(f.getCanonicalPath()));
                } catch (IOException ioe) {
                    throw new CommandLineException("Failed to read path: " + f.getAbsolutePath(), ioe);
                }
            }
        }

        if (config != null) {
            Set<String> modules = new HashSet<>();
            final String dependenciesStr = dependencies.getValue(parsedCmd);
            if(dependenciesStr != null) {
                final String[] depsArr = dependenciesStr.split(",+");
                for(String dep : depsArr) {
                    // TODO validate dependencies
                    String depName = dep.trim();
                    config.addDependency(new ModuleDependency(depName));
                    modules.add(depName);
                }
            }

            final String exportDependenciesStr = exportDependencies.getValue(parsedCmd);
            if (exportDependenciesStr != null) {
                final String[] depsArr = exportDependenciesStr.split(",+");
                for (String dep : depsArr) {
                    // TODO validate dependencies
                    String depName = dep.trim();
                    if (modules.contains(depName)) {
                        deleteRecursively(moduleDir);
                        throw new CommandLineException("Error, duplicated dependency "
                                + depName);
                    }
                    modules.add(depName);
                    config.addDependency(new ModuleDependency(depName, true));
                }
            }

            final String propsStr = props.getValue(parsedCmd);
            if(propsStr != null) {
                final String[] pairs = propsStr.split(",");
                for (String pair : pairs) {
                    int equals = pair.indexOf('=');
                    if (equals == -1) {
                        throw new CommandFormatException("Property '" + pair + "' in '" + propsStr + "' is missing the equals sign.");
                    }
                    final String propName = pair.substring(0, equals);
                    if (propName.isEmpty()) {
                        throw new CommandFormatException("Property name is missing for '" + pair + "' in '" + propsStr + "'");
                    }
                    config.setProperty(propName, pair.substring(equals + 1));
                }
            }

            final String slotVal = slot.getValue(parsedCmd);
            if (slotVal != null) {
                config.setSlot(slotVal);
            }

            final String mainCls = mainClass.getValue(parsedCmd);
            if(mainCls != null) {
                config.setMainClass(mainCls);
            }

            FileOutputStream fos = null;
            final File moduleFile = new File(moduleDir, "module.xml");
            try {
                fos = new FileOutputStream(moduleFile);
                XMLExtendedStreamWriter xmlWriter = create(XMLOutputFactory.newInstance().createXMLStreamWriter(fos, StandardCharsets.UTF_8.name()));
                config.writeContent(xmlWriter, null);
                xmlWriter.flush();
            } catch (IOException e) {
                throw new CommandLineException("Failed to create file " + moduleFile.getAbsolutePath(), e);
            } catch (XMLStreamException e) {
                throw new CommandLineException("Failed to write to " + moduleFile.getAbsolutePath(), e);
            } finally {
                if(fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {}
                }
            }
        }
    }

    private void removeModule(ParsedCommandLine parsedCmd, CommandContext ctx) throws CommandLineException {

        final String moduleName = name.getValue(parsedCmd, true);
        final File modulesDir = getModulesDir(ctx);
        File modulePath = getModulePath(modulesDir, moduleName, slot.getValue(parsedCmd));
        if(!modulePath.exists()) {
            throw new CommandLineException("Failed to locate module " + moduleName + " at " + modulePath.getAbsolutePath());
        }

        // delete the whole slot directory
        deleteRecursively(modulePath);

        modulePath = modulePath.getParentFile();
        while(!modulesDir.equals(modulePath)) {
            if(modulePath.list().length > 0) {
                break;
            }
            if(!modulePath.delete()) {
                throw new CommandLineException("Failed to delete " + modulePath.getAbsolutePath());
            }
            modulePath = modulePath.getParentFile();
        }
    }

    protected void deleteRecursively(final File file) throws CommandLineException {
        if (file.isDirectory()) {
            final File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteRecursively(f);
                }
            }
        }
        if (!file.delete()) {
            throw new CommandLineException("Failed to delete " + file.getAbsolutePath());
        }
    }

    protected File getModulePath(File modulesDir, final String moduleName, String slot) throws CommandLineException {
        return new File(modulesDir, moduleName.replace('.', File.separatorChar) + File.separatorChar + (slot == null ? "main" : slot));
    }

    protected File getModulesDir(CommandContext ctx) throws CommandLineException {
        // First check if we have an option
        File modsDir = null;
        String moduleRootDirStr = moduleRootDir.getValue(ctx.getParsedCommandLine());
        if (moduleRootDirStr != null) {
            modsDir = new File(moduleRootDirStr);
        }
        if (modsDir == null) {
            if(modulesDir != null) {
                return modulesDir;
            }
            // First check the environment variable
            String rootDir = WildFlySecurityManager.getEnvPropertyPrivileged(JBOSS_HOME, null);
            if (rootDir == null) {
                // Not found, check the system property, this may be set from a client using the CLI API to execute commands
                rootDir = WildFlySecurityManager.getPropertyPrivileged(JBOSS_HOME_PROPERTY, null);
            }
            if (rootDir == null) {
                throw new CommandLineException(JBOSS_HOME + " environment variable is not set.");
            }
            modulesDir = new File(rootDir, "modules");
            modsDir = modulesDir;
        }
        if (!modsDir.exists()) {
            throw new CommandLineException("Failed to locate the modules dir on the filesystem: " + modsDir.getAbsolutePath());
        }
        return modsDir;
    }

    public static XMLExtendedStreamWriter create(XMLStreamWriter writer) throws CommandLineException {
        try {
            return new FormattingXMLStreamWriter(writer);
        } catch (Exception e) {
            throw new CommandLineException("Failed to create xml stream writer.", e);
        }
    }

    private static void copyResource(final File source, final File target,
            CommandContext ctx, ASModuleHandler handler) throws CommandLineException {
        if (!source.exists()) {
            target.mkdir();
            return;
        }
        if (source.isDirectory()) {
            copyDirectory(source, target, ctx, handler);
        } else {
            copy(source, target);
        }
    }

    private static void copyDirectory(final File source, final File target,
            CommandContext ctx, ASModuleHandler handler) throws CommandLineException {
        try {
            copyDirectory(source, target, new ArrayList<>());
        } catch (IOException ex) {
            Exception removalException = null;
            try {
                // Remove fully the module.
                handler.removeModule(ctx.getParsedCommandLine(), ctx);
            } catch (Exception ee) {
                removalException = ee;
            }
            String msg = "An error occurred while copying directory  "
                    + source.getAbsolutePath() + " to " + target.getAbsolutePath()
                    + " :" + ex;
            if (removalException == null) {
                msg = "Module not added. " + msg;
            } else {
                msg = msg + ". Attempt to remove the module has failed: "
                        + removalException;
            }
            throw new CommandLineException(msg);
        }
    }

    private static void copyDirectory(final File source, final File target, final List<Path> seen) throws IOException {
        Path sourcePath = source.toPath();
        Path targetPath = target.toPath();
        seen.add(sourcePath);
        Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir,
                    final BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(targetPath.resolve(sourcePath
                        .relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path file,
                    final BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink()) {
                    Path symTarget = Files.readSymbolicLink(file);
                    File symTargetFile = symTarget.toFile();
                    if (!symTargetFile.isAbsolute()) {
                        if (file.getParent() != null) {
                            symTarget = file.getParent().resolve(symTarget);
                        } else {
                            throw new IOException("Recursive symbolic link: "
                                    + file.toFile().getAbsolutePath() + "=>"
                                    + symTargetFile.getCanonicalPath()
                                    + ". Can't copy directory");
                        }
                    }
                    if (symTarget.toFile().getCanonicalFile().isDirectory()) {
                        // Resursive link (if the symlink target has already been copied
                        // as a linked directory).
                        if (seen.contains(symTarget)) {
                            throw new IOException("Recursive symbolic link: "
                                    + file.toFile().getAbsolutePath() + "=>"
                                    + symTarget.toFile().getCanonicalPath()
                                    + ". Can't copy directory");
                        } else {
                            // copy the directory target.
                            copyDirectory(symTarget.toFile(), targetPath.resolve(sourcePath
                                    .relativize(file)).toFile(), seen);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                }
                Files.copy(file,
                        targetPath.resolve(sourcePath.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void copy(final File source, final File target) throws CommandLineException {
        final byte[] buff = new byte[8192];
        BufferedInputStream in = null;
        BufferedOutputStream out = null;
        int read;
        try {
            in = new BufferedInputStream(new FileInputStream(source));
            out = new BufferedOutputStream(new FileOutputStream(target));
            while ((read = in.read(buff)) != -1) {
                out.write(buff, 0, read);
            }
            out.flush();
        } catch (FileNotFoundException e) {
            throw new CommandLineException("Failed to locate the file on the filesystem copying " +
                source.getAbsolutePath() + " to " + target.getAbsolutePath(), e);
        } catch (IOException e) {
            throw new CommandLineException("Failed to copy " + source.getAbsolutePath() + " to " + target.getAbsolutePath(), e);
        } finally {
            try {
                if(out != null) {
                    out.close();
                }
            } catch(IOException e) {}
            try {
                if(in != null) {
                    in.close();
                }
            } catch(IOException e) {}
        }
    }

}
