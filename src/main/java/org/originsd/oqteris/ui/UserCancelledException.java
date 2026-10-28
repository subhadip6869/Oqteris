package org.originsd.oqteris.ui;

/**
 * Thrown when an operation (such as password entry or file selection) is cancelled by the user.
 */
public class UserCancelledException extends RuntimeException {
    public UserCancelledException(String message) {
        super(message);
    }
}
