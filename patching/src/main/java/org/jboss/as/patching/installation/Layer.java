/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.installation;

/**
 * Layer target info layout:
 *
 * <pre><code>
 *
 * ${JBOSS_HOME}
 * |-- bundles
 * |   `-- system
 * |       `-- layers
 * |           `-- &lt;name> => {@link org.jboss.as.patching.DirectoryStructure#getBundleRepositoryRoot()}
 * |               `-- patches
 * |                   `-- &lt;patchId> => {@link org.jboss.as.patching.DirectoryStructure#getBundlesPatchDirectory(String)}
 * |-- modules
 * |   `-- system
 * |       `-- layers
 * |           `-- &lt;name> => {@link org.jboss.as.patching.DirectoryStructure#getModuleRoot()}
 * |                `-- patches
 * |                   `-- &lt;patchId> => {@link org.jboss.as.patching.DirectoryStructure#getModulePatchDirectory(String)}
 * `-- .installation
 *     `-- patches
 *         `-- layers
 *             `-- &lt;name>
 *                 `-- layers.conf => {@link org.jboss.as.patching.DirectoryStructure#getInstallationInfo()}
 * <code>
 * </pre>
 *
 * @author Emanuel Muckenhuber
 */
public interface Layer extends PatchableTarget {

}
