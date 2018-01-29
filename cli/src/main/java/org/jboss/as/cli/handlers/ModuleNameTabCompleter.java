/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.cli.handlers;

import org.jboss.as.cli.EscapeSelector;
import org.jboss.as.cli.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Generates suggestions for module names. Each suggestion generates only next part of the name (ie. up to the name separator).
 *
 * Assumes the module repository used has standard layered repository layout. Matching suggestions are found:
 * <uL>
 *  <li>under the repository root (excluding 'system' directory)
 *  <li>under the system/layers/{layer name}
 *  <li>under the system/add-ons/{add-on name}
 * </uL>
 * Modules changed, removed or added via patches are not included in the suggestions.
 * The modules are not validated - invalid or disabled modules and empty directories are included in the suggestions.
 *
 * @author Bartosz Spyrko-Smietanko
 */
public class ModuleNameTabCompleter {

    private static final EscapeSelector ESCAPE_SELECTOR = ch -> ch == '\\' || ch == ' ' || ch == '"';
    private static final String MODULE_NAME_SEPARATOR = ".";
    public static final String LAYERS_DIR = "system/layers";
    public static final String ADDONS_DIR = "system/add-ons";

    private final File modulesRoot;
    private final File layersDir;
    private final File addonsDir;
    private final boolean includeSystemModules;
    private final boolean excludeNonModuleFolders;

    private ModuleNameTabCompleter(Builder builder) {
        modulesRoot = builder.modulesRoot.getAbsoluteFile();
        layersDir = new File(modulesRoot, LAYERS_DIR);
        addonsDir = new File(modulesRoot, ADDONS_DIR);

        this.excludeNonModuleFolders = builder.excludeNonModuleFolders;
        this.includeSystemModules = builder.includeSystemModules;
    }


    public List<String> complete(String buffer) {
        final String userEntry = buffer == null ? "" : buffer;
        final Set<String> suggestions = new TreeSet<>(); // TreeSet deals with duplication and ordering

        List<File> moduleTrees = findInitialModuleDirectories();

        for (File f : moduleTrees) {
            findSuggestion(f, f.getName(), userEntry, suggestions);
        }

        return new ArrayList<>(suggestions);
    }

    private List<File> findInitialModuleDirectories() {
        List<File> moduleTrees = new ArrayList<>();

        moduleTrees.addAll(Arrays.asList(modulesRoot.listFiles(this::isNotSystemFolder)));

        if (includeSystemModules && layersDir.exists()) {
            for (File layer : layersDir.listFiles(File::isDirectory)) {
                moduleTrees.addAll(Arrays.asList(layer.listFiles(this::isNotPatchFolder)));
            }
        }

        if (includeSystemModules && addonsDir.exists()) {
            for (File addon : addonsDir.listFiles(File::isDirectory)) {
                moduleTrees.addAll(Arrays.asList(addon.listFiles(this::isNotPatchFolder)));
            }
        }

        return moduleTrees;
    }

    private void findSuggestion(File currentDirectory, String suggestion, String userEntry, Collection<String> candidates) {
        if (!matchesUserEntry(currentDirectory, userEntry) || (excludeNonModuleFolders && isSlotDirectory(currentDirectory))) {
            return;
        }

        if (tail(userEntry).isEmpty() && !requestsSubmodules(userEntry)) {
            final String fullModuleName = Util.escapeString(suggestion, ESCAPE_SELECTOR);
            final String partialModuleName = Util.escapeString(suggestion + MODULE_NAME_SEPARATOR, ESCAPE_SELECTOR);

            if (excludeNonModuleFolders) {
                final boolean isExactMatch = currentDirectory.getName().equals(userEntry);
                final boolean hasNestedModules = hasNestedModules(currentDirectory);
                final boolean isCompleteModule = isCompleteModule(currentDirectory);


                /*
                The suggestion should have a trailing separator ('.') if it's a part of longer module name.
                If the suggested name is both a full module name and a part of longer name (ie. nested modules), suggest
                the name without separator - unless user input is a complete name in which case suggest both options.
                 */
                if (isCompleteModule && hasNestedModules && isExactMatch) {
                    candidates.add(fullModuleName);
                    candidates.add(partialModuleName);
                } else if (isCompleteModule) {
                    candidates.add(fullModuleName);
                } else if (hasNestedModules) {
                    candidates.add(partialModuleName);
                }
            } else {
                final boolean hasChildren = currentDirectory.listFiles(File::isDirectory).length > 0;
                final boolean isExactMatch = currentDirectory.getName().equals(userEntry);

                if (hasChildren && isExactMatch) {
                    candidates.add(partialModuleName);
                }
                candidates.add(fullModuleName);
            }
        } else {
            for (File file : currentDirectory.listFiles(File::isDirectory)) {
                findSuggestion(file, suggestion + MODULE_NAME_SEPARATOR + file.getName(), tail(userEntry), candidates);
            }
        }
    }

