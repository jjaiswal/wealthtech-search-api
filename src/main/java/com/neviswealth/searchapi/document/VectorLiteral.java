package com.neviswealth.searchapi.document;

/**
 * Converts a {@code float[]} embedding to the pgvector string literal format
 * (e.g. {@code [0.1,0.2,0.3]}), used with the {@code ?::vector} cast in SQL.
 */
final class VectorLiteral {

    private static final int APPROX_CHARS_PER_ELEMENT = 8;

    private VectorLiteral() {}

    /** Example: {@code {0.1f, 0.2f}} → {@code "[0.1,0.2]"} */
    static String toPgVector(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * APPROX_CHARS_PER_ELEMENT);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Inverse of {@link #toPgVector}.
     * Example: {@code "[0.1,0.2]"} → {@code {0.1f, 0.2f}}
     * Handles both pgvector formats: {@code [a,b,c]} and {@code (a,b,c)}.
     */
    static float[] fromPgVector(String pgVector) {
        if (pgVector == null || pgVector.isBlank()) return new float[0];

        // Strip surrounding brackets: [ ] or ( )
        String trimmed = pgVector.strip();
        if ((trimmed.startsWith("[") && trimmed.endsWith("]"))
                || (trimmed.startsWith("(") && trimmed.endsWith(")"))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }

        String[] parts = trimmed.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].strip());
        }
        return result;
    }
}