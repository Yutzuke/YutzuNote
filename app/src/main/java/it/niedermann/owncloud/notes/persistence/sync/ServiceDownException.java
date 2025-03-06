package it.niedermann.owncloud.notes.persistence.sync;

public class ServiceDownException extends RuntimeException {
    public ServiceDownException(String message) {
        super(message);
    }
}
