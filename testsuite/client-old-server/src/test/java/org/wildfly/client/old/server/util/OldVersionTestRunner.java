/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.client.old.server.util;

import static org.wildfly.core.testrunner.Server.LEGACY_JAVA_HOME;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.inject.Inject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.junit.runner.Result;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Suite;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerController;

/**
 * @author Kabir Khan
 */
public class OldVersionTestRunner extends Suite {

    private static final String LARGE_DEPLOYMENT_NAME = "large-deployment.xml";

    static final String DEPLOYMENT_SIZE = "jboss.test.client.old.server.size";



    static String OLD_VERSIONS_DIR = OldVersionCopier.OLD_VERSIONS_DIR;

    //On OS X we seem to need to specify the JRE, i.e. (on my machine):
    //  /Library/Java/JavaVirtualMachines/jdk1.7.0_71.jdk/Contents/Home/jre
    //rather than
    //  /Library/Java/JavaVirtualMachines/jdk1.7.0_71.jdk/Contents/Home
    //The latter variety causes problems like:
    //  "Cannot run program "/Library/Java/JavaVirtualMachines/jdk1.7.0_71.jdk/Contents/Home/bin/java": error=2, No such file or directory"
    static final String JDK8_LOCATION = "jboss.test.client.old.server.jdk8";

