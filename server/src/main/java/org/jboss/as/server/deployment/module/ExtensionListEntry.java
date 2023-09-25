/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import java.net.URI;

/**
 * An extension list entry.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ExtensionListEntry {
    private final String title;
    private final String name;
    private final String specificationVersion;
    private final String implementationVersion;
    private final String implementationVendorId;
    private final URI implementationUrl;

    /**
     * Construct a new instance.
     *
     * @param title the name of the value of the {@code Extension-List} attribute for this item
     * @param name the value of the {@code &lt;extension&gt;-Extension-Name} attribute
     * @param specificationVersion the value of the {@code &lt;extension&gt;-Specification-Version} attribute
     * @param implementationVersion the value of the {@code &lt;extension&gt;-Implementation-Version} attribute
     * @param implementationVendorId the value of the {@code &lt;extension&gt;-Implementation-Vendor-Id} attribute
     * @param implementationUrl the value of the {@code &lt;extension&gt;-Implementation-URL} attribute
     */
    public ExtensionListEntry(final String title, final String name, final String specificationVersion, final String implementationVersion, final String implementationVendorId, final URI implementationUrl) {
        this.title = title;
        this.name = name;
        this.specificationVersion = specificationVersion;
        this.implementationVersion = implementationVersion;
        this.implementationVendorId = implementationVendorId;
        this.implementationUrl = implementationUrl;
    }

    /**
     * Get the extension list title (from the {@code Extension-List} attribute) for this entry.
     *
     * @return the title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Get the extension name.
     *
     * @return the extension name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the specification version.
     *
     * @return the specification version
     */
    public String getSpecificationVersion() {
        return specificationVersion;
    }

    /**
     * Get the implementation version.
     *
     * @return the implementation version
     */
    public String getImplementationVersion() {
        return implementationVersion;
    }

    /**
     * Get the implementation vendor ID.
     *
     * @return the implementation vendor ID
     */
    public String getImplementationVendorId() {
        return implementationVendorId;
    }

    /**
     * Get the implementation URL.
     *
     * @return the implementation URL
     */
    public URI getImplementationUrl() {
        return implementationUrl;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ExtensionListEntry [");
        if (implementationUrl != null)
            builder.append("implementationUrl=").append(implementationUrl).append(", ");
        if (implementationVendorId != null)
            builder.append("implementationVendorId=").append(implementationVendorId).append(", ");
        if (implementationVersion != null)
            builder.append("implementationVersion=").append(implementationVersion).append(", ");
        if (name != null)
            builder.append("name=").append(name).append(", ");
        if (specificationVersion != null)
            builder.append("specificationVersion=").append(specificationVersion).append(", ");
        if (title != null)
            builder.append("title=").append(title);
        builder.append("]");
        return builder.toString();
    }

}
