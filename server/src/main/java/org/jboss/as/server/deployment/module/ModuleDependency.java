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

import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.filter.PathFilter;

/**
 * @author John E. Bailey
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ModuleDependency implements Serializable {

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
    private final ModuleIdentifier identifier;
    private final boolean export;
    private final boolean optional;
    private final List<FilterSpecification> importFilters = new ArrayList<FilterSpecification>();
    private final List<FilterSpecification> exportFilters = new ArrayList<FilterSpecification>();
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
     * @param importServices
     * @param userSpecified {@code true} if this dependency was specified by the user, {@code false} if it was automatically added
     */
    public ModuleDependency(final ModuleLoader moduleLoader, final String identifier, final boolean optional, final boolean export, final boolean importServices, final boolean userSpecified) {
        this(moduleLoader, ModuleIdentifier.create(identifier), optional, export, importServices, userSpecified, null);
    }

    /**
     * Construct a new instance.
     *
     * @param moduleLoader the module loader of the dependency (if {@code null}, then use the default server module loader)
     * @param identifier the module identifier
     * @param optional {@code true} if this is an optional dependency
     * @param export {@code true} if resources should be exported by default
     * @param importServices
     * @param userSpecified {@code true} if this dependency was specified by the user, {@code false} if it was automatically added
     * @param reason reason for adding implicit module dependency
     */
    public ModuleDependency(final ModuleLoader moduleLoader, final String identifier, final boolean optional, final boolean export, final boolean importServices, final boolean userSpecified, String reason) {
        this(moduleLoader, ModuleIdentifier.create(identifier), optional, export, importServices, userSpecified, reason);
    }

    /**
     * Construct a new instance.
     *
     * @param moduleLoader the module loader of the dependency (if {@code null}, then use the default server module loader)
     * @param identifier the module identifier
     * @param optional {@code true} if this is an optional dependency
     * @param export {@code true} if resources should be exported by default
     * @param importServices
     * @param userSpecified {@code true} if this dependency was specified by the user, {@code false} if it was automatically added
     */
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
     * @param importServices
     * @param userSpecified {@code true} if this dependency was specified by the user, {@code false} if it was automatically added
     * @param reason reason for adding implicit module dependency
     */
    public ModuleDependency(final ModuleLoader moduleLoader, final ModuleIdentifier identifier, final boolean optional, final boolean export, final boolean importServices, final boolean userSpecified, String reason) {
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

    public ModuleIdentifier getIdentifier() {
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
