/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import java.util.Collection;


/**
 * @author Alexey Loubyansky
 *
 */
public interface PatchElement {

    /**
     * Patch element identifier.
     *
     * @return  patch element identifier
     */
    String getId();

    /**
     * The description of the patch element.
     *
     * @return  description of the patch element
     */
    String getDescription();

    /**
     * The provider of the patch element.
     *
     * @return  provider of the patch element
     */
    PatchElementProvider getProvider();

    /**
     * Get the content modifications.
     *
     * @return the modifications
     */
    Collection<ContentModification> getModifications();
}
