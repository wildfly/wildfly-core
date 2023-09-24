/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.CLASS_NAMES;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.MODULE;

import java.security.Provider;
import java.security.Provider.Service;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Class to contain the attribute definition for the runtime representation of a security provider.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ProviderAttributeDefinition {

    private static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NAME, ModelType.STRING).build();

    private static final SimpleAttributeDefinition INFO = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.INFO, ModelType.STRING).build();

    private static final SimpleAttributeDefinition VERSION = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.VERSION, ModelType.DOUBLE).build();

    static final ObjectTypeAttributeDefinition LOADED_PROVIDER = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.LOADED_PROVIDER, NAME, INFO, VERSION)
        .setStorageRuntime()
        .setRequired(false)
        .build();

    /*
     * Service Attributes and Full Definition.
     */

    private static final SimpleAttributeDefinition TYPE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.TYPE, ModelType.STRING).build();

    private static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING).build();

    private static final SimpleAttributeDefinition CLASS_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CLASS_NAME, ModelType.STRING)
        .setAllowExpression(false)
        .build();

    private static final ObjectTypeAttributeDefinition SERVICE = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.SERVICE, TYPE, ALGORITHM, CLASS_NAME)
        .setRequired(true)
        .build();

    private static final ObjectListAttributeDefinition SERVICES = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.SERVICES, SERVICE)
        .build();

    private static final ObjectTypeAttributeDefinition FULL_PROVIDER = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.PROVIDER, NAME, INFO, VERSION, SERVICES)
        .setRequired(false)
        .build();

    static final ObjectListAttributeDefinition LOADED_PROVIDERS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.LOADED_PROVIDERS, FULL_PROVIDER)
        .setStorageRuntime()
        .setRequired(false)
        .build();

    /*
     *  Provider Configuration Attributes
     */

    private static final SimpleAttributeDefinition INDEX = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.INDEX, ModelType.INT)
        .build();

    private static final SimpleAttributeDefinition LOAD_SERVICES = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.LOAD_SERVICES, ModelType.BOOLEAN)
        .setAttributeGroup(ElytronDescriptionConstants.CLASS_LOADING)
        .setAllowExpression(true)
        .setRequired(false)
        .setDefaultValue(ModelNode.FALSE)
        .build();

    private static final SimpleAttributeDefinition PROPERTY_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NAME, ModelType.STRING, false)
        .setAllowExpression(true)
        .setMinSize(1)
        .build();

    private static final SimpleAttributeDefinition VALUE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.VALUE, ModelType.STRING, false)
        .setAllowExpression(true)
        .setMinSize(1)
        .build();

    private static final AttributeDefinition[] PROPERTY_ATTRIBUTES = { PROPERTY_NAME, VALUE };

    private static final ObjectTypeAttributeDefinition PROPERTY = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.PROPERTY, PROPERTY_ATTRIBUTES)
        .build();

    private static final AttributeDefinition[] INDEXED_PROPERTY_ATTRIBUTES = combine(INDEX, PROPERTY_ATTRIBUTES);

    private static final ObjectTypeAttributeDefinition INDEXED_PROPERTY = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.PROPERTY, INDEXED_PROPERTY_ATTRIBUTES)
        .build();

    private static final ObjectListAttributeDefinition PROPERTY_LIST = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.PROPERTY_LIST, PROPERTY)
        .setAttributeGroup(ElytronDescriptionConstants.CONFIGURATION)
        .setAlternatives(ElytronDescriptionConstants.PATH)
        .setAllowDuplicates(true)
        .setRequired(false)
        .build();

    private static final ObjectListAttributeDefinition INDEXED_PROPERTY_LIST = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.PROPERTY_LIST, INDEXED_PROPERTY)
        .setAttributeGroup(ElytronDescriptionConstants.CONFIGURATION)
        .setAlternatives(ElytronDescriptionConstants.PATH)
        .setAllowDuplicates(true)
        .setRequired(false)
        .build();

    private static final SimpleAttributeDefinition PATH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PATH, FileAttributeDefinitions.PATH)
        .setAttributeGroup(ElytronDescriptionConstants.CONFIGURATION)
        .setAlternatives(ElytronDescriptionConstants.PROPERTY_LIST)
        .build();

    private static final SimpleAttributeDefinition RELATIVE_TO = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.RELATIVE_TO, FileAttributeDefinitions.RELATIVE_TO)
        .setAttributeGroup(ElytronDescriptionConstants.CONFIGURATION)
        .build();

    private static final AttributeDefinition[] PROVIDER_ATTRIBUTES = { MODULE, LOAD_SERVICES, CLASS_NAMES, PATH, RELATIVE_TO };

    private static final ObjectTypeAttributeDefinition PROVIDER = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.PROVIDER, combine(null, PROVIDER_ATTRIBUTES, PROPERTY_LIST))
        .build();

    private static final ObjectTypeAttributeDefinition INDEXED_PROVIDER = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.PROVIDER, combine(INDEX, PROVIDER_ATTRIBUTES, INDEXED_PROPERTY_LIST))
        .build();

    static final ObjectListAttributeDefinition PROVIDERS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.PROVIDERS, PROVIDER)
        .setRequired(false)
        .build();

    static final ObjectListAttributeDefinition INDEXED_PROVIDERS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.PROVIDERS, INDEXED_PROVIDER)
        .setRequired(false)
        .build();


    private ProviderAttributeDefinition() {
    }

    /**
     * Populate the supplied response {@link ModelNode} with information about the supplied {@link Provider}
     *
     * @param response the response to populate.
     * @param provider the {@link Provider} to use when populating the response.
     */
    static void populateProvider(final ModelNode response, final Provider provider, final boolean includeServices) {
        response.get(ElytronDescriptionConstants.NAME).set(provider.getName());
        response.get(ElytronDescriptionConstants.INFO).set(provider.getInfo());
        response.get(ElytronDescriptionConstants.VERSION).set(provider.getVersion());

        if (includeServices) {
            addServices(response, provider);
        }
    }

    /**
     * Populate the supplied response {@link ModelNode} with information about each {@link Provider} in the included array.
     *
     * @param response the response to populate.
     * @param providers the array or {@link Provider} instances to use to populate the response.
     */
    static void populateProviders(final ModelNode response, final Provider[] providers) {
        for (Provider current : providers) {
            ModelNode providerModel = new ModelNode();
            populateProvider(providerModel, current, true);
            response.add(providerModel);
        }
    }

    private static void addServices(final ModelNode providerModel, final Provider provider) {
        ModelNode servicesModel = providerModel.get(ElytronDescriptionConstants.SERVICES);

        for (Service current : provider.getServices()) {
            ModelNode serviceModel = new ModelNode();
            serviceModel.get(ElytronDescriptionConstants.TYPE).set(current.getType());
            serviceModel.get(ElytronDescriptionConstants.ALGORITHM).set(current.getAlgorithm());
            serviceModel.get(ElytronDescriptionConstants.CLASS_NAME).set(current.getClassName());

            servicesModel.add(serviceModel);
        }

    }

    private static AttributeDefinition[] combine(AttributeDefinition first, AttributeDefinition[] remaining, AttributeDefinition... more) {
        AttributeDefinition[] response = new AttributeDefinition[(first == null ? 0 : 1) + remaining.length + more.length];
        int pos = 0;
        if (first != null) {
            response[pos++] = first;
        }
        System.arraycopy(remaining, 0, response, pos, remaining.length);
        pos += remaining.length;
        System.arraycopy(more, 0, response, pos, more.length);
        return response;
    }

}
