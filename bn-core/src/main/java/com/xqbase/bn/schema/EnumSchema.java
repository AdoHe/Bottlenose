package com.xqbase.bn.schema;

import com.xqbase.bn.util.ObjectUtil;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;

import java.io.IOException;
import java.util.*;

/**
 * The EnumSchema Definition.
 *
 * @author Tony He
 */
public class EnumSchema extends NamedSchema implements Iterable<String> {

    private final List<String> symbols;
    private final Map<String, Integer> ordinals;

    /**
     * Construct a enum schema.
     *
     * @param schemaName the schema name.
     * @param doc        the schema doc.
     * @param aliases    set of aliases for the name
     * @param props      the schema properties map.
     * @param names      list of named schemas already read
     */
    private EnumSchema(SchemaName schemaName, String doc, Set<String> aliases, LockableArrayList<String> symbols,
                PropertyMap props, SchemaNames names) {
        super(SchemaType.ENUM, schemaName, doc, aliases, props, names);

        this.symbols = symbols.lock();
        this.ordinals = new HashMap<>();
        int index = 0;
        for (String symbol : symbols) {
            if (this.ordinals.put(symbol, index++) != null)
                throw new SchemaParseException("Duplicate enum symbol: " + symbol);
        }
    }

    /**
     * Static function to return new instance of EnumSchema
     *
     * @param enumSchema    JSON object for enum schema
     * @param name          schema name
     * @param doc           documentation to the user of this schema
     * @param aliases       set of aliases for the name
     * @param props         properties map
     * @param names         list of named schema already parsed in
     * @return a new instance of {@link EnumSchema}
     */
    protected static EnumSchema newInstance(JsonNode enumSchema, SchemaName name, String doc,
                Set<String> aliases, PropertyMap props, SchemaNames names) {

        JsonNode symbolsNode = enumSchema.get("symbols");
        if (null == symbolsNode || !symbolsNode.isArray()) {
            throw new SchemaParseException("Enum has no symbols: "+ enumSchema);
        }


        LockableArrayList<String> symbols = new LockableArrayList<>();
        for (JsonNode symbol : symbolsNode) {
            symbols.add(symbol.getTextValue());
        }

        return new EnumSchema(name, doc, aliases, symbols, props, names);
    }

    public int getEnumOrdinal(String symbol) {
        return ordinals.get(symbol);
    }

    public int size() {
        return symbols.size();
    }

    public List<String> getEnumSymbols() {
        return this.symbols;
    }

    public boolean contains(String symbol) {
        return this.symbols.contains(symbol);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;

        if (!(obj instanceof EnumSchema))
            return false;

        EnumSchema that = (EnumSchema) obj;
        if (this.getName().equals(that.getName()) && this.size() == that.size()) {
            for (int i = 0; i < this.size(); i++) {
                if (!(this.symbols.get(i).equals(that.symbols.get(i)))) {
                    return false;
                }
            }
            return ObjectUtil.equals(this.getPropertyMap(), that.getPropertyMap());
        }
        return false;
    }

    @Override
    public int hashCode() {
        int value = getSchemaName().hashCode() + ObjectUtil.hashCode(getPropertyMap());
        for (String symbol : symbols) {
            value += 23 * symbol.hashCode();
        }
        return value;
    }

    @Override
    public Iterator<String> iterator() {
        return symbols.iterator();
    }

    @Override
    protected void writeJsonFields(JsonGenerator gen, SchemaNames names) throws IOException {
        super.writeJsonFields(gen, names);
        gen.writeFieldName("symbols");
        gen.writeStartArray();
        for (String symbol : symbols) {
            gen.writeString(symbol);
        }
        gen.writeEndArray();
    }
}
