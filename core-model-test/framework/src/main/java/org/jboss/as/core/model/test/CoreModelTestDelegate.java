/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILDREN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_CONTROLLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FIXED_PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FIXED_SOURCE_PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_ALIASES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_DEFAULTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INHERITED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IS_DOMAIN_CONTROLLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOCAL_DESTINATION_OUTBOUND_SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MICRO_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MINOR_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_SUBSYSTEM_ENDPOINT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODEL_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAMESPACES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PLATFORM_MBEAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ONLY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELEASE_CODENAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELEASE_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOTE_DESTINATION_OUTBOUND_SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SCHEMA_LOCATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.core.model.bridge.impl.LegacyControllerKernelServicesProxy;
import org.jboss.as.core.model.bridge.local.ScopedKernelServicesBootstrap;
import org.jboss.as.host.controller.HostRunningModeControl;
import org.jboss.as.host.controller.RestartMode;
import org.jboss.as.host.controller.operations.HostAddHandler;
import org.jboss.as.host.controller.operations.LocalDomainControllerAddHandler;
import org.jboss.as.host.controller.operations.RemoteDomainControllerAddHandler;
import org.jboss.as.model.test.ChildFirstClassLoaderBuilder;
import org.jboss.as.model.test.ModelFixer;
import org.jboss.as.model.test.ModelTestBootOperationsBuilder;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.ModelTestModelDescriptionValidator;
import org.jboss.as.model.test.ModelTestModelDescriptionValidator.ValidationConfiguration;
import org.jboss.as.model.test.ModelTestModelDescriptionValidator.ValidationFailure;
import org.jboss.as.model.test.ModelTestOperationValidatorFilter;
import org.jboss.as.model.test.ModelTestOperationValidatorFilter.Action;
import org.jboss.as.version.Stability;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLMapper;
import org.junit.Assert;
import org.wildfly.common.xml.XMLInputFactoryUtil;
import org.wildfly.legacy.test.spi.Version;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class CoreModelTestDelegate {
    public static final ModelTestModelDescriptionValidator.AttributeOrParameterArbitraryDescriptorValidator ARBITRARY_DESCRIPTOR_VALIDATOR = (ModelType currentType, ModelNode currentNode, String descriptor) -> null;
    private static final Set<PathAddress> EMPTY_RESOURCE_ADDRESSES = new HashSet<PathAddress>();
    private static final Set<PathAddress> MISSING_NAME_ADDRESSES = new HashSet<PathAddress>();

    static {
        EMPTY_RESOURCE_ADDRESSES.add(PathAddress.pathAddress(PathElement.pathElement(PROFILE)));
        EMPTY_RESOURCE_ADDRESSES.add(PathAddress.pathAddress(PathElement.pathElement(DEPLOYMENT_OVERLAY), PathElement.pathElement(DEPLOYMENT)));
        EMPTY_RESOURCE_ADDRESSES.add(PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP),
                PathElement.pathElement(DEPLOYMENT_OVERLAY), PathElement.pathElement(DEPLOYMENT)));

        MISSING_NAME_ADDRESSES.add(PathAddress.pathAddress(PathElement.pathElement(PROFILE)));
        MISSING_NAME_ADDRESSES.add(PathAddress.pathAddress(PathElement.pathElement(INTERFACE)));
        MISSING_NAME_ADDRESSES.add(PathAddress.pathAddress(PathElement.pathElement(PATH)));
        MISSING_NAME_ADDRESSES.add(PathAddress.pathAddress(PathElement.pathElement(DEPLOYMENT)));
        MISSING_NAME_ADDRESSES.add(PathAddress.pathAddress(PathElement.pathElement(SERVER_GROUP), PathElement.pathElement(DEPLOYMENT)));
    }

    private final Class<?> testClass;
    private final List<KernelServices> kernelServices = new ArrayList<KernelServices>();
    //Gets set by TransformersTestParameterized for transformers tests. Non transformers tests do not set this
    private volatile ClassloaderParameter currentTransformerClassloaderParameter;

    public CoreModelTestDelegate(Class<?> testClass) {
        this.testClass = testClass;
    }

    void initializeParser() throws Exception {
        //Initialize the parser

    }

    void setCurrentTransformerClassloaderParameter(ClassloaderParameter parameter) {
        ClassloaderParameter current = currentTransformerClassloaderParameter;
        if (current != null) {
            if (current != parameter) {
                //Clear the cached classloader
                current.setClassLoader(null);
                currentTransformerClassloaderParameter = parameter;
            }
        } else {
            currentTransformerClassloaderParameter = parameter;
        }
    }

    void cleanup() throws Exception {
        for (KernelServices kernelServices : this.kernelServices) {
            try {
                kernelServices.shutdown();
            } catch (Exception e) {
            }
        }
        kernelServices.clear();
    }


    protected KernelServicesBuilder createKernelServicesBuilder(TestModelType type, Stability stability) {
        return new KernelServicesBuilderImpl(type, stability);
    }

    private void validateDescriptionProviders(TestModelType type, KernelServices kernelServices,
            Map<ModelNode, Map<String, Set<String>>> attributeDescriptors,
            Map<ModelNode, Map<String, Map<String, Set<String>>>> operationParameterDescriptors) {
        ModelNode op = new ModelNode();
        op.get(OP).set(READ_RESOURCE_DESCRIPTION_OPERATION);
        op.get(OP_ADDR).setEmptyList();
        op.get(RECURSIVE).set(true);
        op.get(INHERITED).set(false);
        op.get(OPERATIONS).set(true);
        op.get(INCLUDE_ALIASES).set(true);
        ModelNode result = kernelServices.executeOperation(op);
        if (result.hasDefined(FAILURE_DESCRIPTION)) {
            throw new RuntimeException(result.get(FAILURE_DESCRIPTION).toString());
        }
        ModelNode model = result.get(RESULT);

        if (type == TestModelType.HOST) {
            //TODO (1)
            //Big big hack to get around the fact that the tests install the host description twice
            //we're only interested in the host model anyway
            //See KnownIssuesValidator.createHostPlatformMBeanAddress
            model = model.require(CHILDREN).require(HOST).require(MODEL_DESCRIPTION).require("primary");
        }

        //System.out.println(model);

        ValidationConfiguration config = KnownIssuesValidationConfiguration.createAndFixupModel(type, model);
        for(Map.Entry<ModelNode, Map<String, Set<String>>> attributeDescriptor : attributeDescriptors.entrySet()) {
            ModelNode address = attributeDescriptor.getKey();
            Map<String, Set<String>> descriptors = attributeDescriptor.getValue();
            for(Map.Entry<String, Set<String>> descriptor : descriptors.entrySet()) {
                String attributeName = descriptor.getKey();
                Set<String> arbitraryAttributeDescriptors =  descriptor.getValue();
                for(String arbitraryAttributeDescriptor : arbitraryAttributeDescriptors) {
                    config.registerAttributeArbitraryDescriptor(address, attributeName, arbitraryAttributeDescriptor, ARBITRARY_DESCRIPTOR_VALIDATOR);
                }
            }
        }
        for (Map.Entry<ModelNode, Map<String, Map<String, Set<String>>>> operationParameterDescriptor : operationParameterDescriptors.entrySet()) {
            ModelNode address = operationParameterDescriptor.getKey();
            Map<String, Map<String, Set<String>>> operationDescriptors = operationParameterDescriptor.getValue();
            for (Map.Entry<String, Map<String, Set<String>>> operationDescriptor : operationDescriptors.entrySet()) {
                String operationName = operationDescriptor.getKey();
                Map<String, Set<String>> parameterDescriptors = operationDescriptor.getValue();
                for (Map.Entry<String, Set<String>> parameterDescriptor : parameterDescriptors.entrySet()) {
                    String parameter = parameterDescriptor.getKey();
                    Set<String> arbitraryAttributeDescriptors = parameterDescriptor.getValue();
                    for (String arbitraryAttributeDescriptor : arbitraryAttributeDescriptors) {
                        config.registerArbitraryDescriptorForOperationParameter(address, operationName, parameter, arbitraryAttributeDescriptor, ARBITRARY_DESCRIPTOR_VALIDATOR);
                    }
                }
            }
        }
        ModelTestModelDescriptionValidator validator = new ModelTestModelDescriptionValidator(PathAddress.EMPTY_ADDRESS.toModelNode(), model, config);
        List<ValidationFailure> validationMessages = validator.validateResources();
        if (!validationMessages.isEmpty()) {
            final StringBuilder builder = new StringBuilder("VALIDATION ERRORS IN MODEL:");
            for (ValidationFailure failure : validationMessages) {
                builder.append(failure);
                builder.append("\n");

            }
            Assert.fail("Failed due to validation errors in the model. Please fix :-) " + builder.toString());
        }
    }

    /**
     * Checks that the transformed model is the same as the model built up in the legacy subsystem controller via the transformed operations,
     * and that the transformed model is valid according to the resource definition in the legacy subsystem controller.
     *
     * @param kernelServices the main kernel services
     * @param modelVersion   the model version of the targeted legacy subsystem
     * @param legacyModelFixer use to touch up the model read from the legacy controller, use sparingly when the legacy model is just wrong. May be {@code null}
     * @return the whole model of the legacy controller
     */
    ModelNode checkCoreModelTransformation(KernelServices kernelServices, ModelVersion modelVersion, ModelFixer legacyModelFixer, ModelFixer transformedModelFixer) throws IOException {
        KernelServices legacyServices = kernelServices.getLegacyServices(modelVersion);

        //Only read the model without any defaults
        ModelNode op = new ModelNode();
        op.get(OP_ADDR).setEmptyList();
        op.get(OP).set(READ_RESOURCE_OPERATION);
        op.get(RECURSIVE).set(true);
        op.get(INCLUDE_DEFAULTS).set(false);
        ModelNode legacyModel;
        try {
            legacyModel = legacyServices.executeForResult(op);
        } catch (OperationFailedException e) {
            throw new RuntimeException(e);
        }

        //Work around known problem where the recursive :read-resource on legacy controllers in ModelVersion < 1.4.0
        //incorrectly does not propagate include-defaults=true when recursing
        //https://issues.jboss.org/browse/AS7-6077
        removeDefaultAttributesWronglyShowingInRecursiveReadResource(modelVersion, legacyServices, legacyModel);

        //1) Check that the transformed model is the same as the whole model read from the legacy controller.
        //The transformed model is done via the resource transformers
        //The model in the legacy controller is built up via transformed operations
        ModelNode transformed = kernelServices.readTransformedModel(modelVersion);

        adjustUndefinedInTransformedToEmpty(modelVersion, legacyModel, transformed);

        if (legacyModelFixer != null) {
            legacyModel = legacyModelFixer.fixModel(legacyModel);
        }
        if (transformedModelFixer != null) {
            transformed = transformedModelFixer.fixModel(transformed);
        }

        //TODO temporary hacks
        temporaryHack(transformed, legacyModel);

        ModelTestUtils.compare(legacyModel, transformed, true);

        //2) Check that the transformed model is valid according to the resource definition in the legacy subsystem controller
        //ResourceDefinition rd = TransformerRegistry.loadSubsystemDefinition(mainSubsystemName, modelVersion);
        //ManagementResourceRegistration rr = ManagementResourceRegistration.Factory.create(rd);
        //ModelTestUtils.checkModelAgainstDefinition(transformed, rr);
        return legacyModel;
    }

    private void temporaryHack(ModelNode transformedModel, ModelNode legacyModel) {
        if (legacyModel.hasDefined(NAMESPACES) && !transformedModel.hasDefined(NAMESPACES)) {
            if (legacyModel.get(NAMESPACES).asList().isEmpty()) {
                legacyModel.get(NAMESPACES).set(new ModelNode());
            }
        }
        if (legacyModel.hasDefined(SCHEMA_LOCATIONS) && !transformedModel.hasDefined(SCHEMA_LOCATIONS)) {
            if (legacyModel.get(SCHEMA_LOCATIONS).asList().isEmpty()) {
                legacyModel.get(SCHEMA_LOCATIONS).set(new ModelNode());
            }
        }


        //We will test these in mixed-domain instead since something differs in the test setup for these attributes
        legacyModel.remove(MANAGEMENT_MAJOR_VERSION);
        legacyModel.remove(MANAGEMENT_MINOR_VERSION);
        legacyModel.remove(MANAGEMENT_MICRO_VERSION);
        legacyModel.remove(NAME);
        legacyModel.remove(RELEASE_CODENAME);
        legacyModel.remove(RELEASE_VERSION);
        transformedModel.remove(MANAGEMENT_MAJOR_VERSION);
        transformedModel.remove(MANAGEMENT_MINOR_VERSION);
        transformedModel.remove(MANAGEMENT_MICRO_VERSION);
        transformedModel.remove(NAME);
        transformedModel.remove(RELEASE_CODENAME);
        transformedModel.remove(RELEASE_VERSION);
    }

    private void removeDefaultAttributesWronglyShowingInRecursiveReadResource(ModelVersion modelVersion, KernelServices legacyServices, ModelNode legacyModel) {

        if (modelVersion.getMajor() == 1 && modelVersion.getMinor() < 4) {
            //Work around known problem where the recursice :read-resource on legacy controllers in ModelVersion < 1.4.0
            //incorrectly does not propagate include-defaults=true when recursing
            //https://issues.jboss.org/browse/AS7-6077
            checkAttributeIsActuallyDefinedAndReplaceIfNot(legacyServices, legacyModel, MANAGEMENT_SUBSYSTEM_ENDPOINT, SERVER_GROUP);
            checkAttributeIsActuallyDefinedAndReplaceIfNot(legacyServices, legacyModel, READ_ONLY, PATH);
            removeDefaultAttributesWronglyShowingInRecursiveReadResourceInSocketBindingGroup(modelVersion, legacyServices, legacyModel);
        }
    }

    private void removeDefaultAttributesWronglyShowingInRecursiveReadResourceInSocketBindingGroup(ModelVersion modelVersion, KernelServices legacyServices, ModelNode legacyModel) {
        if (legacyModel.hasDefined(SOCKET_BINDING_GROUP)) {
            for (Property prop : legacyModel.get(SOCKET_BINDING_GROUP).asPropertyList()) {
                if (prop.getValue().isDefined()) {
                    checkAttributeIsActuallyDefinedAndReplaceIfNot(legacyServices, legacyModel, FIXED_SOURCE_PORT, SOCKET_BINDING_GROUP, prop.getName(), REMOTE_DESTINATION_OUTBOUND_SOCKET_BINDING);
                    checkAttributeIsActuallyDefinedAndReplaceIfNot(legacyServices, legacyModel, FIXED_SOURCE_PORT, SOCKET_BINDING_GROUP, prop.getName(), LOCAL_DESTINATION_OUTBOUND_SOCKET_BINDING);
                    checkAttributeIsActuallyDefinedAndReplaceIfNot(legacyServices, legacyModel, FIXED_PORT, SOCKET_BINDING_GROUP, prop.getName(), SOCKET_BINDING);
                }
            }
        }
    }

    private void adjustUndefinedInTransformedToEmpty(ModelVersion modelVersion, ModelNode legacyModel, ModelNode transformed) {
        boolean is7_1_x = ModelVersion.compare(ModelVersion.create(1, 4, 0), modelVersion) < 0;

        for (PathAddress address : EMPTY_RESOURCE_ADDRESSES) {
            harmonizeModel(modelVersion, legacyModel, transformed, address, ModelHarmonizer.UNDEFINED_TO_EMPTY);
        }

        if (!is7_1_x) {
            for (PathAddress address : MISSING_NAME_ADDRESSES) {
                harmonizeModel(modelVersion, legacyModel, transformed, address, ModelHarmonizer.MISSING_NAME);
            }
        }
    }

    private void harmonizeModel(ModelVersion modelVersion, ModelNode legacyModel, ModelNode transformed,
                                                  PathAddress address, ModelHarmonizer harmonizer) {

        if (address.size() > 0) {
            PathElement pathElement = address.getElement(0);
            if (legacyModel.hasDefined(pathElement.getKey()) && transformed.hasDefined(pathElement.getKey())) {
                ModelNode legacyType = legacyModel.get(pathElement.getKey());
                ModelNode transformedType = transformed.get(pathElement.getKey());
                PathAddress childAddress = address.size() > 1 ? address.subAddress(1) : PathAddress.EMPTY_ADDRESS;
                if (pathElement.isWildcard()) {
                    for (String key : legacyType.keys()) {
                        if (transformedType.has(key)) {
                            harmonizeModel(modelVersion, legacyType.get(key),
                                    transformedType.get(key), childAddress, harmonizer);
                        }
                    }
                } else {
                    harmonizeModel(modelVersion, legacyType.get(pathElement.getValue()),
                            transformedType.get(pathElement.getValue()), address, harmonizer);
                }
            }
        } else {
            harmonizer.harmonizeModel(modelVersion, legacyModel, transformed);
        }
    }

    private void checkAttributeIsActuallyDefinedAndReplaceIfNot(KernelServices legacyServices, ModelNode legacyModel, String attributeName, String...parentAddress) {

        ModelNode parentNode = legacyModel;
        for (String s : parentAddress) {
            if (parentNode.hasDefined(s)) {
                parentNode = parentNode.get(s);
            } else {
                return;
            }
            if (!parentNode.isDefined()) {
                return;
            }
        }

        for (Property prop : parentNode.asPropertyList()) {
            if (prop.getValue().isDefined()) {
                ModelNode attribute = parentNode.get(prop.getName(), attributeName);
                if (attribute.isDefined()) {
                    //Attribute is defined in the legacy model - remove it if that is not the case
                    ModelNode op = new ModelNode();
                    op.get(OP).set(READ_ATTRIBUTE_OPERATION);
                    for (int i = 0 ; i < parentAddress.length ; i ++) {
                        if (i < parentAddress.length -1) {
                            op.get(OP_ADDR).add(parentAddress[i], parentAddress[++i]);
                        } else {
                            op.get(OP_ADDR).add(parentAddress[i], prop.getName());
                        }
                    }
                    op.get(NAME).set(attributeName);
                    op.get(INCLUDE_DEFAULTS).set(false);
                    try {
                        ModelNode result = legacyServices.executeForResult(op);
                        if (!result.isDefined()) {
                                attribute.set(new ModelNode());
                        }
                    } catch (OperationFailedException e) {
                        //TODO this might get thrown because the attribute does not exist in the legacy model?
                        //In which case it should perhaps be undefined
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }


    private class KernelServicesBuilderImpl implements KernelServicesBuilder, ModelTestBootOperationsBuilder.BootOperationParser {

        private final TestModelType type;
        private final ModelTestBootOperationsBuilder bootOperationBuilder = new ModelTestBootOperationsBuilder(testClass, this);
        private final TestParser testParser;
        private ProcessType processType;
        private ModelInitializer modelInitializer;
        private ModelWriteSanitizer modelWriteSanitizer;
        private boolean validateDescription;
        private boolean validateOperations = true;
        private XMLMapper xmlMapper = XMLMapper.Factory.create();
        private Map<ModelVersion, LegacyKernelServicesInitializerImpl> legacyControllerInitializers = new HashMap<ModelVersion, LegacyKernelServicesInitializerImpl>();
        private List<String> contentRepositoryContents = new ArrayList<String>();
        private final RunningModeControl runningModeControl;
        ExtensionRegistry extensionRegistry;
        private final Map<ModelNode, Map<String, Set<String>>> attributeDescriptors = new HashMap<>();
        private Map<ModelNode, Map<String, Map<String, Set<String>>>> operationParameterDescriptors = new HashMap<>();
        private final Stability stability;


        KernelServicesBuilderImpl(TestModelType type, Stability stability) {
            this.type = type;
            this.stability = stability;
            this.processType = type == TestModelType.HOST || type == TestModelType.DOMAIN ? ProcessType.HOST_CONTROLLER : ProcessType.STANDALONE_SERVER;
            runningModeControl = type == TestModelType.HOST ? new HostRunningModeControl(RunningMode.ADMIN_ONLY, RestartMode.HC_ONLY) : new RunningModeControl(RunningMode.ADMIN_ONLY);
            extensionRegistry = ExtensionRegistry.builder(this.processType).withRunningModeControl(this.runningModeControl).withStability(stability).build();
            testParser = TestParser.create(stability, extensionRegistry, xmlMapper, type);
        }


        public KernelServicesBuilder validateDescription() {
            this.validateDescription = true;
            return this;
        }

        @Override
        public KernelServicesBuilder setXmlResource(String resource) throws IOException, XMLStreamException {
            bootOperationBuilder.setXmlResource(resource);
            return this;
        }

        @Override
        public KernelServicesBuilder setXml(String subsystemXml) throws XMLStreamException {
            bootOperationBuilder.setXml(subsystemXml);
            return this;
        }

        @Override
        public KernelServicesBuilder setBootOperations(List<ModelNode> bootOperations) {
            bootOperationBuilder.setBootOperations(bootOperations);
            return this;
        }

        @Override
        public List<ModelNode> parseXml(String xml) throws Exception {
            ModelTestBootOperationsBuilder builder = new ModelTestBootOperationsBuilder(testClass, this);
            builder.setXml(xml);
            return builder.build();
        }

        @Override
        public List<ModelNode> parseXmlResource(String xmlResource) throws Exception {
            ModelTestBootOperationsBuilder builder = new ModelTestBootOperationsBuilder(testClass, this);
            builder.setXmlResource(xmlResource);
            return builder.build();
        }

        @Override
        public KernelServicesBuilder setModelInitializer(ModelInitializer modelInitializer, ModelWriteSanitizer modelWriteSanitizer) {
            bootOperationBuilder.validateNotAlreadyBuilt();
            this.modelInitializer = modelInitializer;
            this.modelWriteSanitizer = modelWriteSanitizer;
            testParser.addModelWriteSanitizer(modelWriteSanitizer);
            return this;
        }


        @Override
        public KernelServicesBuilder createContentRepositoryContent(String hash) {
            contentRepositoryContents.add(hash);
            return this;
        }

        public KernelServices build() throws Exception {
            bootOperationBuilder.validateNotAlreadyBuilt();
            List<ModelNode> bootOperations = bootOperationBuilder.build();


            if (type == TestModelType.HOST) {
                adjustLocalDomainControllerWriteForHost(bootOperations);
            }

            AbstractKernelServicesImpl kernelServices = AbstractKernelServicesImpl.create(processType, runningModeControl, ModelTestOperationValidatorFilter.createValidateAll(), bootOperations, testParser, null, type, modelInitializer, extensionRegistry, contentRepositoryContents);
            CoreModelTestDelegate.this.kernelServices.add(kernelServices);

            if (validateDescription) {
                validateDescriptionProviders(type, kernelServices, attributeDescriptors, operationParameterDescriptors);
            }


            ModelTestUtils.validateModelDescriptions(PathAddress.EMPTY_ADDRESS, kernelServices.getRootRegistration());
            ModelNode model = kernelServices.readWholeModel();
            model = removeForIntellij(model);
            ModelTestUtils.scanForExpressionFormattedStrings(model);

            for (Map.Entry<ModelVersion, LegacyKernelServicesInitializerImpl> entry : legacyControllerInitializers.entrySet()) {
                LegacyKernelServicesInitializerImpl legacyInitializer = entry.getValue();

                List<ModelNode> transformedBootOperations;
                if (legacyInitializer.isDontUseBootOperations()) {
                    transformedBootOperations = Collections.emptyList();
                } else {
                    transformedBootOperations = new ArrayList<ModelNode>();
                    for (ModelNode op : bootOperations) {

                        ModelNode transformed = kernelServices.transformOperation(entry.getKey(), op).getTransformedOperation();
                        if (transformed != null) {
                            transformedBootOperations.add(transformed);
                        }
                    }
                }

                LegacyControllerKernelServicesProxy legacyServices = legacyInitializer.install(kernelServices, modelInitializer, modelWriteSanitizer, contentRepositoryContents, transformedBootOperations);
                kernelServices.addLegacyKernelService(entry.getKey(), legacyServices);
            }


            return kernelServices;
        }


        private void adjustLocalDomainControllerWriteForHost(List<ModelNode> bootOperations) {
            //Remove the write-local-domain-controller operation since the test controller already simulates what it does at runtime.
            //For model validation to work, it always needs to be part of the model, and will be added there by the test controller.
            //We need to keep track of if it was part of the boot ops or not. If it was not part of the boot ops, the model will need
            //'sanitising' to remove it otherwise the xml comparison checks will fail.
            boolean dcInBootOps = false;
            for (Iterator<ModelNode> it = bootOperations.iterator() ; it.hasNext() ; ) {
                ModelNode op = it.next();
                String opName = op.get(OP).asString();
                if (opName.equals(LocalDomainControllerAddHandler.OPERATION_NAME) || opName.equals(RemoteDomainControllerAddHandler.OPERATION_NAME)) {
                    dcInBootOps = true;
                    break;
                }
                if(WRITE_ATTRIBUTE_OPERATION.equals(opName)) {
                    String attributeName = op.get(NAME).asString();
                    if(DOMAIN_CONTROLLER.equals(attributeName)) {
                        dcInBootOps = true;
                        break;
                    }
                }
                // host=foo:add(), always has IS_DOMAIN_CONTROLLER defined.
                if(HostAddHandler.OPERATION_NAME.equals(opName) && op.has(IS_DOMAIN_CONTROLLER)
                        && !op.get(IS_DOMAIN_CONTROLLER).equals(new ModelNode().setEmptyObject())) {
                    dcInBootOps = true;
                    break;
                }
            }
            if (!dcInBootOps) {
                testParser.addModelWriteSanitizer(new ModelWriteSanitizer() {
                    @Override
                    public ModelNode sanitize(ModelNode model) {
                        if (model.isDefined() && model.has(DOMAIN_CONTROLLER)) {
                            model.remove(DOMAIN_CONTROLLER);
                        }
                        return model;
                    }
                });
            }
        }

        private ModelNode removeForIntellij(ModelNode model){
            //When running in intellij it includes
            // "-Dorg.jboss.model.test.maven.repository.urls=${org.jboss.model.test.maven.repository.urls}"
            //in the runtime platform-mbeans's arguments for the host controller so it fails the scan for expression
            //formatted strings. Simply remove it.
            //Also do the same for the following system property in the runtime platform mbean system properties:
            //"org.jboss.model.test.maven.repository.urls" => "${org.jboss.model.test.maven.repository.urls}"
            ModelNode runtime = findModelNode(model, HOST, "primary", CORE_SERVICE, PLATFORM_MBEAN, TYPE, "runtime");
            if (runtime.isDefined()){
                runtime.remove("input-arguments");
                if (runtime.hasDefined(SYSTEM_PROPERTIES)) {
                    ModelNode properties = runtime.get(SYSTEM_PROPERTIES);
                    properties.remove("org.jboss.model.test.maven.repository.urls");
                }

            }
            return model;
        }

        private ModelNode findModelNode(ModelNode model, String...name){
            ModelNode currentModel = model;
            for (String part : name){
                if (!currentModel.hasDefined(part)){
                    return new ModelNode();
                } else {
                    currentModel = currentModel.get(part);
                }
            }
            return currentModel;
        }

        @Override
        public List<ModelNode> parse(String xml) throws XMLStreamException {
            final XMLStreamReader reader = XMLInputFactoryUtil.create().createXMLStreamReader(new StringReader(xml));
            final List<ModelNode> operationList = new ArrayList<ModelNode>();
            xmlMapper.parseDocument(operationList, reader);
            return operationList;
        }

        @Override
        public LegacyKernelServicesInitializer createLegacyKernelServicesBuilder(ModelVersion modelVersion, ModelTestControllerVersion testControllerVersion) {
            if (type != TestModelType.DOMAIN) {
                throw new IllegalStateException("Can only create legacy kernel services for DOMAIN.");
            }
            LegacyKernelServicesInitializerImpl legacyKernelServicesInitializerImpl = new LegacyKernelServicesInitializerImpl(modelVersion, testControllerVersion);
            legacyControllerInitializers.put(modelVersion, legacyKernelServicesInitializerImpl);
            return legacyKernelServicesInitializerImpl;
        }

        @Override
        public KernelServicesBuilder setDontValidateOperations() {
            validateOperations = true;
            return this;
        }

        @Override
        public KernelServicesBuilder registerAttributeArbitraryDescriptor(ModelNode address, String name, String descriptor) {
            if(!attributeDescriptors.containsKey(address)) {
                attributeDescriptors.put(address, new HashMap<>());
            }
            if(!attributeDescriptors.get(address).containsKey(name)) {
               attributeDescriptors.get(address).put(name, new HashSet<>());
            }
            attributeDescriptors.get(address).get(name).add(descriptor);
            return this;
        }

        @Override
        public KernelServicesBuilder registerArbitraryDescriptorForOperationParameter(ModelNode address, String operation, String parameter, String descriptor) {
             if(!operationParameterDescriptors.containsKey(address)) {
                operationParameterDescriptors.put(address, new HashMap<>());
            }
            if(!operationParameterDescriptors.get(address).containsKey(operation)) {
               operationParameterDescriptors.get(address).put(operation, new HashMap<>());
            }
            if(!operationParameterDescriptors.get(address).get(operation).containsKey(parameter)) {
               operationParameterDescriptors.get(address).get(operation).put(parameter, new HashSet<>());
            }
            operationParameterDescriptors.get(address).get(operation).get(parameter).add(descriptor);
            return this;
        }
    }

    private class LegacyKernelServicesInitializerImpl implements LegacyKernelServicesInitializer {
        private final ChildFirstClassLoaderBuilder classLoaderBuilder;
        private final ModelVersion modelVersion;
        private final List<LegacyModelInitializerEntry> modelInitializerEntries = new ArrayList<LegacyModelInitializerEntry>();
        private final ModelTestControllerVersion testControllerVersion;
        private boolean dontUseBootOperations = false;
        private boolean skipReverseCheck;
        private ModelFixer reverseCheckMainModelFixer;
        private ModelFixer reverseCheckLegacyModelFixer;
        private ModelTestOperationValidatorFilter.Builder operationValidationExcludeFilterBuilder;

        LegacyKernelServicesInitializerImpl(ModelVersion modelVersion, ModelTestControllerVersion version) {
            this.classLoaderBuilder = new ChildFirstClassLoaderBuilder(version.isEap());
            this.modelVersion = modelVersion;
            this.testControllerVersion = version;
        }

        private LegacyControllerKernelServicesProxy install(AbstractKernelServicesImpl mainServices, ModelInitializer modelInitializer, ModelWriteSanitizer modelWriteSanitizer, List<String> contentRepositoryContents, List<ModelNode> bootOperations) throws Exception {
            if (testControllerVersion == null) {
                throw new IllegalStateException();
            }

            if (!skipReverseCheck) {
                bootCurrentVersionWithLegacyBootOperations(bootOperations, modelInitializer, modelWriteSanitizer, contentRepositoryContents, mainServices);
            }

            final ClassLoader legacyCl;
            if (currentTransformerClassloaderParameter != null && currentTransformerClassloaderParameter.getClassLoader() != null) {
                legacyCl = currentTransformerClassloaderParameter.getClassLoader();
            } else {
                classLoaderBuilder.addParentFirstClassPattern("org.jboss.as.core.model.bridge.shared.*");

                //These two is needed or the child first classloader never gets GC'ed which causes OOMEs for very big tests
                //Here the Reference$ReaperThread hangs onto the classloader
                classLoaderBuilder.addParentFirstClassPattern("org.jboss.modules.*");
                //Here the NDC hangs onto the classloader
                classLoaderBuilder.addParentFirstClassPattern("org.jboss.logmanager.*");

                classLoaderBuilder.addMavenResourceURL("org.wildfly.core:wildfly-core-model-test-framework:" + ModelTestControllerVersion.CurrentVersion.VERSION);
                classLoaderBuilder.addMavenResourceURL("org.wildfly.core:wildfly-model-test:" + ModelTestControllerVersion.CurrentVersion.VERSION);
                classLoaderBuilder.addMavenResourceURL("org.wildfly.legacy.test:wildfly-legacy-spi:" + Version.LEGACY_TEST_CONTROLLER_VERSION);

                if (testControllerVersion != ModelTestControllerVersion.MASTER) {
                    String groupId = testControllerVersion.getCoreMavenGroupId();
                    String hostControllerArtifactId = testControllerVersion.getHostControllerMavenArtifactId();

                    classLoaderBuilder.addRecursiveMavenResourceURL(groupId + ":" + hostControllerArtifactId + ":" + testControllerVersion.getCoreVersion());
                    classLoaderBuilder.addMavenResourceURL("org.wildfly.legacy.test:wildfly-legacy-core-" + testControllerVersion.getTestControllerVersion() + ":" + Version.LEGACY_TEST_CONTROLLER_VERSION);

                }
                legacyCl = classLoaderBuilder.build();
                if (currentTransformerClassloaderParameter != null) {
                    //Cache the classloader for the other tests
                    currentTransformerClassloaderParameter.setClassLoader(legacyCl);
                }
            }


            ScopedKernelServicesBootstrap scopedBootstrap = new ScopedKernelServicesBootstrap(legacyCl);
            LegacyControllerKernelServicesProxy legacyServices = scopedBootstrap.createKernelServices(bootOperations, getOperationValidationFilter(), modelVersion, modelInitializerEntries);

            return legacyServices;
        }

        @Override
        public LegacyKernelServicesInitializer initializerCreateModelResource(PathAddress parentAddress, PathElement relativeResourceAddress, ModelNode model, String... capabilities) {
            modelInitializerEntries.add(new LegacyModelInitializerEntry(parentAddress, relativeResourceAddress, model, capabilities));
            return this;
        }

        @Override
        public LegacyKernelServicesInitializer initializerCreateModelResource(PathAddress parentAddress, PathElement relativeResourceAddress, ModelNode model) {
            return initializerCreateModelResource(parentAddress, relativeResourceAddress, model, new String[0]);
        }

        @Override
        public LegacyKernelServicesInitializer addOperationValidationExclude(String name, PathAddress pathAddress) {
            addOperationValidationConfig(name, pathAddress, Action.NOCHECK);
            return this;
        }

        @Override
        public LegacyKernelServicesInitializer addOperationValidationResolve(String name, PathAddress pathAddress) {
            addOperationValidationConfig(name, pathAddress, Action.RESOLVE);
            return this;
        }


        private void addOperationValidationConfig(String name, PathAddress pathAddress, Action action) {
            if (operationValidationExcludeFilterBuilder == null) {
                operationValidationExcludeFilterBuilder = ModelTestOperationValidatorFilter.createBuilder();
            }
            operationValidationExcludeFilterBuilder.addOperation(pathAddress, name, action, null);
        }

        @Override
        public LegacyKernelServicesInitializer setDontUseBootOperations() {
            dontUseBootOperations = true;
            return this;
        }

        boolean isDontUseBootOperations() {
            return dontUseBootOperations;
        }

        @Override
        public LegacyKernelServicesInitializer skipReverseControllerCheck() {
            skipReverseCheck = true;
            return this;
        }

        @Override
        public LegacyKernelServicesInitializer configureReverseControllerCheck(ModelFixer mainModelFixer, ModelFixer legacyModelFixer) {
            this.reverseCheckMainModelFixer = mainModelFixer;
            this.reverseCheckLegacyModelFixer = legacyModelFixer;
            return this;
        }

        private KernelServices bootCurrentVersionWithLegacyBootOperations(List<ModelNode> bootOperations, ModelInitializer modelInitializer, ModelWriteSanitizer modelWriteSanitizer, List<String> contentRepositoryHashes, KernelServices mainServices) throws Exception {
            KernelServicesBuilder reverseServicesBuilder = createKernelServicesBuilder(TestModelType.DOMAIN, Stability.DEFAULT)
                .setBootOperations(bootOperations)
                .setModelInitializer(modelInitializer, modelWriteSanitizer);
            for (String hash : contentRepositoryHashes) {
                reverseServicesBuilder.createContentRepositoryContent(hash);
            }
            KernelServices reverseServices = reverseServicesBuilder.build();
            if (reverseServices.getBootError() != null) {
                Throwable t = reverseServices.getBootError();
                if (t instanceof Exception) {
                    throw (Exception)t;
                }
                throw new Exception(t);
            }
            Assert.assertTrue(reverseServices.getBootErrorDescription(), reverseServices.isSuccessfulBoot() && !reverseServices.hasBootErrorCollectorFailures());

            ModelNode mainModel = mainServices.readWholeModel();
            if (reverseCheckMainModelFixer != null) {
                mainModel = reverseCheckMainModelFixer.fixModel(mainModel);
            }
            ModelNode reverseModel = reverseServices.readWholeModel();
            if (reverseCheckLegacyModelFixer != null) {
                reverseModel = reverseCheckLegacyModelFixer.fixModel(reverseModel);
            }
            ModelTestUtils.compare(mainModel, reverseModel);
            return reverseServices;
        }

        private ModelTestOperationValidatorFilter getOperationValidationFilter() {
            if (operationValidationExcludeFilterBuilder != null) {
                return operationValidationExcludeFilterBuilder.build();
            }
            return ModelTestOperationValidatorFilter.createValidateAll();
        }
    }
}
