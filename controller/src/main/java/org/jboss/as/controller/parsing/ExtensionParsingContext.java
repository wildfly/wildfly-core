/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.parsing;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.jboss.as.controller.FeatureRegistry;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.extension.UnstableSubsystemNamespaceParser;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;

/**
 * Context in effect when the {@code extension} element for a given {@link org.jboss.as.controller.Extension} is being parsed. Allows the
 * extension to {@link org.jboss.as.controller.Extension#initializeParsers(ExtensionParsingContext) initialize the XML parsers} that can
 * be used for parsing the {@code subsystem} elements that contain the configuration for its subsystems.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ExtensionParsingContext extends FeatureRegistry {

    /**
     * Gets the type of the current process.
     * @return the current process type. Will not be {@code null}
     */
    ProcessType getProcessType();

    /**
     * Gets the current running mode of the process.
     * @return the current running mode. Will not be {@code null}
     */
    RunningMode getRunningMode();

    /**
     * Set the parser for the profile-wide subsystem configuration XML element.  The element is always
     * called {@code "subsystem"}.  The reader should populate the given model node with the appropriate
     * "subsystem add" update, without the address or operation name as that information will be automatically
     * populated.
     *
     * @param subsystemName the name of the subsystem. Cannot be {@code null}
     * @param namespaceUri the URI of the subsystem's XML namespace, in string form. Cannot be {@code null}
     * @param reader the element reader. Cannot be {@code null}
     *
     * @throws IllegalStateException if another {@link org.jboss.as.controller.Extension} has already registered a subsystem with the given
     *                               {@code subsystemName}
     */
    void setSubsystemXmlMapping(String subsystemName, String namespaceUri, XMLElementReader<List<ModelNode>> reader);

    /**
      * Set the parser for the profile-wide subsystem configuration XML element.  The element is always
      * called {@code "subsystem"}.  The reader should populate the given model node with the appropriate
      * "subsystem add" update, without the address or operation name as that information will be automatically
      * populated.
      * It is recommended that supplier always creates new instance of the {@link XMLElementReader}
      * instead of caching and returning always same instance.
      *
      * @param subsystemName the name of the subsystem. Cannot be {@code null}
      * @param namespaceUri the URI of the sussystem's XML namespace, in string form. Cannot be {@code null}
      * @param supplier of the element reader. Cannot be {@code null}
      *
      * @throws IllegalStateException if another {@link org.jboss.as.controller.Extension} has already registered a subsystem with the given
      *                               {@code subsystemName}
      */
    void setSubsystemXmlMapping(String subsystemName, String namespaceUri, Supplier<XMLElementReader<List<ModelNode>>> supplier);

    /**
     * Set the parser for the profile-wide subsystem configuration XML element.  The element is always
     * called {@code "subsystem"}.  The reader of the schema should populate the given model node with the appropriate
     * "subsystem add" update, without the address or operation name as that information will be automatically
     * populated.
     *
     * @param <S> the schema type
     * @param subsystemName the name of the subsystem. Cannot be {@code null}
     * @param schemas a set of schemas to be registered
     */
    default <S extends SubsystemSchema<S>> void setSubsystemXmlMappings(String subsystemName, Set<S> schemas) {
        for (S schema : schemas) {
            XMLElementReader<List<ModelNode>> reader = this.enables(schema) ? schema : new UnstableSubsystemNamespaceParser(subsystemName);
            this.setSubsystemXmlMapping(subsystemName, schema.getNamespace().getUri(), reader);
        }
    }

    /**
     * Registers a {@link ProfileParsingCompletionHandler} to receive a callback upon completion of parsing of a
     * profile.
     *
     * @param handler the handler. Cannot be {@code null}
     */
    void setProfileParsingCompletionHandler(ProfileParsingCompletionHandler handler);
}
