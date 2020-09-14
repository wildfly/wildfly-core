/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.server.deployment.transformation;


import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Performs transformation operations on deployment content.
 *
 * This interface is experimental and may be removed or altered at any time.
 */
@Deprecated
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
