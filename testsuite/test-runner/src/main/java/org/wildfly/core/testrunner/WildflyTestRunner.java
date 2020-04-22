package org.wildfly.core.testrunner;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;
import org.wildfly.security.WildFlyElytronProvider;

/**
 * A lightweight test runner for running management based tests
 *
 * @author Stuart Douglas
 */
public class WildflyTestRunner extends BlockJUnit4ClassRunner {

    private static final Provider ELYTRON_PROVIDER = new WildFlyElytronProvider();

    private final ServerController controller = new ServerController();
    private final boolean automaticServerControl;
    private final List<ServerSetupTask> serverSetupTasks = new LinkedList<>();

    private final AtomicBoolean doOnce = new AtomicBoolean(false);

    private final ParameterDescriptions parameterDescriptions = new ParameterDescriptions();

    /**
     * Creates a BlockJUnit4ClassRunner to run {@code klass}
     *
     * @throws org.junit.runners.model.InitializationError if the test class is malformed.
     */
    public WildflyTestRunner(Class<?> klass) throws InitializationError {
        super(klass);
        if (klass.isAnnotationPresent(ServerControl.class)) {
            ServerControl serverControl = klass.getAnnotation(ServerControl.class);
            automaticServerControl = !serverControl.manual();
        } else {
            automaticServerControl = true;
        }
    }

