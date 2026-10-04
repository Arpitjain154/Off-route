package com.idr.navigator.compat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** SourceVersion.isKeyword semantics using Android's standard collections only. */
public final class JavaKeywords {
    private JavaKeywords() {}
    private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
        "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private", "protected", "public",
        "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "_"
    ));
    public static boolean isKeyword(CharSequence name) { return RESERVED.contains(name.toString()); }
}
