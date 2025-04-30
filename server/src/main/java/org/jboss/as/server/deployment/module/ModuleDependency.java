/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jboss.as.controller.ModuleIdentifierUtil;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.filter.PathFilter;

/**
 * @author John E. Bailey
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ModuleDependency implements Serializable {

    public static final class Builder {
        private final ModuleLoader moduleLoader;
        private final String identifier;
        private boolean export;
        private boolean optional;
        private boolean importServices;
        private boolean userSpecified;
        private String reason;

        public static Builder of(ModuleLoader moduleLoader, String moduleName) {
            return new Builder(moduleLoader, moduleName);
        }

        private Builder(ModuleLoader moduleLoader, String moduleName) {
            this.moduleLoader = moduleLoader;
            this.identifier = ModuleIdentifierUtil.parseCanonicalModuleIdentifier(moduleName);
        }

        /**
         * Sets whether the dependent module should export this dependency's resources.
         *
         * @param export {@code true} if the dependencies resources should be exported
         * @return this builder
         */
        public Builder setExport(boolean export) {
            this.export = export;
            return this;
        }

        /**
         * Sets whether the dependent module should be able to import services from this dependency.
         *
         * @param importServices {@code true} if the dependent module should be able to load services from the dependency
         * @return this builder
         */
        public Builder setImportServices(boolean importServices) {
            this.importServices = importServices;
            return this;
        }

        /**
         * Sets whether this dependency is optional.
         *
         * @param optional {@code true} if the dependency is optional
         * @return this builder
         */
        public Builder setOptional(boolean optional) {
            this.optional = optional;
            return this;
        }

        /**
         * Sets whether this dependency was explicitly specified by the user.
         *
         * @param userSpecified {@code true} if this dependency was specified by the user, {@code false} if it was automatically added
         * @return this builder
         */
        public Builder setUserSpecified(boolean userSpecified) {
            this.userSpecified = userSpecified;
            return this;
        }

        /**
         * Sets an informational reason describing why this dependency was added.
         *
         * @param reason the reason. May be {@code null}
         * @return this builder
         */
        public Builder setReason(String reason) {
            this.reason = reason;
            return this;
        }

        /**
         * Build a {@code ModuleDependency} using this builder's settings.
         *
         * @return the {@code ModuleDependency}. Will not return {@code null}
         */
        public ModuleDependency build() {
            return new ModuleDependency(moduleLoader, identifier, reason, optional, export, importServices, userSpecified);
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ModuleDependency [");
        if (identifier != null)
            builder.append("identifier=").append(identifier).append(", ");
        if (moduleLoader != null)
            builder.append("moduleLoader=").append(moduleLoader).append(", ");
        builder.append("export=").append(export).append(", optional=").append(optional).append(", importServices=").append(
                importServices);
        if (reason != null) {
            builder.append(", ").append("reason=").append(reason);
        }
        builder.append("]");
        return builder.toString();
    }

    private static final long serialVersionUID = 2749276798703740853L;

    private final ModuleLoader moduleLoader;
    private final String identifier;
    private final boolean export;
    private final boolean optional;
    private final List<FilterSpecification> importFilters = new ArrayList<>();
    private final List<FilterSpecification> exportFilters = new ArrayList<>();
    private final boolean importServices;
    private final boolean userSpecified;
    private final String reason;

    /**
     * Construct a new instance.
     *
     * @param moduleLoader the module loader of the dependency (if {@code null}, then use the default server module loader)
     * @param identifier the module identifier
     * @param optional {@code true} if this is an optional dependency
     * @param export {@code true} if resources should be exported by default
     * @param importServices {@code true} if the dependent module should be able to load services from the dependency
     * @param userSpecified {@code true} if this dependency was specified by the user, {@code false} if it was automatically added
     *
     * @deprecated Use a {@link Builder}
     */
    @Deprecated(forRemoval = true)
    public ModuleDependency(final ModuleLoader moduleLoader, final String identifier, final boolean optional, final boolean export, final boolean importServices, final boolean userSpecified) {
        this(moduleLoader, ModuleIdentifierUtil.parseCanonicalModuleIdentifier(identifier), null, optional, export, importServices, userSpecified);
    }

    /**
     * Construct a new instance.
     *
     * @param moduleLoader the module loader of the dependency (if {@code null}, then use the default server module loader)
     * @param identifier the module identifier
     * @param optional {@code true} if this is an optional dependency
     * @param export {@code true} if resources should be exported by default
     * @param importServices  {@code true} if the dependent module should be able to load services from the dependency
     * @param userSpecified {@code true} if this dependency was specified by the user, {@code false} if it was automatically added
     * @param reason reason for adding implicit module dependency
     *
     * @deprecated Use a {@link Builder}
     */
    @Deprecated(forRemoval = true)
    public ModuleDependency(final ModuleLoader moduleLoader, final String identifier, final boolean optional, final boolean export, final boolean importServices, final boolean userSpecified, String reason) {
        this(moduleLoader, ModuleIdentifierUtil.parseCanonicalModuleIdentifier(identifier), reason, optional, export, importServices, userSpecified);
    }

    /**
     * Construct a new instance.
     *
     * @param moduleLoader the module loader of the dependency (if {@code null}, then use the default server module loader)
     * @param identifier the module identifier
     * @param optional {@code true} if this is an optional dependency
     * @param export {@code true} if resources should be exported by default
     * @param importServices  {@code true} if the dependent module should be able to load services from the dependency
     * @param userSpecified {@code true} if this dependency was specified by the user, {@code false} if it was automatically added
     *
     * @deprecated Use a {@link Builder} or {@link ModuleDependency(ModuleLoader, String, boolean, boolean, boolean, boolean)}
     */
    @Deprecated(forRemoval = true)
    public ModuleDependency(final ModuleLoader moduleLoader, final ModuleIdentifier identifier, final boolean optional, final boolean export, final boolean importServices, final boolean userSpecified) {
        this(moduleLoader, identifier, optional, export, importServices, userSpecified, null);
    }

    /**
     * Construct a new instance.
     *
     * @param moduleLoader the module loader of the dependency (if {@code null}, then use the default server module loader)
     * @param identifier the module identifier
     * @param optional {@code true} if this is an optional dependency
     * @param export {@code true} if resources should be exported by default
     * @param importServices {@code true} if the dependent module should be able to load services from the dependency
     * @param userSpecified {@code true} if this dependency was specified by the user, {@code false} if it was automatically added
     * @param reason reason for adding implicit module dependency
     *
     * @deprecated Use a {@link Builder}
     */
    @Deprecated(forRemoval = true)
    public ModuleDependency(final ModuleLoader moduleLoader, final ModuleIdentifier identifier, final boolean optional, final boolean export, final boolean importServices, final boolean userSpecified, String reason) {
        this(moduleLoader, identifier.toString(), reason, optional, export, importServices, userSpecified);
    }

    private ModuleDependency(final ModuleLoader moduleLoader, final String identifier, String reason, final boolean optional, final boolean export, final boolean importServices, final boolean userSpecified) {
        this.identifier = identifier;
        this.optional = optional;
        this.export = export;
        this.moduleLoader = moduleLoader;
        this.importServices = importServices;
        this.userSpecified = userSpecified;
        this.reason = reason;
    }

    public ModuleLoader getModuleLoader() {
        return moduleLoader;
    }

    /** @deprecated use {@link #getDependencyModule()} */
    @Deprecated(forRemoval = true)
    public ModuleIdentifier getIdentifier() {
        return ModuleIdentifier.fromString(identifier);
    }

    /**
     * Gets the name of the module upon which there is a dependency.
     *
     * @return the {@link ModuleIdentifierUtil#parseCanonicalModuleIdentifier(String) canonical form} of the name of module
     */
    public String getDependencyModule() {
        return identifier;
    }

    public Optional<String> getReason() {
        if (reason != null) {
            return Optional.of(reason);
        } else {
            return Optional.empty();
        }
    }

    public boolean isOptional() {
        return optional;
    }

    public boolean isExport() {
        return export;
    }

    public boolean isUserSpecified() {
        return userSpecified;
    }

    public void addImportFilter(PathFilter pathFilter, boolean include) {
        importFilters.add(new FilterSpecification(pathFilter, include));
    }

    public List<FilterSpecification> getImportFilters() {
        return importFilters;
    }

    public void addExportFilter(PathFilter pathFilter, boolean include) {
        exportFilters.add(new FilterSpecification(pathFilter, include));
    }

    public List<FilterSpecification> getExportFilters() {
        return exportFilters;
    }

    public boolean isImportServices() {
        return importServices;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModuleDependency that = (ModuleDependency) o;

        // Note we don't include 'reason' in equals or hashcode as it does not drive distinct behavior
        return export == that.export
                && optional == that.optional
                && importServices == that.importServices
                && userSpecified == that.userSpecified
                && Objects.equals(moduleLoader, that.moduleLoader)
                && identifier.equals(that.identifier) && importFilters.equals(that.importFilters)
                && exportFilters.equals(that.exportFilters);
    }

    @Override
    public int hashCode() {
        // Note we don't include 'reason' in equals or hashcode as it does not drive distinct behavior
        return Objects.hash(moduleLoader, identifier, export, optional, importFilters, exportFilters, importServices, userSpecified);
    }
}
