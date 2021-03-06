package com.xqbase.bn.specific;

import com.xqbase.bn.generic.GenericData;

/**
 * Utilities for generated Java classes.
 *
 * @author Tony He
 */
public class SpecificData extends GenericData {

    private static final SpecificData INSTANCE = new SpecificData();

    public SpecificData() {}

    public SpecificData(ClassLoader classLoader) {
        super(classLoader);
    }

    public static SpecificData get() {
        return INSTANCE;
    }
}
