package com.centella.chembl.exception;

/**
 * Thrown when the ChEMBL API returns a 404 for the given target ID.
 */
public class TargetNotFoundException extends RuntimeException {
    public TargetNotFoundException(String message) {
        super(message);
    }
}
