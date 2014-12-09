package org.wildfly.core.testrunner;

/**
 * Exception that is thrown if an operation failed
 *
* @author Stuart Douglas
*/
public class UnsuccessfulOperationException extends Exception {
    private static final long serialVersionUID = 1L;

    public UnsuccessfulOperationException(String message) {
        super(message);
    }
}
