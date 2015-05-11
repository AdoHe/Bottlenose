package com.xqbase.baiji.io;

import com.xqbase.baiji.schema.Schema;

import java.io.IOException;

/**
 * Write data of a schema.
 * <p>Implemented for different in-memory data representations.
 *
 * @author Tony He
 */
public interface DatumWriter<D> {

    /**
     * Get the schema.
     */
    Schema getSchema();

    /**
     * Write a datum.  Traverse the schema, depth first, writing each leaf value
     * in the schema from the datum to the output.
     */
    void write(D datum, Encoder out) throws IOException;
}
