/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.BCRYPT_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CLEAR_PASSWORD_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SALTED_SIMPLE_DIGEST_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SCRAM_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SIMPLE_DIGEST_MAPPER;
import static org.wildfly.extension.elytron.ElytronExtension.asStringIfDefined;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.operations.validation.StringAllowedValuesValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.inject.InjectionException;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.security.auth.realm.jdbc.JdbcSecurityRealm;
import org.wildfly.security.auth.realm.jdbc.JdbcSecurityRealmBuilder;
import org.wildfly.security.auth.realm.jdbc.KeyMapper;
import org.wildfly.security.auth.realm.jdbc.QueryBuilder;
import org.wildfly.security.auth.realm.jdbc.mapper.AttributeMapper;
import org.wildfly.security.auth.realm.jdbc.mapper.PasswordKeyMapper;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.password.interfaces.BCryptPassword;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.interfaces.SaltedSimpleDigestPassword;
import org.wildfly.security.password.interfaces.ScramDigestPassword;
import org.wildfly.security.password.interfaces.SimpleDigestPassword;

/**
 * A {@link ResourceDefinition} for a {@link SecurityRealm} backed by a database using JDBC.
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
class JdbcRealmDefinition extends SimpleResourceDefinition {

    static final ServiceUtil<SecurityRealm> REALM_SERVICE_UTIL = ServiceUtil.newInstance(SECURITY_REALM_RUNTIME_CAPABILITY, ElytronDescriptionConstants.JDBC_REALM, SecurityRealm.class);

    /**
     * {@link ElytronDescriptionConstants#CLEAR_PASSWORD_MAPPER} complex attribute;
     */
    static class ClearPasswordObjectDefinition implements PasswordMapperObjectDefinition {

        static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING)
                .setRequired(false)
                .setDefaultValue(new ModelNode(ClearPassword.ALGORITHM_CLEAR))
                .setValidator(new StringAllowedValuesValidator(ClearPassword.ALGORITHM_CLEAR))
                .setAllowExpression(false)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PASSWORD_INDEX, ModelType.INT, false)
                .setMinSize(1)
                .setValidator(new IntRangeValidator(1))
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(
                ElytronDescriptionConstants.CLEAR_PASSWORD_MAPPER, PASSWORD)
                .setRequired(false)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        @Override
        public ObjectTypeAttributeDefinition getObjectDefinition() {
            return OBJECT_DEFINITION;
        }

        @Override
        public SimpleAttributeDefinition[] getAttributes() {
            return new SimpleAttributeDefinition[] { PASSWORD };
        }

        @Override
        public PasswordKeyMapper toPasswordKeyMapper(OperationContext context, ModelNode propertyNode) throws OperationFailedException, InvalidKeyException {
            String algorithm = ElytronExtension.asStringIfDefined(context, ALGORITHM, propertyNode);
            int password = ElytronExtension.asIntIfDefined(context, PASSWORD, propertyNode);

            return PasswordKeyMapper
                    .builder()
                    .setDefaultAlgorithm(algorithm)
                    .setHashColumn(password)
                    .build();
        }
    }

    /**
     * {@link ElytronDescriptionConstants#BCRYPT_MAPPER} complex attribute;
     */
    static class BcryptPasswordObjectDefinition implements PasswordMapperObjectDefinition {

        static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING)
                .setRequired(false)
                .setDefaultValue(new ModelNode(BCryptPassword.ALGORITHM_BCRYPT))
                .setValidator(new StringAllowedValuesValidator(BCryptPassword.ALGORITHM_BCRYPT))
                .setAllowExpression(false)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PASSWORD_INDEX, ModelType.INT, false)
                .setMinSize(1)
                .setValidator(new IntRangeValidator(1))
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition ITERATION_COUNT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ITERATION_COUNT_INDEX, ModelType.INT, false)
                .setValidator(new IntRangeValidator(1))
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition SALT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SALT_INDEX, ModelType.INT, false)
                .setValidator(new IntRangeValidator(1))
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(
                ElytronDescriptionConstants.BCRYPT_MAPPER, PASSWORD, SALT, ITERATION_COUNT)
                .setRequired(false)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        @Override
        public ObjectTypeAttributeDefinition getObjectDefinition() {
            return OBJECT_DEFINITION;
        }

        @Override
        public SimpleAttributeDefinition[] getAttributes() {
            return new SimpleAttributeDefinition[] { PASSWORD, SALT, ITERATION_COUNT};
        }

        @Override
        public PasswordKeyMapper toPasswordKeyMapper(OperationContext context, ModelNode propertyNode) throws OperationFailedException, InvalidKeyException {
            String algorithm = ElytronExtension.asStringIfDefined(context, ALGORITHM, propertyNode);
            int password = ElytronExtension.asIntIfDefined(context, PASSWORD, propertyNode);
            int salt = ElytronExtension.asIntIfDefined(context, SALT, propertyNode);
            int iterationCount = ElytronExtension.asIntIfDefined(context, ITERATION_COUNT, propertyNode);

            return PasswordKeyMapper.builder()
                    .setDefaultAlgorithm(algorithm)
                    .setHashColumn(password)
                    .setSaltColumn(salt)
                    .setIterationCountColumn(iterationCount)
                    .build();
        }
    }

    /**
     * {@link ElytronDescriptionConstants#SALTED_SIMPLE_DIGEST_MAPPER} complex attribute;
     */
    static class SaltedSimpleDigestObjectDefinition implements PasswordMapperObjectDefinition {

        static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING)
                .setRequired(false)
                .setDefaultValue(new ModelNode(SaltedSimpleDigestPassword.ALGORITHM_PASSWORD_SALT_DIGEST_MD5))
                .setValidator(new StringAllowedValuesValidator(
                        SaltedSimpleDigestPassword.ALGORITHM_PASSWORD_SALT_DIGEST_MD5,
                        SaltedSimpleDigestPassword.ALGORITHM_PASSWORD_SALT_DIGEST_SHA_1,
                        SaltedSimpleDigestPassword.ALGORITHM_PASSWORD_SALT_DIGEST_SHA_256,
                        SaltedSimpleDigestPassword.ALGORITHM_PASSWORD_SALT_DIGEST_SHA_384,
                        SaltedSimpleDigestPassword.ALGORITHM_PASSWORD_SALT_DIGEST_SHA_512,
                        SaltedSimpleDigestPassword.ALGORITHM_SALT_PASSWORD_DIGEST_MD5,
                        SaltedSimpleDigestPassword.ALGORITHM_SALT_PASSWORD_DIGEST_SHA_1,
                        SaltedSimpleDigestPassword.ALGORITHM_SALT_PASSWORD_DIGEST_SHA_256,
                        SaltedSimpleDigestPassword.ALGORITHM_SALT_PASSWORD_DIGEST_SHA_384,
                        SaltedSimpleDigestPassword.ALGORITHM_SALT_PASSWORD_DIGEST_SHA_512
                ))
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PASSWORD_INDEX, ModelType.INT, false)
                .setMinSize(1)
                .setValidator(new IntRangeValidator(1))
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition SALT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SALT_INDEX, ModelType.INT, false)
                .setValidator(new IntRangeValidator(1))
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(
                ElytronDescriptionConstants.SALTED_SIMPLE_DIGEST_MAPPER, ALGORITHM, PASSWORD, SALT)
                .setRequired(false)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        @Override
        public ObjectTypeAttributeDefinition getObjectDefinition() {
            return OBJECT_DEFINITION;
        }

        @Override
        public SimpleAttributeDefinition[] getAttributes() {
            return new SimpleAttributeDefinition[] { ALGORITHM, PASSWORD, SALT};
        }

        @Override
        public PasswordKeyMapper toPasswordKeyMapper(OperationContext context, ModelNode propertyNode) throws OperationFailedException, InvalidKeyException {
            String algorithm = ElytronExtension.asStringIfDefined(context, ALGORITHM, propertyNode);
            int password = ElytronExtension.asIntIfDefined(context, PASSWORD, propertyNode);
            int salt = ElytronExtension.asIntIfDefined(context, SALT, propertyNode);

            return PasswordKeyMapper.builder()
                    .setDefaultAlgorithm(algorithm)
                    .setHashColumn(password)
                    .setSaltColumn(salt)
                    .build();
        }
    }

    /**
     * {@link ElytronDescriptionConstants#SIMPLE_DIGEST_MAPPER} complex attribute;
     */
    static class SimpleDigestMapperObjectDefinition implements PasswordMapperObjectDefinition {

        static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING)
                .setRequired(false)
                .setDefaultValue(new ModelNode(SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_MD5))
                .setValidator(new StringAllowedValuesValidator(
                        SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_MD2,
                        SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_MD5,
                        SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_SHA_1,
                        SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_SHA_256,
                        SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_SHA_384,
                        SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_SHA_512
                ))
                .setAllowExpression(false)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PASSWORD_INDEX, ModelType.INT, false)
                .setMinSize(1)
                .setValidator(new IntRangeValidator(1))
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(
                ElytronDescriptionConstants.SIMPLE_DIGEST_MAPPER, ALGORITHM, PASSWORD)
                .setRequired(false)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        @Override
        public ObjectTypeAttributeDefinition getObjectDefinition() {
            return OBJECT_DEFINITION;
        }

        @Override
        public SimpleAttributeDefinition[] getAttributes() {
            return new SimpleAttributeDefinition[] { ALGORITHM, PASSWORD };
        }

        @Override
        public PasswordKeyMapper toPasswordKeyMapper(OperationContext context, ModelNode propertyNode) throws OperationFailedException, InvalidKeyException {
            String algorithm = ElytronExtension.asStringIfDefined(context, ALGORITHM, propertyNode);
            int password = ElytronExtension.asIntIfDefined(context, PASSWORD, propertyNode);

            return PasswordKeyMapper.builder()
                    .setDefaultAlgorithm(algorithm)
                    .setHashColumn(password)
                    .build();
        }
    }

    /**
     * {@link ElytronDescriptionConstants#SCRAM_MAPPER} complex attribute;
     */
    static class ScramMapperObjectDefinition implements PasswordMapperObjectDefinition {

        static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING)
                .setRequired(false)
                .setDefaultValue(new ModelNode(ScramDigestPassword.ALGORITHM_SCRAM_SHA_256))
                .setValidator(new StringAllowedValuesValidator(
                        ScramDigestPassword.ALGORITHM_SCRAM_SHA_1,
                        ScramDigestPassword.ALGORITHM_SCRAM_SHA_256
                ))
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PASSWORD_INDEX, ModelType.INT, false)
                .setMinSize(1)
                .setValidator(new IntRangeValidator(1))
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition ITERATION_COUNT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ITERATION_COUNT_INDEX, ModelType.INT, false)
                .setValidator(new IntRangeValidator(1))
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition SALT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SALT_INDEX, ModelType.INT, false)
                .setValidator(new IntRangeValidator(1))
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.SCRAM_MAPPER, ALGORITHM, PASSWORD, SALT, ITERATION_COUNT)
                .setRequired(false)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        @Override
        public ObjectTypeAttributeDefinition getObjectDefinition() {
            return OBJECT_DEFINITION;
        }

        @Override
        public SimpleAttributeDefinition[] getAttributes() {
            return new SimpleAttributeDefinition[] { ALGORITHM, PASSWORD, SALT, ITERATION_COUNT };
        }

        @Override
        public PasswordKeyMapper toPasswordKeyMapper(OperationContext context, ModelNode propertyNode) throws OperationFailedException, InvalidKeyException {
            String algorithm = ElytronExtension.asStringIfDefined(context, ALGORITHM, propertyNode);
            int password = ElytronExtension.asIntIfDefined(context, PASSWORD, propertyNode);
            int salt = ElytronExtension.asIntIfDefined(context, SALT, propertyNode);
            int iterationCount = ElytronExtension.asIntIfDefined(context, ITERATION_COUNT, propertyNode);

            return PasswordKeyMapper.builder()
                    .setDefaultAlgorithm(algorithm)
                    .setHashColumn(password)
                    .setSaltColumn(salt)
                    .setIterationCountColumn(iterationCount)
                    .build();
        }
    }

    interface PasswordMapperObjectDefinition {
        ObjectTypeAttributeDefinition getObjectDefinition();
        SimpleAttributeDefinition[] getAttributes();
        PasswordKeyMapper toPasswordKeyMapper(OperationContext context, ModelNode propertyNode) throws OperationFailedException, InvalidKeyException;
    }

    static class AttributeMappingObjectDefinition {
        static final SimpleAttributeDefinition INDEX = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.INDEX, ModelType.INT, false)
                .setAllowExpression(true)
                .setValidator(new IntRangeValidator(1))
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition TO = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.TO, ModelType.STRING, false)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition[] ATTRIBUTES = new SimpleAttributeDefinition[] {TO, INDEX};

        static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.ATTRIBUTE, ATTRIBUTES)
                .build();
    }

    /**
     * {@link ElytronDescriptionConstants#PRINCIPAL_QUERY} complex attribute.
     */
    static class PrincipalQueryAttributes {
        static final SimpleAttributeDefinition SQL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SQL, ModelType.STRING, false)
                .setAllowExpression(true)
                .setMinSize(1)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition DATA_SOURCE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DATA_SOURCE, ModelType.STRING, false)
                .setMinSize(1)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilityReference(Capabilities.DATA_SOURCE_CAPABILITY_NAME, Capabilities.SECURITY_REALM_CAPABILITY, true)
                .build();

        static final ObjectListAttributeDefinition ATTRIBUTE_MAPPINGS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.ATTRIBUTE_MAPPING, AttributeMappingObjectDefinition.OBJECT_DEFINITION)
                .setRequired(false)
                .setAttributeGroup(ElytronDescriptionConstants.ATTRIBUTE)
                .setAllowDuplicates(true)
                .build();

        static Map<String, PasswordMapperObjectDefinition> SUPPORTED_PASSWORD_MAPPERS;

        static {
            Map<String, PasswordMapperObjectDefinition> supportedMappers = new HashMap<>();

            supportedMappers.put(CLEAR_PASSWORD_MAPPER, new ClearPasswordObjectDefinition());
            supportedMappers.put(BCRYPT_MAPPER, new BcryptPasswordObjectDefinition());
            supportedMappers.put(SALTED_SIMPLE_DIGEST_MAPPER, new SaltedSimpleDigestObjectDefinition());
            supportedMappers.put(SIMPLE_DIGEST_MAPPER, new SimpleDigestMapperObjectDefinition());
            supportedMappers.put(SCRAM_MAPPER, new ScramMapperObjectDefinition());

            SUPPORTED_PASSWORD_MAPPERS = Collections.unmodifiableMap(supportedMappers);
        }

        static final ObjectTypeAttributeDefinition PRINCIPAL_QUERY = new ObjectTypeAttributeDefinition.Builder(
                ElytronDescriptionConstants.PRINCIPAL_QUERY,
                SQL,
                DATA_SOURCE,
                ATTRIBUTE_MAPPINGS,
                ClearPasswordObjectDefinition.OBJECT_DEFINITION,
                BcryptPasswordObjectDefinition.OBJECT_DEFINITION,
                SaltedSimpleDigestObjectDefinition.OBJECT_DEFINITION,
                SimpleDigestMapperObjectDefinition.OBJECT_DEFINITION,
                ScramMapperObjectDefinition.OBJECT_DEFINITION)
                .setRequired(false)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final ObjectListAttributeDefinition PRINCIPAL_QUERIES = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.PRINCIPAL_QUERY, PRINCIPAL_QUERY)
                .setMinSize(1)
                .setAllowDuplicates(true)
                .setRestartAllServices()
                .build();
    }

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {PrincipalQueryAttributes.PRINCIPAL_QUERIES};

    private static final AbstractAddStepHandler ADD = new RealmAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, SECURITY_REALM_RUNTIME_CAPABILITY);
    private static final OperationStepHandler WRITE = new ElytronReloadRequiredWriteAttributeHandler(ATTRIBUTES);

    JdbcRealmDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.JDBC_REALM), ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.JDBC_REALM))
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(SECURITY_REALM_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition current : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, WRITE);
        }
    }

    private static class RealmAddHandler extends BaseAddHandler {

        private RealmAddHandler() {
            super(SECURITY_REALM_RUNTIME_CAPABILITY, ATTRIBUTES);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();
            RuntimeCapability<Void> runtimeCapability = SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName realmName = runtimeCapability.getCapabilityServiceName(SecurityRealm.class);
            ModelNode principalQueries = PrincipalQueryAttributes.PRINCIPAL_QUERIES.resolveModelAttribute(context, operation);
            final JdbcSecurityRealmBuilder builder = JdbcSecurityRealm.builder();

            TrivialService<SecurityRealm> service = new TrivialService<SecurityRealm>(builder::build);
            ServiceBuilder<SecurityRealm> serviceBuilder = serviceTarget.addService(realmName, service);

            for (ModelNode query : principalQueries.asList()) {
                String authenticationQuerySql = asStringIfDefined(context, PrincipalQueryAttributes.SQL, query);
                QueryBuilder queryBuilder = builder.principalQuery(authenticationQuerySql)
                        .withMapper(resolveAttributeMappers(context, query))
                        .withMapper(resolveKeyMappers(context, query));

                String dataSourceName = asStringIfDefined(context, PrincipalQueryAttributes.DATA_SOURCE, query);
                String capabilityName = Capabilities.DATA_SOURCE_CAPABILITY_NAME + "." + dataSourceName;
                ServiceName dataSourceServiceName = context.getCapabilityServiceName(capabilityName, DataSource.class);

                serviceBuilder.addDependency(dataSourceServiceName, DataSource.class, new Injector<DataSource>() {

                    @Override
                    public void inject(DataSource value) throws InjectionException {
                        queryBuilder.from(value);
                    }

                    @Override
                    public void uninject() {
                        // no-op
                    }
                });
            }

            commonDependencies(serviceBuilder)
                    .setInitialMode(ServiceController.Mode.ACTIVE)
                    .install();
        }

        private AttributeMapper[] resolveAttributeMappers(OperationContext context, ModelNode principalQueryNode) throws OperationFailedException {
            List<AttributeMapper> attributeMappers = new ArrayList<>();

            ModelNode attributeMappingNode = PrincipalQueryAttributes.ATTRIBUTE_MAPPINGS.resolveModelAttribute(context, principalQueryNode);

            if (attributeMappingNode.isDefined()) {
                for (ModelNode attributeNode : attributeMappingNode.asList()) {
                    ModelNode indexNode = AttributeMappingObjectDefinition.INDEX.resolveModelAttribute(context, attributeNode);
                    ModelNode nameNode = AttributeMappingObjectDefinition.TO.resolveModelAttribute(context, attributeNode);

                    attributeMappers.add(new AttributeMapper(indexNode.asInt(), nameNode.asString()));
                }
            }

            return attributeMappers.toArray(new AttributeMapper[attributeMappers.size()]);
        }
    }

    private static KeyMapper resolveKeyMappers(OperationContext context, ModelNode authenticationQueryNode) throws OperationFailedException {
        KeyMapper keyMapper = null;

        for (String name : authenticationQueryNode.keys()) {
            ModelNode propertyNode = authenticationQueryNode.require(name);

            if (!propertyNode.isDefined()) {
                continue;
            }

            PasswordMapperObjectDefinition mapperResource = PrincipalQueryAttributes.SUPPORTED_PASSWORD_MAPPERS.get(name);

            if (mapperResource == null) {
                continue;
            }

            if (keyMapper != null) {
                throw ROOT_LOGGER.jdbcRealmOnlySingleKeyMapperAllowed();
            }

            try {
                keyMapper = mapperResource.toPasswordKeyMapper(context, propertyNode);
            } catch (InvalidKeyException e) {
                throw new OperationFailedException("Invalid key type.", e);
            }
        }

        return keyMapper;
    }
}
