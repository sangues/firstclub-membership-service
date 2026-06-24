package com.firstclub.membership.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.HashMap;
import java.util.Map;

@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, String>, String> {
    private static final ObjectMapper M = new ObjectMapper();
    private static final TypeReference<Map<String, String>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        try { return attribute == null ? "{}" : M.writeValueAsString(attribute); }
        catch (Exception e) { throw new IllegalStateException("serialize params", e); }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        try { return (dbData == null || dbData.isBlank()) ? new HashMap<>() : M.readValue(dbData, TYPE); }
        catch (Exception e) { throw new IllegalStateException("deserialize params", e); }
    }
}