    private void doInject(TestClass klass, Object instance) {

        try {

            for (FrameworkField frameworkField : klass.getAnnotatedFields(Inject.class)) {
                Field field = frameworkField.getField();
                if ((instance == null && Modifier.isStatic(field.getModifiers()) ||
                        instance != null)) {//we want to do injection even on static fields before test run, so we make sure that client is correct for current state of server
                    field.setAccessible(true);
                    if (field.getType() == ManagementClient.class && controller.isStarted()) {
                        field.set(instance, controller.getClient());
                    } else if (field.getType() == ModelControllerClient.class && controller.isStarted()) {
                        field.set(instance, controller.getClient().getControllerClient());
                    } else if (field.getType() == ServerController.class) {
                        field.set(instance, controller);
                    }
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to inject", e);
        }
    }

    @Override
    protected Object createTest() throws Exception {
        Object res = super.createTest();
        doInject(getTestClass(), res);
        return res;
    }

    @Override
    protected List<FrameworkMethod> getChildren() {
        final List<FrameworkMethod> result = new ArrayList<>();
        final TestClass testClass = getTestClass();
        final Collection<Object> parameters = resolveParameters(testClass);
        // If there are no resolved parameters we can just use the default methods
        if (parameters.isEmpty()) {
            result.addAll(super.getChildren());
        } else {
            final FrameworkField field = findParameterField(testClass);
            for (FrameworkMethod method : super.getChildren()) {
                for (Object value : parameters) {
                    parameterDescriptions.add(method, field, value);
                    // Add the same method for each parameter value
                    result.add(method);
                }
            }
        }
        return result;
    }

    @Override
    protected Statement methodInvoker(final FrameworkMethod method, final Object test) {
        if (!parameterDescriptions.isEmpty()) {
            final ParameterDescription description = parameterDescriptions.take(method);
            if (description != null) {
                try {
                    final Field field = description.field.getField();
                    if (!field.isAccessible()) {
                        field.setAccessible(true);
                    }
                    field.set(test, description.value);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(String.format("Failed to set value %s on field %s for test %s",
                            description.value, description.field.getName(), test.getClass().getName()), e);
                }
            }
        }
        return super.methodInvoker(method, test);
    }

    @Override
    public void run(final RunNotifier notifier) {
        notifier.addListener(new RunListener() {
            @Override
            public void testRunFinished(Result result) throws Exception {
                super.testRunFinished(result);
                parameterDescriptions.clear();
                if (automaticServerControl) {
                    controller.stop();
                }
            }
        });
        configureTestRun();
        if (!serverSetupTasks.isEmpty() && !automaticServerControl) {
            throw new RuntimeException("Can't run setup tasks with manual server control");
        }
        boolean providerInstalled = false;
        if (Security.getProvider(ELYTRON_PROVIDER.getName()) == null) {
            Security.insertProviderAt(ELYTRON_PROVIDER, 0);
            providerInstalled = true;
        }
        if (automaticServerControl) {
            runSetupTasks();
        }
        super.run(notifier);
        if (automaticServerControl) {
            runTearDownTasks();
        }
        if (providerInstalled) {
            Security.removeProvider(ELYTRON_PROVIDER.getName());
        }
    }

    private void runSetupTasks() {
        for (ServerSetupTask task : serverSetupTasks) {
            try {
                task.setup(controller.getClient());
            } catch (Exception e) {
                throw new RuntimeException(String.format("Could not run setup task '%s'", task), e);
            }
        }
    }

    private void runTearDownTasks() {
        List<ServerSetupTask> reverseServerSetupTasks = new LinkedList<>(serverSetupTasks);
        Collections.reverse(reverseServerSetupTasks);
        for (ServerSetupTask task : reverseServerSetupTasks) {
            try {
                task.tearDown(controller.getClient());
            } catch (Exception e) {
                throw new RuntimeException(String.format("Could not run tear down task '%s'", task), e);
            }
        }
        checkServerState();
    }

    private void prepareSetupTasks(TestClass klass) {
        try {
            if (klass.getJavaClass().isAnnotationPresent(ServerSetup.class)) {
                ServerSetup serverSetup = klass.getAnnotation(ServerSetup.class);
                for (Class<? extends ServerSetupTask> clazz : serverSetup.value()) {
                    Constructor<? extends ServerSetupTask> ctor = clazz.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    serverSetupTasks.add(ctor.newInstance());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void startServerIfRequired() {
        if (automaticServerControl) {
            controller.start();
        }
    }

    private void checkServerState() {
        ModelNode op = new ModelNode();
        op.get("operation").set("read-attribute");
        op.get("name").set("server-state");
        try {
            ModelNode result = controller.getClient().executeForResult(op);
            if (!"running".equalsIgnoreCase(result.asString())) {
                throw new RuntimeException(String.format("Server state is '%s' following test completion; tests must complete with the server in 'running' state", result.asString()));
            }
        } catch (UnsuccessfulOperationException e) {
            throw new RuntimeException("Failed checking server-state", e);
        }

    }

    private void configureTestRun() {
        startServerIfRequired();
        if (doOnce.compareAndSet(false, true)) {
            doInject(getTestClass(), null);
            prepareSetupTasks(getTestClass());
        }
    }

    private FrameworkField findParameterField(final TestClass testClass) {
        final List<FrameworkField> fields = testClass.getAnnotatedFields(Parameter.class);
        if (fields.size() > 1) {
            throw new RuntimeException(String.format("More than one field was annotated with @%s: %s",
                    Parameter.class.getSimpleName(), fields));
        } else if (fields.isEmpty()) {
            throw new RuntimeException(String.format("If the @%s annotation is present on a method a field must be annotated with @%s",
                    Parameters.class.getSimpleName(), Parameter.class.getSimpleName()));
        }
        return fields.get(0);
    }

    @SuppressWarnings("unchecked")
    private Collection<Object> resolveParameters(final TestClass testClass) {
        final FrameworkMethod method = findParametersMethod(testClass);
        if (method == null) {
            return Collections.emptyList();
        }
        try {
            return (Collection<Object>) method.invokeExplosively(null);
        } catch (Throwable t) {
            throw new RuntimeException(String.format("Failed to invoke method %s on test %s", method.getName(), testClass.getName()), t);
        }
    }

    private FrameworkMethod findParametersMethod(final TestClass testClass) {
        final List<FrameworkMethod> methods = testClass.getAnnotatedMethods(Parameters.class);
        if (methods.size() > 1) {
            throw new RuntimeException(String.format("More than one method was annotated with @%s: %s",
                    Parameters.class.getSimpleName(), methods));
        } else if (methods.isEmpty()) {
            return null;
        }
        final FrameworkMethod method = methods.get(0);
        if (method.isPublic() && method.isStatic() && Collection.class.isAssignableFrom(method.getReturnType())) {
            return method;
        }
        throw new RuntimeException(String.format("Method %s must be public, static and have a return type assignable to Collection<Object>.",
                method.getName()));
    }

}
