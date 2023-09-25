/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.client.helpers.ClientConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_CONTROL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_ORGANIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ORGANIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PRODUCT_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PRODUCT_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Global operation to build the installation summary of a server or a domain.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class GlobalInstallationReportHandler extends GlobalOperationHandlers.AbstractMultiTargetHandler {

    public static final String OPERATION_NAME = "product-info";
    public static final String SUB_OPERATION_NAME = "report";

    public static final String ARCH = "host-cpu-arch";
    public static final String AVAILABLE_PROCESSORS = "host-core-count";
    public static final String CPU = "host-cpu";
    public static final String FORMAT = "format";
    public static final String HOSTNAME = "host-name";
    public static final String INSTANCE_ID = "instance-identifier";
    public static final String JAVA_VERSION = "java-version";
    public static final String JVM = "jvm";
    public static final String JVM_HOME = "java-home";
    public static final String JVM_VENDOR = "jvm-vendor";
    public static final String JVM_VERSION = "jvm-version";
    public static final String NODE_NAME = "node-name";
    public static final String OS = "host-operating-system";
    public static final String PRODUCT_COMMUNITY_IDENTIFIER = "product-community-identifier";
    public static final String PRODUCT_HOME = "product-home";
    public static final String PRODUCT_INSTALLATION_DATE = "installation-date";
    public static final String PRODUCT_LAST_UPDATE = "last-update-date";
    public static final String STANDALONE_DOMAIN_IDENTIFIER = "standalone-or-domain-identifier";
    public static final String SUMMARY = "summary";

    /**
     * Type of PRODUCT_COMMUNITY_IDENTIFIER
     */
    public static final String PRODUCT_TYPE = "Product";
    public static final String PROJECT_TYPE = "Project";

    /**
     * Supported FORMATs
     */
    public static final String XML_FORMAT = "xml";
    public static final String JSON_FORMAT = "json";

    public static final SimpleAttributeDefinition JVM_DEFINITION = ObjectTypeAttributeDefinition.Builder.of(JVM,
            SimpleAttributeDefinitionBuilder.create(JAVA_VERSION, ModelType.STRING, true).build(),
            SimpleAttributeDefinitionBuilder.create(JVM_VERSION, ModelType.STRING, true).build(),
            SimpleAttributeDefinitionBuilder.create(JVM_VENDOR, ModelType.STRING, true).build(),
            SimpleAttributeDefinitionBuilder.create(JVM_HOME, ModelType.STRING, true).build()
    ).setRequired(true).setAttributeMarshaller(AttributeMarshaller.ELEMENT_ONLY_OBJECT).build();
    public static final SimpleAttributeDefinition CPU_DEFINITION = ObjectTypeAttributeDefinition.Builder.of(CPU,
            SimpleAttributeDefinitionBuilder.create(ARCH, ModelType.STRING, true).build(),
            SimpleAttributeDefinitionBuilder.create(AVAILABLE_PROCESSORS, ModelType.INT, true).setDefaultValue(new ModelNode(1)).build()
    ).setRequired(true).setAttributeMarshaller(AttributeMarshaller.ATTRIBUTE_OBJECT).build();

    public static final SimpleAttributeDefinition SUMMARY_DEFINITION = new ObjectTypeAttributeDefinition.Builder(SUMMARY,
            SimpleAttributeDefinitionBuilder.create(NODE_NAME, ModelType.STRING, true).build(),
            SimpleAttributeDefinitionBuilder.create(HOSTNAME, ModelType.STRING, true).build(),
            SimpleAttributeDefinitionBuilder.create(INSTANCE_ID, ModelType.STRING, true).build(),
            SimpleAttributeDefinitionBuilder.create(PRODUCT_NAME, ModelType.STRING, true).build(),
            SimpleAttributeDefinitionBuilder.create(PRODUCT_COMMUNITY_IDENTIFIER, ModelType.STRING)
                    .setAllowedValues(PRODUCT_TYPE, PROJECT_TYPE).build(),
            SimpleAttributeDefinitionBuilder.create(PRODUCT_VERSION, ModelType.STRING, true).build(),
            SimpleAttributeDefinitionBuilder.create(PRODUCT_HOME, ModelType.STRING, true).build(),
            SimpleAttributeDefinitionBuilder.create(PRODUCT_INSTALLATION_DATE, ModelType.STRING, true).build(),
            SimpleAttributeDefinitionBuilder.create(PRODUCT_LAST_UPDATE, ModelType.STRING, true).build(),
            SimpleAttributeDefinitionBuilder.create(OS, ModelType.STRING, false).build(),
            JVM_DEFINITION,
            CPU_DEFINITION).setAttributeMarshaller(AttributeMarshaller.ELEMENT_ONLY_OBJECT).build();

    public static final SimpleAttributeDefinition CREATE_REPORT_DEFINITION =
            SimpleAttributeDefinitionBuilder.create(FILE, ModelType.BOOLEAN, true)
                    .setDefaultValue(ModelNode.FALSE).build();

    public static final SimpleAttributeDefinition FILE_FORMAT_DEFINITION =
            SimpleAttributeDefinitionBuilder.create(FORMAT, ModelType.STRING, true)
                            .setDefaultValue(new ModelNode(XML_FORMAT))
                            .setAllowedValues(XML_FORMAT, JSON_FORMAT).build();

    public static final GlobalInstallationReportHandler INSTANCE = new GlobalInstallationReportHandler();

    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME,
            ControllerResolver.getResolver("global"))
            .setRuntimeOnly()
            .setReadOnly()
            .setReplyType(ModelType.LIST)
            .setReplyParameters(SUMMARY_DEFINITION)
            .build();

    @Override
    void doExecute(OperationContext context, ModelNode operation, FilteredData filteredData, boolean ignoreMissingResource) throws OperationFailedException {
        final Map<String, GlobalOperationHandlers.AvailableResponse> servers = new HashMap<>();
        ModelNode rootModel = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS,false).getModel();
        final String defaultOrganization = (rootModel.hasDefined(DOMAIN_ORGANIZATION)) ? rootModel.get(DOMAIN_ORGANIZATION).asString() : null;
        final Map<String, String> serverOrganizations = new HashMap<>();
        final ReportAssemblyHandler assemblyHandler = new ReportAssemblyHandler(servers, serverOrganizations, filteredData, ignoreMissingResource);
        if (null != context.getProcessType()) {
            switch (context.getProcessType()) {
                case HOST_CONTROLLER:
                case EMBEDDED_HOST_CONTROLLER:
                    context.addStep(new OperationStepHandler() {
                        @Override
                        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                            context.addStep(assemblyHandler, OperationContext.Stage.VERIFY, true);
                            String host = Util.getNameFromAddress(context.getCurrentAddress());
                            PathAddress hostAddress = context.getCurrentAddress();
                            ModelNode hostModel = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
                            final String hostOrganization = hostModel.hasDefined(ORGANIZATION) ? hostModel.get(ORGANIZATION).asString() : defaultOrganization;
                            addHostReport(context, hostAddress, host, servers);
                            if (hostOrganization != null) {
                                serverOrganizations.put(hostOrganization, hostOrganization);
                            }
                            Set<String> hostServers = context.readResource(PathAddress.EMPTY_ADDRESS).getChildrenNames(SERVER);
                            for (String server : hostServers) {
                                String nodeName = host + ":" + server;
                                addServerReport(context, hostAddress.append(SERVER, server), nodeName, servers);
                                if (hostOrganization != null) {
                                    serverOrganizations.put(nodeName, hostOrganization);
                                }
                            }
                        }
                    }, OperationContext.Stage.RUNTIME);
                    break;
                case STANDALONE_SERVER:
                case DOMAIN_SERVER:
                case EMBEDDED_SERVER:
                case SELF_CONTAINED:
                    context.addStep(assemblyHandler, OperationContext.Stage.VERIFY, true);
                    addStandaloneReport(context, servers);
                    break;
                default:
            }
        }
    }

    private void addStandaloneReport(OperationContext context, final Map<String, GlobalOperationHandlers.AvailableResponse> responseMap) {
        ModelNode reportOperation = Util.getEmptyOperation(SUB_OPERATION_NAME, PathAddress.EMPTY_ADDRESS.toModelNode());
        final ModelNode response = new ModelNode();
        GlobalOperationHandlers.AvailableResponse availableResponse = new GlobalOperationHandlers.AvailableResponse(response);
        OperationEntry entry = context.getResourceRegistration().getOperationEntry(PathAddress.EMPTY_ADDRESS, SUB_OPERATION_NAME);
        OperationStepHandler osh = entry.getOperationHandler();
        GlobalOperationHandlers.AvailableResponseWrapper wrapper = new GlobalOperationHandlers.AvailableResponseWrapper(osh, availableResponse);
        context.addStep(response, reportOperation, wrapper, OperationContext.Stage.RUNTIME);
        responseMap.put("", availableResponse);
        ControllerLogger.ROOT_LOGGER.debug("We are asking the standalone server its report");
    }

    private void addServerReport(OperationContext context, PathAddress address, String server, final Map<String, GlobalOperationHandlers.AvailableResponse> responseMap) {
        ModelNode reportOperation = Util.getEmptyOperation(SUB_OPERATION_NAME, address.toModelNode());
        final ModelNode response = new ModelNode();
        GlobalOperationHandlers.AvailableResponse availableResponse = new GlobalOperationHandlers.AvailableResponse(response);
        responseMap.put(server, availableResponse);
        OperationEntry entry = context.getRootResourceRegistration().getOperationEntry(address, SUB_OPERATION_NAME);
        if (entry != null) {
            OperationStepHandler osh = entry.getOperationHandler();
            GlobalOperationHandlers.AvailableResponseWrapper wrapper = new GlobalOperationHandlers.AvailableResponseWrapper(osh, availableResponse);
            context.addStep(response, reportOperation, wrapper, OperationContext.Stage.RUNTIME);
            ControllerLogger.ROOT_LOGGER.debugf("We are asking the server %s its report", server);
        }
    }

    private void addHostReport(OperationContext context, PathAddress address, String host, final Map<String, GlobalOperationHandlers.AvailableResponse> responseMap) {
        ModelNode reportOperation = Util.getEmptyOperation(SUB_OPERATION_NAME, address.toModelNode());
        final ModelNode response = new ModelNode();
        GlobalOperationHandlers.AvailableResponse availableResponse = new GlobalOperationHandlers.AvailableResponse(response);
        responseMap.put(host, availableResponse);
        OperationEntry entry = context.getRootResourceRegistration().getOperationEntry(address, SUB_OPERATION_NAME);
        if (entry != null) {
            OperationStepHandler osh = entry.getOperationHandler();
            GlobalOperationHandlers.AvailableResponseWrapper wrapper = new GlobalOperationHandlers.AvailableResponseWrapper(osh, availableResponse);
            context.addStep(response, reportOperation, wrapper, OperationContext.Stage.RUNTIME);
            ControllerLogger.ROOT_LOGGER.debugf("We are asking the host %s its report", host);
        }
    }

    public static OperationStepHandler createDomainOperation() {
        return new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                Resource res = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS,false);
                Set<String> hosts = res.getChildrenNames(HOST);
                String hostName = hosts.iterator().next();
                PathAddress address = PathAddress.pathAddress(PathElement.pathElement(HOST));
                //Hacky part we are getting the handler from a real host and calling the operation on /host=*
                OperationEntry entry = context.getRootResourceRegistration().getOperationEntry(PathAddress.pathAddress(PathElement.pathElement(HOST, hostName)), OPERATION_NAME);
                ModelNode reportOperation = Util.getEmptyOperation(OPERATION_NAME, address.toModelNode());
                if (operation.hasDefined(CREATE_REPORT_DEFINITION.getName())) {
                    reportOperation.get(CREATE_REPORT_DEFINITION.getName()).set(operation.get(CREATE_REPORT_DEFINITION.getName()));
                    if (operation.hasDefined(FILE_FORMAT_DEFINITION.getName())) {
                        reportOperation.get(FILE_FORMAT_DEFINITION.getName()).set(operation.get(FILE_FORMAT_DEFINITION.getName()));
                    }
                }
                if (entry != null) {
                    OperationStepHandler osh = entry.getOperationHandler();
                    context.addStep(reportOperation, osh, OperationContext.Stage.MODEL);
                }
            }
        };
    }
    /**
     * Assembles the response to a read-attribute request from the components gathered by earlier steps.
     */
    private static class ReportAssemblyHandler implements OperationStepHandler {

        private final Map<String, GlobalOperationHandlers.AvailableResponse> servers;
        private final Map<String, String> serverOrganizations;
        private final FilteredData filteredData;
        private final boolean ignoreMissingResource;

        private ReportAssemblyHandler(final Map<String, GlobalOperationHandlers.AvailableResponse> servers,
                final Map<String, String> serverOrganizations, final FilteredData filteredData, final boolean ignoreMissingResource) {
            this.servers = servers;
            this.serverOrganizations = serverOrganizations;
            this.filteredData = filteredData;
            this.ignoreMissingResource = ignoreMissingResource;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            boolean record = false;
            ModelNode rootModel = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS,false).getModel();
            String defaultOrganization = null;
            if(rootModel.hasDefined(ORGANIZATION)) {
                defaultOrganization = rootModel.get(ORGANIZATION).asString();
            }
            String format = FILE_FORMAT_DEFINITION.resolveModelAttribute(context, operation).asString();
            Map<String, ModelNode> sortedAttributes = new TreeMap<>();
            boolean failed = false;
            for (Map.Entry<String, GlobalOperationHandlers.AvailableResponse> entry : servers.entrySet()) {
                GlobalOperationHandlers.AvailableResponse ar = entry.getValue();
                if (ar.unavailable) {
                    // Our target resource has disappeared
                    handleMissingResource(context);
                    return;
                }
                ModelNode value = ar.response;
                if (!value.has(FAILURE_DESCRIPTION)) {
                    sortedAttributes.put(entry.getKey(), value.get(RESULT));
                } else if (value.hasDefined(FAILURE_DESCRIPTION)) {
                    context.getFailureDescription().set(value.get(FAILURE_DESCRIPTION));
                    failed = true;
                    break;
                }
            }
            if (!failed) {
                final ModelNode result = context.getResult();
                result.setEmptyList();
                ReportAttacher attacher;
                switch(format) {
                    case JSON_FORMAT:
                        attacher = new JsonReportAttacher(record);
                        break;
                    case XML_FORMAT:
                    default:
                        attacher = new XMLReportAttacher(SUMMARY_DEFINITION, record, "urn:jboss:product-report:1.0", "report");
                }
                if (sortedAttributes.size() == 1) {
                    Entry<String, ModelNode> entry = sortedAttributes.entrySet().iterator().next();
                    ModelNode report = entry.getValue();
                    result.add(report);
                    attacher.addReport(report);
                } else {
                    for(Entry<String, ModelNode> entry : sortedAttributes.entrySet()) {
                        ModelNode report = entry.getValue();
                        ModelNode summary = report.get(SUMMARY_DEFINITION.getName());
                        updateSummary(summary, defaultOrganization, entry.getKey());
                        result.add(report);
                        attacher.addReport(report);
                    }
                }
                attacher.attachResult(context);
                if (filteredData != null && filteredData.hasFilteredData()) {
                    context.getResponseHeaders().get(ACCESS_CONTROL).set(filteredData.toModelNode());
                }
            }
        }

        private void updateSummary(final ModelNode summary, String defaultOrganization, String nodeName) {
            summary.get(NODE_NAME).set(nodeName);
            if (!summary.hasDefined(ORGANIZATION)) {
                if (serverOrganizations.containsKey(nodeName)) {
                    summary.get(ORGANIZATION).set(serverOrganizations.get(nodeName));
                } else if (defaultOrganization != null) {
                    summary.get(ORGANIZATION).set(defaultOrganization);
                }
            }
        }

        private void handleMissingResource(OperationContext context) {
            // Our target resource has disappeared
            if (context.hasResult()) {
                context.getResult().set(new ModelNode());
            }
            if (!ignoreMissingResource) {
                throw ControllerLogger.MGMT_OP_LOGGER.managementResourceNotFound(context.getCurrentAddress());
            }
        }
    }
}
