package com.groomdesk.customer;

/**
 * Customer names are stored in a consistent "Title Case" however the cashier typed them
 * ("ajith RAJ" becomes "Ajith Raj", "k.r. prakash" becomes "K.R. Prakash").
 * The frontend applies the same rule while typing (lib/names.ts); this is the source of truth.
 */
public final class PersonNames {

    private PersonNames() {
    }

    /** Trims, collapses repeated spaces, and capitalises the first letter of each word. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String collapsed = raw.trim().replaceAll("\\s+", " ");
        StringBuilder out = new StringBuilder(collapsed.length());
        boolean startOfWord = true;
        for (int i = 0; i < collapsed.length(); i++) {
            char c = collapsed.charAt(i);
            out.append(startOfWord ? Character.toUpperCase(c) : Character.toLowerCase(c));
            // A new word starts after a space, an initial's dot, a hyphen or an apostrophe (D'Souza).
            startOfWord = c == ' ' || c == '.' || c == '-' || c == '\'';
        }
        return out.toString();
    }
}