    private boolean matchesUserEntry(File currentDirectory, String userEntry) {
        if (!userEntry.endsWith(MODULE_NAME_SEPARATOR) && tail(userEntry).isEmpty()) {
            return currentDirectory.getName().startsWith(head(userEntry));
        } else {
            return currentDirectory.getName().equals(head(userEntry));
        }
    }

    private boolean isCompleteModule(File file) {
        return file.listFiles(f -> f.isDirectory() && isSlotDirectory(f)).length > 0;
    }


    private boolean hasNestedModules(File file) {
        final File[] nonSlotChildren = file.listFiles(f -> f.isDirectory() && !isSlotDirectory(f));
        for (File potentialModule : nonSlotChildren) {
            if (subModuleExists(potentialModule)) {
                return true;
            }
        }
        return false;
    }

    // depth- first search for any module - just to check that the suggestion has any chance of delivering correct result
    private boolean subModuleExists(File dir) {
        if (isSlotDirectory(dir)) {
            return true;
        } else {
            File[] children = dir.listFiles(File::isDirectory);
            for (File child : children) {
                if (subModuleExists(child)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isSlotDirectory(File currentDirectory) {
        return currentDirectory.listFiles(f -> f.getName().equals("module.xml")).length > 0;
    }

    private boolean requestsSubmodules(String moduleNamePattern) {
        return moduleNamePattern.endsWith(MODULE_NAME_SEPARATOR);
    }

    private boolean isNotSystemFolder(File f) {
        return f.isDirectory() && !f.getName().equals("system");
    }

    private boolean isNotPatchFolder(File f) {
        return f.isDirectory() && !f.getName().equals("patches");
    }

    // get first part of module name (up to separator)
    private String head(String moduleName) {
        if (moduleName.indexOf(MODULE_NAME_SEPARATOR) > 0) {
            return moduleName.substring(0, moduleName.indexOf(MODULE_NAME_SEPARATOR));
        } else {
            return moduleName;
        }
    }

    // get all parts of module name apart from first
    private String tail(String moduleName) {
        if (moduleName.indexOf(MODULE_NAME_SEPARATOR) > 0) {
            return moduleName.substring(moduleName.indexOf(MODULE_NAME_SEPARATOR) + 1);
        } else {
            return "";
        }
    }

    public static Builder completer(File modulesRoot) {
        return new Builder(modulesRoot);
    }

    public static class Builder {
        private final File modulesRoot;
        private boolean includeSystemModules;
        private boolean excludeNonModuleFolders;

        public Builder(File modulesRoot) {
            this.modulesRoot = modulesRoot;
        }

        public Builder includeSystemModules(boolean includeSystemModules) {
            this.includeSystemModules = includeSystemModules;
            return this;
        }

        public Builder excludeNonModuleFolders(boolean excludeNonModuleFolders) {
            this.excludeNonModuleFolders = excludeNonModuleFolders;
            return this;
        }

        public ModuleNameTabCompleter build() {
            return new ModuleNameTabCompleter(this);
        }
    }
}
