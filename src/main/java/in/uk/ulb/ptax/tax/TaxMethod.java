package in.uk.ulb.ptax.tax;

public enum TaxMethod {
    /** A - self assessment on carpet / covered area and ULB rental rates */
    SELF_ASSESSMENT,
    /** B - based on circle rate */
    CIRCLE_RATE,
    /** C - sum of actual / estimated rent of each unit */
    RENT_BASED
}
