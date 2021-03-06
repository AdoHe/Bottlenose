package com.xqbase.bn.schema;


import com.xqbase.bn.common.util.StringUtils;
import com.xqbase.bn.exceptions.BaijiRuntimeException;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;

import java.io.IOException;
import java.io.StringWriter;

/**
 * An abstract data type.
 * <p>A schema may be one of:
 * <ul>
 * <li>A <i>record</i>, mapping field names to field value data;
 * <li>An <i>enum</i>, containing one of a small set of symbols;
 * <li>An <i>array</i> of values, all of the same schema;
 * <li>A <i>map</i>, containing string/value pairs, of a declared schema;
 * <li>A <i>union</i> of other schemas;
 * <li>A unicode <i>string</i>;
 * <li>A sequence of <i>bytes</i>;
 * <li>A UTC datetime</li>
 * <li>A 32-bit signed <i>int</i>;
 * <li>A 64-bit signed <i>long</i>;
 * <li>A 32-bit IEEE single-<i>float</i>; or
 * <li>A 64-bit IEEE <i>double</i>-float; or
 * <li>A <i>boolean</i>; or
 * <li><i>null</i>.
 * </ul>
 * <p/>
 * The schema objects are <i>logically</i> immutable.
 *
 * @author Tony He
 */
public abstract class Schema {

    private static final JsonFactory FACTORY = new JsonFactory();
    private static final ObjectMapper MAPPER = new ObjectMapper(FACTORY);

    static {
        FACTORY.enable(JsonParser.Feature.ALLOW_COMMENTS);
        FACTORY.setCodec(MAPPER);
    }

    private final SchemaType type;
    private final PropertyMap propertyMap;

    protected Schema(SchemaType type, PropertyMap propertyMap) {
        this.type = type;
        this.propertyMap = propertyMap;
    }

    /**
     * Get the type of the schema.
     * @return type of schema
     */
    public SchemaType getType() {
        return type;
    }

    /**
     * Get additional JSON attributes apart from those defined in the Baiji spec.
     * @return additional JSON attributes
     */
    public PropertyMap getPropertyMap() {
        return propertyMap;
    }

    /**
     * The name of this schema. If this is a named schema such as an enum,
     * it returns the fully qualified name for the schema.
     * For other schemas, it returns the type of the schema.
     *
     * @return the qualified or type of the schema.
     */
    public abstract String getName();

    /**
     * Parses a JSON string to create a new schema object.
     *
     * @param json JSON string
     * @return a new Schema Object
     */
    public static Schema parse(String json) {
        if (!StringUtils.hasLength(json)) {
            throw new IllegalArgumentException("JSON string can't be null or empty.");
        }

        return parse(json.trim(), new SchemaNames());  // standalone schema, so no enclosing namespace
    }

    /**
     * Parses a JSON string to create a new schema object.
     *
     * @param json JSON string to parse.
     * @param names list of {@link SchemaName}s already read.
     * @return a schema object.
     */
    protected static Schema parse(String json, SchemaNames names) {
        // First try to constructor a PrimitiveSchema instance
        Schema schema = PrimitiveSchema.newInstance(json);
        if (schema != null)
            return schema;

        try {
            JsonNode node = MAPPER.readTree(json);
            return parse(node, names);
        } catch (Throwable t) {
            throw new SchemaParseException("Could not parse. " + t.getMessage() + "\n" + json);
        }
    }

    /**
     * Traverse the JSON node to create a new instance of schema object.
     *
     * @param jsonNode the json node.
     * @param names list of {@link SchemaName}s already read.
     * @return a schema object.
     */
    protected static Schema parse(JsonNode jsonNode, SchemaNames names) {
        if (null == jsonNode) {
            throw new IllegalArgumentException("parsed JsonNode can't be null");
        }

        if (jsonNode.isTextual()) {
            String value = jsonNode.getTextValue();
            PrimitiveSchema ps = PrimitiveSchema.newInstance(value);
            if (ps != null) {
                return ps;
            }

            NamedSchema schema = names.getSchema(value, null);
            if (schema != null) {
                return schema;
            }

            throw new SchemaParseException("Undefined JsonNode name: " + value);
        } else if (jsonNode.isArray()) {
            return UnionSchema.newInstance((ArrayNode) jsonNode, null, names);
        } else if (jsonNode.isObject()) {
            JsonNode typeNode = jsonNode.get("type");
            if (null == typeNode) {
                throw new SchemaParseException("type property can't be null");
            }

            PropertyMap props = JsonHelper.getProperties(jsonNode);
            if (typeNode.isTextual()) {
                String type = typeNode.getTextValue();

                if ("array".equals(type)) {
                    return ArraySchema.newInstance(jsonNode, props, names);
                } else if ("map".equals(type)) {
                    return MapSchema.newInstance(jsonNode, props, names);
                }

                PrimitiveSchema ps = PrimitiveSchema.newInstance(type);
                if (ps != null)
                    return ps;

                return NamedSchema.newInstance(jsonNode, props, names);
            } else if (typeNode.isArray()) {
                return UnionSchema.newInstance((ArrayNode) typeNode, props, names);
            }
        }
        return null;
    }

    /**
     * Render this as <a href="http://json.org/">JSON</a>.
     */
    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(boolean pretty) {
        try {
            StringWriter writer = new StringWriter();
            JsonGenerator gen = FACTORY.createJsonGenerator(writer);
            if (pretty)
                gen.useDefaultPrettyPrinter();

            if (this instanceof PrimitiveSchema || this instanceof UnionSchema) {
                gen.writeStartObject();
                gen.writeFieldName("type");
            }

            writeJSON(gen, new SchemaNames());

            if (this instanceof PrimitiveSchema || this instanceof UnionSchema) {
                gen.writeEndObject();
            }

            gen.flush();
            return writer.toString();
        } catch (IOException e) {
            throw new BaijiRuntimeException(e);
        }
    }

    protected void writeJSON(JsonGenerator gen, SchemaNames names) throws IOException {
        writeStartObject(gen);
        writeJsonFields(gen, names);
        gen.writeEndObject();
    }

    private void writeStartObject(JsonGenerator gen) throws IOException {
        gen.writeStartObject();
        gen.writeFieldName("type");
        gen.writeString(type.toString().toLowerCase());
    }

    /**
     * Default implementation for writing schema properties in JSON format
     *
     * @param gen      JSON generator
     * @param names    list of named schemas already written
     */
    protected void writeJsonFields(JsonGenerator gen, SchemaNames names) throws IOException {
    }
}
