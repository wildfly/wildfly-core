/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.transformation;


import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Performs transformation operations on deployment content.
 * <p>
 * This interface is experimental and may be removed or altered at any time.
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated(forRemoval = false)
public interface DeploymentTransformer {

    /**
     * Perform transformation of streamed deployment content.
     *
     * @param in InputStream containing the deployment content to be transformed.
     * @param name the name of the deployment
     * @return an input stream from which the transformed content can be read.
     * @throws IOException if a problem occurs reading or writing the content
     */
    InputStream transform(InputStream in, String name) throws IOException;

    /**
     * Perform transformation of filesystem deployment content.
     * @param src path of the deployment content to be transformed
     * @param target path of either the file to which the transformed content should be written or to the directory
     *               in which it should be written. If the latter the name of the written file will be the same as
     *               the name of the {@code src} file. Note also that {@code target} can be the same path as
     *               {@code src}, in which case the file at {@code src} will be replaced.
     * @throws IOException if a problem occurs reading or writing the content
     */
    void transform(Path src, Path target) throws IOException ;
}
