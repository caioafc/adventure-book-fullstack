package com.adventurebook.serialization;

import java.util.regex.Pattern;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class SectionIdDeserializer extends ValueDeserializer<String> {

    private static final Pattern DIGITS = Pattern.compile("[0-9]+");

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() == JsonToken.VALUE_STRING || parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            String id = parser.getValueAsString();
            if (DIGITS.matcher(id).matches()) {
                return id;
            }
        }
        return context.reportInputMismatch(String.class,
                "Section identifiers must be non-negative integers or strings containing only digits");
    }
}
