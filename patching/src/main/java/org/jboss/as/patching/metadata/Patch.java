/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The patch metadata.
 *
 * @author Emanuel Muckenhuber
 * @author Alexey Loubyansky
 */
public interface Patch {

    Pattern PATCH_NAME_PATTERN = Pattern.compile("[-a-zA-Z0-9_+*.]+");

    public enum PatchType {
        CUMULATIVE("cumulative"),
        ONE_OFF("one-off"),
        ;

        private final String name;

        private PatchType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * Get the unique patch ID.
     *
     * @return the patch id
     */
    String getPatchId();

    /**
     * Get the patch description.
     *
     * @return the patch description
     */
    String getDescription();

    /**
     * Get the link to the patch.
     *
     * @return the link
     */
    String getLink();

    /**
     * Returns the target identity.
     *
     * @return  identity which produced this patch
     */
    Identity getIdentity();

    /**
     * List of patch elements.
     *
     * @return  list of patch elements
     */
    List<PatchElement> getElements();

    /**
     * Get the content modifications.
     *
     * @return the modifications
     */
    List<ContentModification> getModifications();

}
