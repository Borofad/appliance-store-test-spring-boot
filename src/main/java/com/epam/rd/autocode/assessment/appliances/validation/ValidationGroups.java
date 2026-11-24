package com.epam.rd.autocode.assessment.appliances.validation;

import jakarta.validation.groups.Default;

/** Validation groups. OnCreate extends Default, so it validates everything plus the create-only rules. */
public final class ValidationGroups {

    private ValidationGroups() {
    }

    /** Use in the controller as @Validated(ValidationGroups.OnCreate.class) for "new" forms. */
    public interface OnCreate extends Default {
    }
}
