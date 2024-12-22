/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.security.manager.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Function;

import javax.xml.stream.XMLInputFactory;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ExpressionStreamReaderDelegate;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.security.PermissionFactory;
import org.jboss.vfs.VirtualFile;
import org.wildfly.common.xml.XMLInputFactoryUtil;

/**
 * This class implements a {@link DeploymentUnitProcessor} that parses security permission files that might be
 * included in application components.
 * <p/>
 * The EE7 specification (section EE6.2.2.6) allows application components to specify required security permissions:
 * <p/>
 * "<i>Permission declarations must be stored in META-INF/permissions.xml file within an EJB, web, application client, or
 * resource adapter archive in order for them to be located and processed.
 * <p/>
 * The permissions for a packaged library are the same as the permissions for the module. Thus, if a library is packaged
 * in a .war file, it gets the permissions of the .war file.
 * <p/>
 * For applications packaged in an .ear file, the declaration of permissions must be at .ear file level. This permission
 * set is applied to all modules and libraries packaged within  the .ear file or within its contained modules. Any
 * permissions.xml files within such packaged modules are ignored, regardless of whether a permissions.xml file has been
 * supplied for the .ear file itself.</i>"
 * <p/>
 * As can be noted, the EE spec doesn't allow sub-deployments to override permissions set at the .ear level. We find it
 * a bit too restrictive, so we introduced the META-INF/jboss-permissions.xml descriptor. It uses the same schema as the
 * standard permissions.xml file but, unlike the latter, is always processed and the permissions contained in it override
 * any permissions set by a parent deployment. If a deployment contains both permissions files, jboss-permissions.xml
 * takes precedence over the standard permissions.xml.
 *
 * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
 */
public class PermissionsParserProcessor implements DeploymentUnitProcessor {

    private static final String PERMISSIONS_XML = "META-INF/permissions.xml";

    private static final String JBOSS_PERMISSIONS_XML = "META-INF/jboss-permissions.xml";

    // minimum set of permissions that are to be granted to all deployments.
    private final List<PermissionFactory> minPermissions;

    /**
     * Creates an instance of {@link PermissionsParserProcessor} with the specified minimum and maximum set of permissions.
     *
     * @param minPermissions a {@link List} containing the permissions that are to be granted to all deployments.
     */
    public PermissionsParserProcessor(List<PermissionFactory> minPermissions) {
        this.minPermissions = minPermissions;
    }

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ResourceRoot deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        final ModuleLoader moduleLoader = deploymentUnit.getAttachment(Attachments.SERVICE_MODULE_LOADER);
        final String moduleIdentifier = deploymentUnit.getAttachment(Attachments.MODULE_IDENTIFIER).toString();
        final Function<String, String> wflyResolverFunc = deploymentUnit.getAttachment(Attachments.WFLY_DESCRIPTOR_EXPR_EXPAND_FUNCTION);
        final Function<String, String> specResolverFunc = deploymentUnit.getAttachment(Attachments.SPEC_DESCRIPTOR_EXPR_EXPAND_FUNCTION);

        // non-spec behavior: always process permissions declared in META-INF/jboss-permissions.xml.
        VirtualFile jbossPermissionsXML = deploymentRoot.getRoot().getChild(JBOSS_PERMISSIONS_XML);
        if (jbossPermissionsXML.exists() && jbossPermissionsXML.isFile()) {
            List<PermissionFactory> factories = this.parsePermissions(jbossPermissionsXML, moduleLoader, moduleIdentifier, wflyResolverFunc);
            for (PermissionFactory factory : factories) {
                moduleSpecification.addPermissionFactory(factory);
            }
            // add the permissions specified in the minimum set.
            for (PermissionFactory factory : this.minPermissions) {
                moduleSpecification.addPermissionFactory(factory);
            }
        }

        // spec compliant behavior: only top-level deployments are processed (sub-deployments inherit permissions
        // defined at the .ear level, if any).
        else {
            if (deploymentUnit.getParent() == null) {
                VirtualFile permissionsXML = deploymentRoot.getRoot().getChild(PERMISSIONS_XML);
                if (permissionsXML.exists() && permissionsXML.isFile()) {
                    // parse the permissions and attach them in the deployment unit.
                    List<PermissionFactory> factories = this.parsePermissions(permissionsXML, moduleLoader, moduleIdentifier, specResolverFunc);
                    for (PermissionFactory factory : factories) {
                        moduleSpecification.addPermissionFactory(factory);
                    }
                }
                // add the minimum set of permissions to top-level deployments - sub-deployments will inherit them automatically.
                for (PermissionFactory factory : this.minPermissions) {
                    moduleSpecification.addPermissionFactory(factory);
                }
            } else {
                ModuleSpecification parentSpecification = deploymentUnit.getParent().getAttachment(Attachments.MODULE_SPECIFICATION);
                List<PermissionFactory> factories = parentSpecification.getPermissionFactories();
                if (factories != null && !factories.isEmpty()) {
                    // parent deployment contains permissions: subdeployments inherit those permissions.
                    for (PermissionFactory factory : factories) {
                        moduleSpecification.addPermissionFactory(factory);
                    }
                }
            }
        }
    }

    /**
     * <p>
     * Parses the permissions declared in the specified file. The permissions are wrapped in factory objects so they can
     * be lazily instantiated after the deployment unit module has been created.
     * </p>
     *
     * @param file               the {@link VirtualFile} that contains the permissions declarations.
     * @param loader             the {@link ModuleLoader} that is to be used by the factory to instantiate the permission.
     * @param moduleName         the name of the module that is to be used by the factory to instantiate the permission.
     * @param exprExpandFunction A function which will be used, if provided, to expand any expressions (of the form of {@code ${foobar}})
     *                           in the content being parsed. This function can be null, in which case the content is processed literally.
     * @return a list of {@link PermissionFactory} objects representing the parsed permissions.
     * @throws DeploymentUnitProcessingException if an error occurs while parsing the permissions.
     */
    private List<PermissionFactory> parsePermissions(final VirtualFile file, final ModuleLoader loader, final String moduleName, final Function<String, String> exprExpandFunction)
            throws DeploymentUnitProcessingException {

        InputStream inputStream = null;
        try {
            inputStream = file.openStream();
            final XMLInputFactory inputFactory = XMLInputFactoryUtil.create();
            final ExpressionStreamReaderDelegate expressionStreamReaderDelegate = new ExpressionStreamReaderDelegate(inputFactory.createXMLStreamReader(inputStream), exprExpandFunction);
            return PermissionsParser.parse(expressionStreamReaderDelegate, loader, moduleName);
        } catch (Exception e) {
            throw new DeploymentUnitProcessingException(e.getMessage(), e);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
            }
        }
    }
}