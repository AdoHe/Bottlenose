package com.xqbase.bn.rpc.server.tomcat;

/**
 * Generates ANSI encoded output, automatically attempting to detect if the terminal
 * supports ANSI.
 *
 * @author Tony He
 */
public class AnsiOutput {

    private static final String ENCODE_JOIN = ";";

    private static Enabled enabled = Enabled.DETECT;

    private static final String OPERATING_SYSTEM_NAME = System.getProperty("os.name")
            .toLowerCase();

    private static final String ENCODE_START = "\033[";

    private static final String ENCODE_END = "m";

    private static final String RESET = "0;" + AnsiElement.DEFAULT;

    /**
     * Sets if ANSI output is enabled.
     * @param enabled if ANSI is enabled, disabled or detected
     */
    public static void setEnabled(Enabled enabled) {
        AnsiOutput.enabled = enabled;
    }

    static Enabled getEnabled() {
        return AnsiOutput.enabled;
    }

    /**
     * Create a new ANSI string from the specified elements. Any {@link AnsiElement}s will
     * be encoded as required.
     * @param elements the elements to encode
     * @return a string of the encoded elements
     */
    public static String toString(Object... elements) {
        StringBuilder sb = new StringBuilder();
        if (isEnabled()) {
            buildEnabled(sb, elements);
        }
        else {
            buildDisabled(sb, elements);
        }
        return sb.toString();
    }

    private static void buildEnabled(StringBuilder sb, Object[] elements) {
        boolean writingAnsi = false;
        boolean containsEncoding = false;
        for (Object element : elements) {
            if (element instanceof AnsiElement) {
                containsEncoding = true;
                if (!writingAnsi) {
                    sb.append(ENCODE_START);
                    writingAnsi = true;
                }
                else {
                    sb.append(ENCODE_JOIN);
                }
            }
            else {
                if (writingAnsi) {
                    sb.append(ENCODE_END);
                    writingAnsi = false;
                }
            }
            sb.append(element);
        }
        if (containsEncoding) {
            sb.append(writingAnsi ? ENCODE_JOIN : ENCODE_START);
            sb.append(RESET);
            sb.append(ENCODE_END);
        }
    }

    private static void buildDisabled(StringBuilder sb, Object[] elements) {
        for (Object element : elements) {
            if (!(element instanceof AnsiElement) && element != null) {
                sb.append(element);
            }
        }
    }

    private static boolean isEnabled() {
        if (enabled == Enabled.DETECT) {
            return detectIfEnabled();
        }
        return enabled == Enabled.ALWAYS;
    }

    private static boolean detectIfEnabled() {
        try {
            if (System.console() == null) {
                return false;
            }
            return !(OPERATING_SYSTEM_NAME.indexOf("win") >= 0);
        }
        catch (Throwable ex) {
            return false;
        }
    }

    /**
     * Possible values to pass to {@link AnsiOutput#setEnabled}. Determines when to output
     * ANSI escape sequences for coloring application output.
     */
    public static enum Enabled {

        /**
         * Try to detect whether ANSI coloring capabilities are available. The default
         * value for {@link AnsiOutput}.
         */
        DETECT,

        /**
         * Enable ANSI-colored output
         */
        ALWAYS,

        /**
         * Disable ANSI-colored output
         */
        NEVER

    }
}
