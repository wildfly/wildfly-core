/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.layers;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author jdenise@redhat.com
 */
public class Result {

    public static class ExtensionResult {

        private final long size;
        private final Set<String> modules;
        private final Set<String> unresolved;
        private final String module;

        ExtensionResult(String module, long size, Set<String> modules, Set<String> unresolved) {
            this.module = module;
            this.size = size;
            this.modules = modules;
            this.unresolved = unresolved;
        }

        /**
         * @return the size
         */
        public long getSize() {
            return size;
        }

        /**
         * @return the modules
         */
        public Set<String> getModules() {
            return modules;
        }

        /**
         * @return the notReferenced
         */
        public Set<String> getUnresolved() {
            return unresolved;
        }

        /**
         * @return the module
         */
        public String getModule() {
            return module;
        }
    }
    private final long size;
    private final Map<String, Set<String>> unresolvedOptional;
    private final Set<String> modules;
    private final Set<String> notReferenced;
    private final Set<String> aliases;
    private final List<ExtensionResult> extensions;

    Result(long size, Set<String> modules, Map<String, Set<String>> unresolvedOptional, Set<String> notReferenced,
           Set<String> aliases, List<ExtensionResult> extensions) {
        this.size = size;
        this.modules = modules;
        this.unresolvedOptional = unresolvedOptional;
        this.notReferenced = notReferenced;
        this.aliases = aliases;
        this.extensions = extensions;
    }

    /**
     * @return the size
     */
    public long getSize() {
        return size;
    }

    /**
     * @return the unresolvedOptional
     */
    public Map<String, Set<String>> getUnresolvedOptional() {
        return unresolvedOptional;
    }

    /**
     * @return the modules
     */
    public Set<String> getModules() {
        return modules;
    }

    /**
     * @return the notReferenced
     */
    public Set<String> getNotReferenced() {
        return notReferenced;
    }

    /**
     * @return the aliases
     */
    public Set<String> getAliases() {
        return aliases;
    }

    /**
     * @return the extensions
     */
    public List<ExtensionResult> getExtensions() {
        return extensions;
    }

}
