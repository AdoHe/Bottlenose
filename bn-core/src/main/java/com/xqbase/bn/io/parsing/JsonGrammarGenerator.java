package com.xqbase.bn.io.parsing;

import com.xqbase.bn.exceptions.BaijiRuntimeException;
import com.xqbase.bn.schema.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The class that generates a grammar suitable to parse Baiji data
 * in JSON format.
 */
public class JsonGrammarGenerator extends ValidatingGrammarGenerator {

    /**
     * Returns the non-terminal that is the start symbol
     * for the grammar for the given schema <tt>sc</tt>.
     */
    @Override
    public Symbol generate(Schema schema) {
        return Symbol.root(generate(schema, new HashMap<LitS, Symbol>()));
    }

    /**
     * Returns the non-terminal that is the start symbol
     * for grammar of the given schema <tt>sc</tt>. If there is already an entry
     * for the given schema in the given map <tt>seen</tt> then
     * that entry is returned. Otherwise a new symbol is generated and
     * an entry is inserted into the map.
     * @param sc    The schema for which the start symbol is required
     * @param seen  A map of schema to symbol mapping done so far.
     * @return      The start symbol for the schema
     */
    @Override
    public Symbol generate(Schema sc, Map<LitS, Symbol> seen) {
        switch (sc.getType()) {
            case NULL:
            case BOOLEAN:
            case INT:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case DATETIME:
            case STRING:
            case BYTES:
            case UNION:
                return super.generate(sc, seen);
            case ENUM: {
                EnumSchema esc = (EnumSchema) sc;
                return Symbol.seq(Symbol.enumLabelsAction(esc.getEnumSymbols()), Symbol.ENUM);
            }
            case ARRAY: {
                ArraySchema asc = (ArraySchema) sc;
                return Symbol.seq(Symbol.repeat(Symbol.ARRAY_END, Symbol.ITEM_END,
                        generate(asc.getItemSchema(), seen)), Symbol.ARRAY_START);
            }
            case MAP: {
                MapSchema msc = (MapSchema) sc;
                return Symbol.seq(Symbol.repeat(Symbol.MAP_END, Symbol.ITEM_END,
                        generate(msc.getValueSchema(), seen), Symbol.MAP_KEY_MARKER, Symbol.STRING),
                        Symbol.MAP_START);
            }
            case RECORD: {
                RecordSchema rsc = (RecordSchema) sc;
                LitS litS = new LitS(rsc);
                Symbol result = seen.get(litS);
                if (null == result) {
                    Symbol[] production = new Symbol[rsc.getFields().size() * 3 + 2];
                    result = Symbol.seq(production);
                    seen.put(litS, result);

                    int i = production.length;
                    int n = 0;
                    List<Field> fields = rsc.getFields();
                    production[--i] = Symbol.RECORD_START;
                    for (Field f : fields) {
                        production[--i] = Symbol.fieldAdjustAction(n, f.getName());
                        production[--i] = generate(f.getSchema(), seen);
                        production[--i] = Symbol.FIELD_END;
                        n++;
                    }
                    production[--i] = Symbol.RECORD_END;
                    return result;
                }
            }
            default:
                throw new BaijiRuntimeException("Unexpected Schema Type");
        }
    }
}
