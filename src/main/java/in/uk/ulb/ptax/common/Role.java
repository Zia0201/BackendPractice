package in.uk.ulb.ptax.common;

public enum Role {
    ADMIN,
    RECORD_KEEPER,
    CLERK,
    VERIFIER,
    FIELD_INSPECTOR,
    APPROVER,
    COUNTER,
    CITIZEN;

    public boolean isUlbStaff() {
        return this != ADMIN && this != CITIZEN;
    }
}
