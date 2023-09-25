/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform;

import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;

/**
 * A potentially remote target requiring transformation.
 *
 * @author Emanuel Muckenhuber
 */
public interface TransformationTarget {

    /**
     * Get the version of this target.
     *
     * @return the model version
     */
    ModelVersion getVersion();

    /**
     * Get the subsystem version.
     *
     * @param subsystemName the subsystem name
     * @return the version of the specified subsystem, {@code null} if it does not exist
     */
    ModelVersion getSubsystemVersion(String subsystemName);

    /**
     * Get the transformer entry.
     * @param context TODO
     * @param address the path address
     *
     * @return the transformer entry
     */
    TransformerEntry getTransformerEntry(TransformationContext context, PathAddress address);

    /**
     * Get path transformations.
     *
     * @param address the path address
     * @return a list of registered path transformers
     */
    List<PathAddressTransformer> getPathTransformation(PathAddress address);

    /**
     * Resolve a resource transformer for agiven address.
     * @param context TODO
     * @param address the path address
     *
     * @return the transformer
     */
    ResourceTransformer resolveTransformer(ResourceTransformationContext context, PathAddress address);

    /**
     * Resolve an operation transformer for a given address.
     * @param context TODO
     * @param address the address
     * @param operationName the operation name
     *
     * @return the operation transformer
     */
    OperationTransformer resolveTransformer(TransformationContext context, PathAddress address, String operationName);

    /**
     * Add version information for a subsystem.
     *
     * @param subsystemName the name of the subsystem. Cannot be {@code null}
     * @param majorVersion the major version of the subsystem's management API
     * @param minorVersion the minor version of the subsystem's management API
     */
    void addSubsystemVersion(String subsystemName, int majorVersion, int minorVersion);

    /**
     * Add version information for a subsystem.
     *
     * @param subsystemName the subsystem name
     * @param version the version
     */
    void addSubsystemVersion(String subsystemName, ModelVersion version);

    /**
     * Get the type of the target.
     *
     * @return the target type
     */
    TransformationTargetType getTargetType();

    /**
     * Get the name of the host we are talking to
     */
    String getHostName();

    /**
     * Gets whether this target can make its list of ignored resources known when it registers.
     *
     * @return {@code true} if the target can provide the ignored resources list; {@code false} if that is not supported.
     */
    boolean isIgnoredResourceListAvailableAtRegistration();

    boolean isIgnoreUnaffectedConfig();

    enum TransformationTargetType {

        DOMAIN,
        HOST,
        SERVER,
        ;
    }
}
