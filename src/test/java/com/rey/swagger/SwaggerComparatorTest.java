package com.rey.swagger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import v2.io.swagger.models.Swagger;
import v2.io.swagger.parser.SwaggerParser;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class SwaggerComparatorTest {

    @Test
    public void test() throws Exception {
        SwaggerParser parser = new SwaggerParser();

        final Swagger expected = parser.read("expected.yaml");
        final Swagger actual = parser.read("actual.yaml");
        SwaggerComparator swaggerComparator = new SwaggerComparator(expected, actual, true);

        assertEquals("{}", swaggerComparator.compare());
    }

    private void prettyPrint(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Object obj = mapper.readValue(json, Object.class);
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj));
    }
}