/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.subsystem.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.LegacyResourceDefinition;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.junit.Assert;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 */
class TransformationUtils {

    private TransformationUtils() {
        //
    }

    private static ModelNode getSubsystemDefinitionForVersion(final Class<?> classForDmrPackage, final String subsystemName, ModelVersion version) {

        StringBuilder key = new StringBuilder(subsystemName).append("-").append(version.getMajor()).append(".").append(version.getMinor());
        key.append('.').append(version.getMicro()).append(".dmr");
        InputStream is = null;
        try {
            is = classForDmrPackage.getResourceAsStream(key.toString());
            if (is == null) {
                return null;
            }
            return ModelNode.fromStream(is);
        } catch (IOException e) {
            ControllerLogger.ROOT_LOGGER.cannotReadTargetDefinition(e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    //
                }
            }
        }
        return null;
    }

    private static ResourceDefinition loadSubsystemDefinitionFromFile(final Class<?> classForDmrPackage, final String subsystemName, ModelVersion version) {
        final ModelNode desc = getSubsystemDefinitionForVersion(classForDmrPackage, subsystemName, version);
        if (desc == null) {
            return null;
        }
        return new LegacyResourceDefinition(desc);
    }

    private static Resource modelToResource(final PathAddress startAddress, final ImmutableManagementResourceRegistration reg, final ModelNode model, boolean includeUndefined) {
        return modelToResource(startAddress, reg, model, includeUndefined, PathAddress.EMPTY_ADDRESS);
    }

    private static Resource modelToResource(final PathAddress startAddress, final ImmutableManagementResourceRegistration reg, final ModelNode model, boolean includeUndefined, PathAddress fullPath) {
        Resource res = Resource.Factory.create();
        ModelNode value = new ModelNode();
        Set<String> allFields = new HashSet<String>(model.keys());
        for (String name : reg.getAttributeNames(PathAddress.EMPTY_ADDRESS)) {
            AttributeAccess aa = reg.getAttributeAccess(PathAddress.EMPTY_ADDRESS, name);
            if (aa.getStorageType() == AttributeAccess.Storage.RUNTIME){
                allFields.remove(name);
                continue;
            }

            if (includeUndefined) {
                value.get(name).set(model.get(name));
            } else {
                if (model.hasDefined(name)) {
                    value.get(name).set(model.get(name));
                }
            }
            allFields.remove(name);
        }
        if (!value.isDefined() && model.isDefined() && reg.getChildAddresses(PathAddress.EMPTY_ADDRESS).isEmpty()) {
            value.setEmptyObject();
        }
        res.writeModel(value);

        for (String childType : reg.getChildNames(PathAddress.EMPTY_ADDRESS)) {
            if (model.hasDefined(childType)) {
                for (Property property : model.get(childType).asPropertyList()) {
                    PathElement childPath = PathElement.pathElement(childType, property.getName());
                    ImmutableManagementResourceRegistration subRegistration = reg.getSubModel(PathAddress.pathAddress(childPath));
                    Resource child = modelToResource(startAddress, subRegistration, property.getValue(), includeUndefined, fullPath.append(childPath));
                    res.registerChild(childPath, child);
                }
            }
            allFields.remove(childType);
        }

        if (!allFields.isEmpty()){
            throw ControllerLogger.ROOT_LOGGER.modelFieldsNotKnown(allFields, startAddress.append(fullPath));
        }
        return res;
    }

    static Resource modelToResource(final ImmutableManagementResourceRegistration reg, final ModelNode model, boolean includeUndefined) {
        return TransformationUtils.modelToResource(PathAddress.EMPTY_ADDRESS, reg, model, includeUndefined);
    }

    /**
     * Dumps the target subsystem resource description to DMR format, needed by TransformerRegistry for non-standard subsystems
     *
     * @param kernelServices the kernel services for the started controller
     * @param modelVersion   the target subsystem model version
     * @param mainSubsystemName name of subsystem
     * @param dmrFile file where to write description
     */
    private static void generateLegacySubsystemResourceRegistrationDmr(KernelServices kernelServices, ModelVersion modelVersion, String mainSubsystemName, Path dmrFile) throws IOException {
        KernelServices legacy = kernelServices.getLegacyServices(modelVersion);

        //Generate the org.jboss.as.controller.transform.subsystem-version.dmr file - just use the format used by TransformerRegistry for now
        PathAddress pathAddress = PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, mainSubsystemName));
        ModelNode desc = ((KernelServicesInternal) legacy).readFullModelDescription(pathAddress.toModelNode());
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(dmrFile, StandardCharsets.UTF_8))) {
            desc.writeString(pw, false);
            //System.out.println("Legacy resource definition dmr written to: " + dmrFile.getAbsolutePath());
        }
    }

    static ResourceDefinition getResourceDefinition(KernelServices kernelServices, ModelVersion modelVersion, String mainSubsystemName) throws IOException {
        //Look for the file in the org.jboss.as.subsystem.test package - this is where we used to store them before the split
        ResourceDefinition rd = TransformationUtils.loadSubsystemDefinitionFromFile(TransformationUtils.class.getClass(), mainSubsystemName, modelVersion);

        if (rd == null) {
            //This is the 'new' post-split way. First check for a cached .dmr file. This which also allows people
            //to override the file for the very rare cases where the rd needed touching up (probably only the case for 7.1.x descriptions).
            File file = getDmrFile(kernelServices, modelVersion, mainSubsystemName);
            if (!file.exists()) {
                generateLegacySubsystemResourceRegistrationDmr(kernelServices, modelVersion, mainSubsystemName, file.toPath());
            }
            //System.out.println("Using legacy resource definition dmr: " + file);
            rd = TransformationUtils.loadSubsystemDefinitionFromFile(kernelServices.getTestClass(), mainSubsystemName, modelVersion);
        }
        return rd;
    }

    private static File getDmrFile(KernelServices kernelServices, ModelVersion modelVersion, String mainSubsystemName) {

        File file = determineTestClassesDirectory();
        for (String part : kernelServices.getTestClass().getPackage().getName().split("\\.")) {
            file = new File(file, part);
            if (!file.exists()) {
                file.mkdir();
            }
        }
        return new File(file, mainSubsystemName + "-" + modelVersion.getMajor() + "." + modelVersion.getMinor() + "." + modelVersion.getMicro() + ".dmr");
    }

    private static File determineTestClassesDirectory() {
        File file = new File("target/test-classes").getAbsoluteFile();
        if (!file.exists()) {
            // In IntelliJ we often end up with the wrong directory so brute-force determining what it is
            // Note this will not work for the tests testing the subsystem test framework itself but should work elsewhere
            File stackTraceFile = null;
            StackTraceElement[] elements = new Exception().getStackTrace();
            for (StackTraceElement element : elements) {
                if (!element.getClassName().startsWith("org.jboss.as.subsystem.test.")) {
                    try {
                        Class clazz = TransformationUtils.class.getClassLoader().loadClass(element.getClassName());
                        stackTraceFile = new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    break;

                }
            }
            Assert.assertNotNull("Could not determine test-classes directory", stackTraceFile);
            file = stackTraceFile.getAbsoluteFile();
        }
        Assert.assertTrue("Could not determine test-classes directory" + file, file.exists());
        return file;
    }

}
