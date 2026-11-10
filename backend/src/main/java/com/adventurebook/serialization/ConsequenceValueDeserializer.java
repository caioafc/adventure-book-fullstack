package com.adventurebook.serialization;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class ConsequenceValueDeserializer extends ValueDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            return parser.getIntValue();
        }
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            try {
                return Integer.valueOf(parser.getString());
            } catch (NumberFormatException ignored) {
                // Report the same error for fractional, malformed and overflowing amounts.
            }
        }
        return context.reportInputMismatch(Integer.class,
                "Consequence value must be a whole number within the integer range");
    }
}
