/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test;

import java.io.IOException;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.dmr.ModelNode;

/**
 * A builder to create a controller and initialize it with the passed in subsystem xml or boot operations.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface KernelServicesBuilder {

    /**
     * The default is to validate the operations sent in to the model controller. Turn it off call this method
     *
     * @return this builder
     */
    KernelServicesBuilder setDontValidateOperations();


    /**
     * By default the description is not validated. Call this to validates the full model description minus what is set up by {@link KnownIssuesValidationConfiguration}
     *
     * @return this builder
     */
    KernelServicesBuilder validateDescription();

    /**
     * Register an arbitrary descriptor for description validation.
     *
     * @return this builder
     */
    KernelServicesBuilder registerAttributeArbitraryDescriptor(ModelNode address, String attribute, String descriptor);

    /**
     * Register an arbitrary descriptor for description validation.
     *
     * @return this builder
     */
    KernelServicesBuilder registerArbitraryDescriptorForOperationParameter(ModelNode address, String operation, String parameter, String descriptor);

    /**
     * Sets the subsystem xml resource containing the xml to be parsed to create the boot operations used to initialize the controller. The resource is loaded using similar
     * semantics to {@link Class#getResource(String)}
     * @param resource the resource with subsystem xml
     * @return this builder
     * @throws IllegalStateException if {@link #setBootOperations(List)}, {@link #setXml(String)} (String)} or {@link #setXmlResource(String)} (String)} have
     * already been called
     * @throws IllegalStateException if {@link #build()} has already been called
     * @throws IOException if there were problems reading the resource
     * @throws XMLStreamException if there were problems parsing the xml
     */
    KernelServicesBuilder setXmlResource(String resource) throws IOException, XMLStreamException;

    /**
     * Sets the subsystem xml to be parsed to create the boot operations used to initialize the controller
     * @param subsystemXml the subsystem xml
     * @return this builder
     * @throws IllegalStateException if {@link #setBootOperations(List)}, {@link #setSubsystemXml(String)} or {@link #setSubsystemXmlResource(String)} have
     * already been called
     * @throws IllegalStateException if {@link #build()} has already been called
     * @throws XMLStreamException if there were problems parsing the xml
     */
    KernelServicesBuilder setXml(String subsystemXml) throws XMLStreamException;

    /**
     * Sets the boot operations to be used to initialize the controller
     * @param bootOperations the boot operations
     * @return this builder
     * @throws IllegalStateException if {@link #setBootOperations(List)}, {@link #setSubsystemXml(String)} or {@link #setSubsystemXmlResource(String)} have
     * @throws IllegalStateException if {@link #build()} has already been called
     * already been called
     */
    KernelServicesBuilder setBootOperations(List<ModelNode> bootOperations);

    /**
     * Adds a model initializer which can be used to initialize the model before executing the boot operations, and a model write sanitizer
     * which can be used to remove resources from the model for the xml comparison tests.
     *
     * @param modelInitializer the model initilizer
     * @param modelWriteSanitizer the model write sanitizer
     * @return this builder
     * @throws IllegalStateException if the model initializer was already set or if {@link #build()} has already been called
     */
    KernelServicesBuilder setModelInitializer(ModelInitializer modelInitializer, ModelWriteSanitizer modelWriteSanitizer);

    KernelServicesBuilder createContentRepositoryContent(String hash);

    /**
     * Creates a new legacy kernel services initializer used to configure a new controller containing an older version of the subsystem being tested.
     * When {@link #build()} is called any legacy controllers will be created as well.
     *
     * @param modelVersion The model version of the legacy subsystem
     * @param testControllerVersion the version of the legacy controller to load up
     * @return the legacy kernel services initializer
     * @throws IllegalStateException if {@link #build()} has already been called
     */
     LegacyKernelServicesInitializer createLegacyKernelServicesBuilder(ModelVersion modelVersion, ModelTestControllerVersion testControllerVersion);

    /**
     * Creates the controller and initializes it with the passed in configuration options.
     * If {@link #createLegacyKernelServicesBuilder(org.jboss.as.controller.ModelVersion, org.jboss.as.core.model.test.LegacyKernelServicesInitializer.TestControllerVersion)} (ModelVersion)} was called kernel services will be created for the legacy subsystem
     * controllers as well, accessible from {@link KernelServices#getLegacyServices(ModelVersion)} on the created {@link KernelServices}
     *
     * @return the kernel services wrapping the controller
     */
    KernelServices build() throws Exception;

    /**
     * Parses the given xml into operations. This may be called after {@link #build()} has been called.
     *
     * @param xml a string containing the xml
     * @return the parsed operations
     */
    List<ModelNode> parseXml(String xml) throws Exception;

    /**
     * Parses the given xml into operations. The resource is loaded using similar semantics to {@link Class#getResource(String)}.
     * This may be called after {@link #build()} has been called.
     *
     * @param xml a string containing the xml resource
     * @return the parsed operations
     */
    List<ModelNode> parseXmlResource(String xmlResource) throws Exception;
}
