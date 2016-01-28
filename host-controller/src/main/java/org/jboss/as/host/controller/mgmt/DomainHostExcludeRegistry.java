/*
Copyright 2016 Red Hat, Inc.

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

package org.jboss.as.host.controller.mgmt;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.domain.controller.logging.DomainControllerLogger;

/**
 * Registry of domain-wide host-ignore information.
 *
 * @author Brian Stansberry
 */
public final class DomainHostExcludeRegistry {

    /**
     * Key used to identify kernel management API version entries in the {@link DomainHostExcludeRegistry}. May
     * either encapsulate a full major.minor.micro version, or just a major.minor.
     */
    public static final class VersionKey {
        private final int majorVersion;
        private final int minorVersion;
        private final Integer microVersion;

        /**
         * Create a version key
         * @param majorVersion the kernel management API major version
         * @param minorVersion the kernel management API minor version
         * @param microVersion the kernel management API micro version, or {@code null} if this key is
         *                     used for {@code host-ignore} information that can apply to any micro release
         *                     of the given major + minor.
         */
        public VersionKey(int majorVersion, int minorVersion, Integer microVersion) {
            this.majorVersion = majorVersion;
            this.minorVersion = minorVersion;
            this.microVersion = microVersion;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            VersionKey that = (VersionKey) o;

            return majorVersion == that.majorVersion
                    && minorVersion == that.minorVersion
                    && (microVersion != null ? microVersion.equals(that.microVersion) : that.microVersion == null);

        }

        @Override
        public int hashCode() {
            int result = majorVersion;
            result = 31 * result + minorVersion;
            result = 31 * result + (microVersion != null ? microVersion.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "VersionKey{" +
                    "majorVersion=" + majorVersion +
                    ", minorVersion=" + minorVersion +
                    ", microVersion=" + microVersion +
                    '}';
        }
    }

    /**
     * Information about what should and should not be ignored for a host running a particular
     * kernel management version.
     */
    static final class VersionExcludeData {
        private final Set<String> excludedExtensions;
        private final Set<String> activeServerGroups;
        private final Set<String> activeSocketBindingGroups;

        private VersionExcludeData(Set<String> excludedExtensions, Set<String> activeServerGroups, Set<String> activeSocketBindingGroups) {
            this.excludedExtensions = excludedExtensions == null
                    ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(excludedExtensions));
            this.activeServerGroups = activeServerGroups == null
                    ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(activeServerGroups));
            this.activeSocketBindingGroups = activeSocketBindingGroups == null
                    ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(activeSocketBindingGroups));
        }

        Set<String> getExcludedExtensions() {
            return excludedExtensions;
        }

        Set<String> getActiveServerGroups() {
            return activeServerGroups;
        }

        Set<String> getActiveSocketBindingGroups() {
            return activeSocketBindingGroups;
        }

        @Override
        public String toString() {
            return "VersionExcludeData{" +
                    "ignoredExtensions=" + excludedExtensions +
                    ", activeServerGroups=" + activeServerGroups +
                    ", activeSocketBindingGroups=" + activeSocketBindingGroups +
                    '}';
        }
    }

    private final Map<VersionKey, VersionExcludeData> registry = Collections.synchronizedMap(new HashMap<>());

    /** Creates a new {@code DomainHostIgnoreRegistry */
    public DomainHostExcludeRegistry() {

    }

    /**
     * Gets the host-ignore data for a slave host running the given version.
     *
     * @param major the kernel management API major version
     * @param minor the kernel management API minor version
     * @param micro the kernel management API micro version
     *
     * @return the host-ignore data, or {@code null} if there is no matching registration
     */
    VersionExcludeData getVersionIgnoreData(int major, int minor, int micro) {
        VersionExcludeData result = registry.get(new VersionKey(major, minor, micro));
        if (result == null) {
            result = registry.get(new VersionKey(major, minor, null));
        }
        return result;
    }

    public void recordVersionExcludeData(VersionKey version, Set<String> excludedExtensions, Set<String> activeServerGroups, Set<String> activeSocketBindingGroups) {
        VersionExcludeData value = new VersionExcludeData(excludedExtensions, activeServerGroups, activeSocketBindingGroups);
        registry.put(version, value);
        DomainControllerLogger.ROOT_LOGGER.tracef("Recorded %s for %s", value, version);
    }

    public void removeVersionExcludeData(VersionKey version) {
        VersionExcludeData value = registry.remove(version);
        DomainControllerLogger.ROOT_LOGGER.tracef("Removed %s for %s", value, version);
    }
}
