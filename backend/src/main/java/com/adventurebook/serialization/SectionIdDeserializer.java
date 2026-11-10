package com.adventurebook.serialization;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class SectionIdDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() == JsonToken.VALUE_STRING || parser.currentToken().isNumeric()) {
            return parser.getValueAsString();
        }
        return context.reportInputMismatch(String.class, "Section identifiers must be strings or numbers");
    }
}
