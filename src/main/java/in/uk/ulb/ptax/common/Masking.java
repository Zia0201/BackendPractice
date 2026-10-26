package in.uk.ulb.ptax.common;

public final class Masking {

    private Masking() {
    }

    /** 9876500101 -> 98XXXXXX01. Used on the public dashboard. */
    public static String mobile(String mobile) {
        if (mobile == null || mobile.length() < 4) {
            return mobile;
        }
        return mobile.substring(0, 2) + "X".repeat(mobile.length() - 4) + mobile.substring(mobile.length() - 2);
    }
}
