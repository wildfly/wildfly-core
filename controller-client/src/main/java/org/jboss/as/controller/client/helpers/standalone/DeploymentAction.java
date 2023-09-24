/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.standalone;

import java.util.UUID;


public interface DeploymentAction {

    /**
     * Enumeration of valid types of deployment actions.
     */
    enum Type {
        /**
         * Add content to the deployment content repository. Does not imply
         * deploying it into the runtime.
         */
        ADD,
        /**
         * Add content to an exploded deployment in content repository. Does not imply
         * deploying it into the runtime.
         */
        ADD_CONTENT,
        /**
         * Deploy content into the runtime, without replacing existing content.
         */
        DEPLOY,
        /**
         * Explode a deployment content in the content repository. The content must have
         * been previously {@link #ADD added to the content repository}.
         */
        EXPLODE,
        /**
         * Replace existing content in the runtime. The new content must have
         * been previously {@link #ADD added to the content repository}.
         */
        REPLACE,
        /**
         * Add new content to the deployment content repository, replace
         * existing content of the same name in the runtime, and remove the
         * replaced content from the deployment content repository. This is
         * equivalent to an {@link #ADD}, {@link #UNDEPLOY}, {@link #DEPLOY},
         * {@link #REMOVE} sequence where the new content has the same name
         * as the content being replaced.
         */
        FULL_REPLACE,
        /**
         * Undeploy content from the runtime. The content remains in the
         * content repository.
         */
        UNDEPLOY,
        /**
         * Undeploy existing content from the runtime and deploy it again.
         */
        REDEPLOY,
        /**
         * Remove content from the deployment content repository. The content
         * must have previously been {@link #UNDEPLOY undeployed from all
         * runtimes}.
         */
        REMOVE,
        /**
         * Remove content from an exploded deployment in the content repository. The content
         * must have previously been {@link #UNDEPLOY undeployed from all
         * runtimes}.
         */
        REMOVE_CONTENT
    }

    /**
     * Gets the unique id of the action.
     *
     * @return the id. Will not be <code>null</code>
     */
    UUID getId() ;

    /**
     * Gets the type of the action.
     *
     * @return the type. Will not be <code>null</code>
     */
    Type getType();

    /**
     * Gets the name of the content associated with
     * the action. All deployment content has a unique name provided by the
     * user when an {@link Type#ADD} or {@link Type#FULL_REPLACE} action
     * is requested. API methods that request other {@link Type types of actions}
     * will ask for this name as a parameter.
     *
     * @return the name of the content. Will not be <code>null</code>
     */
    String getDeploymentUnitUniqueName() ;

    /**
     * For type {@link Type#REPLACE} and {@link Type#FULL_REPLACE} only, returns the name
     * of the content that is being replaced.
     *
     * @return the name of the content being replaced, or <code>null</code> if
     *   {@link #getType()} is not {@link Type#REPLACE} or {@link Type#FULL_REPLACE}.
     *   Will not be <code>null</code> otherwise
     */
    String getReplacedDeploymentUnitUniqueName();
}
