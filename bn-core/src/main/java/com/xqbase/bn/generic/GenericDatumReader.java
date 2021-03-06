package com.xqbase.bn.generic;

import com.xqbase.bn.io.DatumReader;
import com.xqbase.bn.io.Decoder;
import com.xqbase.bn.io.DecoderFactory;
import com.xqbase.bn.io.ResolvingDecoder;
import com.xqbase.bn.schema.RecordSchema;
import com.xqbase.bn.schema.Schema;

import java.io.IOException;
import java.util.Map;

/**
 * {@link com.xqbase.bn.io.DatumReader} for generic java classes.
 *
 * @author Tony He
 */
public abstract class GenericDatumReader<T> implements DatumReader<T> {

    private final GenericData data;
    private Schema schema;

    private ResolvingDecoder creatorResolver = null;
    private final Thread creator;

    private static final ThreadLocal<Map<Schema, Map<Schema, ResolvingDecoder>>>
            RESOLVER_CACHE =
            new ThreadLocal<Map<Schema, Map<Schema, ResolvingDecoder>>>() {
                @Override
                protected Map<Schema, Map<Schema, ResolvingDecoder>> initialValue() {
                    return super.initialValue();
                }
            };

    public GenericDatumReader(Schema schema) {
        this(schema, GenericData.get());
    }

    public GenericDatumReader(Schema schema, GenericData data) {
        this(data);
        this.schema = schema;
    }

    protected GenericDatumReader(GenericData data) {
        this.data = data;
        this.creator = Thread.currentThread();
    }

    protected final ResolvingDecoder getResolver(Schema schema) throws IOException {
        Thread thread = Thread.currentThread();
        ResolvingDecoder resolver;
        if (thread == creator && creatorResolver != null) {
            return creatorResolver;
        }

        Map<Schema, ResolvingDecoder> cache = RESOLVER_CACHE.get().get(schema);
        if (null == cache) {

        }
        resolver = cache.get(schema);
        if (null == resolver) {
            resolver = DecoderFactory.get().resolvingDecoder(schema, null);
            cache.put(schema, resolver);
        }
        if (thread == creator) {
            creatorResolver = resolver;
        }
        return resolver;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T read(T reuse, Decoder in) throws IOException {
        ResolvingDecoder resolver = getResolver(schema);
        resolver.configure(in);
        T result = (T) read(reuse, schema, resolver);
        resolver.drain();
        return result;
    }

    protected Object read(Object reuse, Schema schema, ResolvingDecoder resolver) {
        switch (schema.getType()) {
            case RECORD:
        }
        return null;
    }

    private Object readRecord(Object reuse, RecordSchema recordSchema, ResolvingDecoder resolver) {
        return null;
    }
}