    /**
     * Annotation for a method which provides parameters to be injected into the
     * test class constructor by <code>Parameterized</code>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface OldVersionParameter {
        /**
         * <p>
         * Optional pattern to derive the test's name from the parameters. Use
         * numbers in braces to refer to the parameters or the additional data
         * as follows:
         * </p>
         *
         * <pre>
         * {index} - the current parameter index
         * {0} - the first parameter value
         * {1} - the second parameter value
         * etc...
         * </pre>
         * <p>
         * Default value is "{index}" for compatibility with previous JUnit
         * versions.
         * </p>
         *
         * @return {@link MessageFormat} pattern string, except the index
         *         placeholder.
         * @see MessageFormat
         */
        String name() default "{index}";
    }

    private class TestClassRunnerForParameters extends BlockJUnit4ClassRunner {
        private final OldVersionTestParameter parameter;

        private final String fName;

        private final ServerController controller = new ServerController();
        private final LargeDeploymentFile largeDeploymentFile;

        TestClassRunnerForParameters(Class<?> klass, OldVersionTestParameter parameters,
                                     String name) throws InitializationError {
            super(klass);
            fName = name;
            this.parameter = parameters;
            try {
                this.largeDeploymentFile = createTestFile();
            } catch (IOException e) {
                throw new InitializationError(e);
            }
        }

        private void doInject(Class<?> klass, Object instance) {
            Class c = klass;
            try {
                while (c != null && c != Object.class) {
                    for (Field field : c.getDeclaredFields()) {
                        if ((instance == null && Modifier.isStatic(field.getModifiers()) ||
                                instance != null && !Modifier.isStatic(field.getModifiers()))) {
                            if (field.isAnnotationPresent(Inject.class)) {
                                field.setAccessible(true);
                                if (field.getType() == ManagementClient.class && controller.isStarted()) {
                                    field.set(instance, controller.getClient());
                                } else if (field.getType() == ModelControllerClient.class && controller.isStarted()) {
                                    field.set(instance, controller.getClient().getControllerClient());
                                } else if (field.getType() == ServerController.class) {
                                    field.set(instance, controller);
                                } else if (field.getType() == LargeDeploymentFile.class) {
                                    field.set(instance, largeDeploymentFile);
                                }
                            }
                        }
                    }
                    c = c.getSuperclass();
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to inject", e);
            }
        }

        @Override
        public Object createTest() throws Exception {
            return createTestUsingConstructorInjection();
        }

        private Object createTestUsingConstructorInjection() throws Exception {
            Object test =  getTestClass().getOnlyConstructor().newInstance(parameter);
            doInject(getTestClass().getJavaClass(), test);
            return test;
        }


        @Override
        protected String getName() {
            return fName;
        }

        @Override
        protected String testName(FrameworkMethod method) {
            return method.getName() + getName();
        }

        @Override
        protected void validateConstructor(List<Throwable> errors) {
            validateOnlyOneConstructor(errors);
        }

        @Override
        protected void validateFields(List<Throwable> errors) {
            super.validateFields(errors);
        }

        @Override
        protected Statement classBlock(RunNotifier notifier) {
            return childrenInvoker(notifier);
        }

        @Override
        protected Annotation[] getRunnerAnnotations() {
            return new Annotation[0];
        }

        @Override
        public void run(final RunNotifier notifier){
            if (controller.isStarted()) {
                controller.stop();
            }
            setupServer();
            try {
                controller.start();
            } catch (RuntimeException e) {
                throw new RuntimeException(parameter.getAsVersion() +  ": " + e.getMessage(), e);
            }
            try {
                doInject(super.getTestClass().getJavaClass(), null);
                notifier.addListener(new RunListener() {
                    @Override
                    public void testRunFinished(Result result) throws Exception {
                        super.testRunFinished(result);
                        controller.stop();
                        largeDeploymentFile.getFile().delete();
                    }
                });
                super.run(notifier);
            } finally {
                controller.stop();
            }
        }


        private void setupServer() {
            Version.AsVersion version = parameter.getAsVersion();
            File file = OldVersionCopier.expandOldVersion(version);
            System.setProperty("jboss.home", file.getAbsolutePath());
            System.setProperty("management.protocol", version.getManagementProtocol());
            System.setProperty("management.port", version.getManagementPort());
            if (version.getJdk() != Version.JDK.JDK11) {
                final String prop;
                switch (version.getJdk()){
                    case JDK8:
                        prop = JDK8_LOCATION;
                        break;
                    default:
                        throw new IllegalStateException("Unknown jdk");
                }
                String jdkHome = System.getProperty(prop);
                if (jdkHome == null) {
                    throw new IllegalStateException("Could not determine " + version.getJdk() +
                            " home from either -D" + prop);
                }
                System.setProperty(LEGACY_JAVA_HOME, jdkHome);

                System.setProperty("jvm.args", "-Xmx512m -XX:MaxMetaspaceSize=256m");
            } else {
                System.clearProperty(LEGACY_JAVA_HOME);
            }
        }
    }

    private static final List<Runner> NO_RUNNERS = Collections
            .<Runner>emptyList();

    private final ArrayList<Runner> runners = new ArrayList<Runner>();

    /**
     * Only called reflectively. Do not use programmatically.
     */
    public OldVersionTestRunner(Class<?> klass) throws Throwable {
        super(klass, NO_RUNNERS);
        OldVersionParameter parameters = getParametersMethod().getAnnotation(
                OldVersionParameter.class);
        createRunnersForParameters(allParameters(), parameters.name());

    }

    @Override
    protected List<Runner> getChildren() {
        return runners;
    }

    @SuppressWarnings("unchecked")
    private Iterable<OldVersionTestParameter> allParameters() throws Throwable {
        Object parameters = getParametersMethod().invokeExplosively(null);
        if (parameters instanceof Iterable) {
            return (Iterable<OldVersionTestParameter>) parameters;
        } else {
            throw parametersMethodReturnedWrongType();
        }
    }

    private FrameworkMethod getParametersMethod() throws Exception {
        List<FrameworkMethod> methods = getTestClass().getAnnotatedMethods(
                OldVersionParameter.class);
        for (FrameworkMethod each : methods) {
            if (each.isStatic() && each.isPublic()) {
                return each;
            }
        }

        throw new Exception("No public static parameters method on class "
                + getTestClass().getName());
    }

    private void createRunnersForParameters(Iterable<OldVersionTestParameter> allParameters,
                                            String namePattern) throws Exception {
        try {
            int i = 0;
            for (OldVersionTestParameter parametersOfSingleTest : allParameters) {
                String name = nameFor(namePattern, i, parametersOfSingleTest);
                TestClassRunnerForParameters runner = new TestClassRunnerForParameters(
                        getTestClass().getJavaClass(), parametersOfSingleTest,
                        name);
                runners.add(runner);
                ++i;
            }
        } catch (ClassCastException e) {
            throw parametersMethodReturnedWrongType();
        }
    }

    private String nameFor(String namePattern, int index, OldVersionTestParameter parameters) {
        String finalPattern = namePattern.replaceAll("\\{index\\}",
                Integer.toString(index));
        String name = MessageFormat.format(finalPattern, parameters);
        return "[" + name + "]";
    }

    private Exception parametersMethodReturnedWrongType() throws Exception {
        String className = getTestClass().getName();
        String methodName = getParametersMethod().getName();
        String message = MessageFormat.format(
                "{0}.{1}() must return an Iterable of arrays.",
                className, methodName);
        return new Exception(message);
    }

    private LargeDeploymentFile createTestFile() throws IOException {
        //Default to a 1GB deployment
        int size = Integer.getInteger(DEPLOYMENT_SIZE, 1024 * 1024 * 1024);
        Path path = Paths.get(new File(".").getAbsolutePath(), "target", "deployment");
        File dir = path.toFile();
        if (!dir.exists()) {
            Files.createDirectories(path);
        }

        path = Paths.get(path.toString(), LARGE_DEPLOYMENT_NAME);
        File file = path.toFile();
        if (!file.exists()) {
            byte[] bytes = new byte[1024];
            for (int i = 0 ; i < 1024 ; i++) {
                bytes[i] = (byte)i;
            }
            try (BufferedOutputStream writer = new BufferedOutputStream(new FileOutputStream(file))) {
                int max = size/1024;
                for (int i = 0 ; i < max ; i++) {
                    writer.write(bytes);
                }
            }
        }

        return new LargeDeploymentFile(file);
    }

}