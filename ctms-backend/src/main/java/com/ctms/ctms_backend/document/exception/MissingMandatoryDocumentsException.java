package com.ctms.ctms_backend.document.exception;

import java.util.List;

public class MissingMandatoryDocumentsException extends RuntimeException {

    private final List<String> missingCategories;

    public MissingMandatoryDocumentsException(List<String> missingCategories) {
        super("Missing mandatory documents for this phase transition: " + String.join(", ", missingCategories));
        this.missingCategories = missingCategories;
    }

    public List<String> getMissingCategories() {
        return missingCategories;
    }
}
