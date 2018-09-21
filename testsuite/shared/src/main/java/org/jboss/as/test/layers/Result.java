/*
 * Copyright 2016-2018 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    private final List<ExtensionResult> extensions;

    Result(long size, Set<String> modules, Map<String, Set<String>> unresolvedOptional, Set<String> notReferenced, List<ExtensionResult> extensions) {
        this.size = size;
        this.modules = modules;
        this.unresolvedOptional = unresolvedOptional;
        this.notReferenced = notReferenced;
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
     * @return the extensions
     */
    public List<ExtensionResult> getExtensions() {
        return extensions;
    }

}
