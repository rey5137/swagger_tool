package com.rey.swagger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Objects;
import v2.io.swagger.models.HttpMethod;
import v2.io.swagger.models.Model;
import v2.io.swagger.models.Operation;
import v2.io.swagger.models.Path;
import v2.io.swagger.models.RefModel;
import v2.io.swagger.models.RefResponse;
import v2.io.swagger.models.Response;
import v2.io.swagger.models.Swagger;
import v2.io.swagger.models.parameters.AbstractSerializableParameter;
import v2.io.swagger.models.parameters.BodyParameter;
import v2.io.swagger.models.parameters.Parameter;
import v2.io.swagger.models.parameters.RefParameter;
import v2.io.swagger.models.properties.ArrayProperty;
import v2.io.swagger.models.properties.BooleanProperty;
import v2.io.swagger.models.properties.DateProperty;
import v2.io.swagger.models.properties.DateTimeProperty;
import v2.io.swagger.models.properties.DoubleProperty;
import v2.io.swagger.models.properties.FloatProperty;
import v2.io.swagger.models.properties.IntegerProperty;
import v2.io.swagger.models.properties.LongProperty;
import v2.io.swagger.models.properties.ObjectProperty;
import v2.io.swagger.models.properties.PasswordProperty;
import v2.io.swagger.models.properties.Property;
import v2.io.swagger.models.properties.RefProperty;
import v2.io.swagger.models.properties.StringProperty;
import v2.io.swagger.models.properties.UUIDProperty;
import v2.io.swagger.models.refs.RefType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SwaggerComparator {

    private boolean debug = false;

    private final Swagger expectedSwagger;
    private final Swagger actualSwagger;

    private final ObjectMapper mapper = new ObjectMapper();

    public SwaggerComparator(Swagger expectedSwagger, Swagger actualSwagger) {
        this(expectedSwagger, actualSwagger, false);
    }

    public SwaggerComparator(Swagger expectedSwagger, Swagger actualSwagger, boolean debug) {
        this.expectedSwagger = expectedSwagger;
        this.actualSwagger = actualSwagger;
        this.debug = debug;
    }

    public String compare() throws JsonProcessingException {
        ObjectNode rootNode = mapper.createObjectNode();

        if(expectedSwagger.getPaths() != null) {
            expectedSwagger.getPaths().forEach((key, path) -> {
                Path actualPath = findPath(key, actualSwagger);
                log("Compare path: %s", key);
                if (actualPath == null)
                    rootNode.put(key, "Not found");
                else {
                    ObjectNode pathNode = mapper.createObjectNode();
                    comparePath(pathNode, path, actualPath);
                    if (pathNode.size() > 0)
                        rootNode.set(key, pathNode);
                }
            });
        }

        return rootNode.toString();
    }

    private void comparePath(ObjectNode node, Path expected, Path actual) {
        if(expected.getOperationMap() != null) {
            Map<HttpMethod, Operation> actualOperations = actual.getOperationMap();
            expected.getOperationMap().forEach((method, operation) -> {
                Operation actualOperation = actualOperations == null ? null : actualOperations.get(method);
                log("Compare operation: %s", method.name());
                if (actualOperation == null)
                    node.put(method.name(), "Not found");
                else {
                    ObjectNode operationNode = mapper.createObjectNode();
                    compareOperation(operationNode, operation, actualOperation);
                    if (operationNode.size() > 0)
                        node.set(method.name(), operationNode);
                }
            });
        }
    }

    private void compareOperation(ObjectNode node, Operation expected, Operation actual) {
        ObjectNode parametersNode = mapper.createObjectNode();
        if(expected.getParameters() != null) {
            expected.getParameters().stream()
                    .map(parameter -> dereferenceParameter(parameter, expectedSwagger))
                    .forEach(parameter -> {
                        log("Compare parameter in %s: %s", parameter.getIn(), parameter.getName());

                        ObjectNode inNode = (ObjectNode) parametersNode.get(parameter.getIn());
                        if (inNode == null)
                            inNode = mapper.createObjectNode();

                        Parameter actualParameter = dereferenceParameter(findParameter(parameter.getIn(), parameter.getName(), actual), actualSwagger);

                        if (actualParameter == null)
                            inNode.put(parameter.getName(), "Not found");
                        else {
                            ObjectNode parameterNode = mapper.createObjectNode();
                            compareParameter(parameterNode, parameter, actualParameter);
                            if (parameterNode.size() > 0)
                                inNode.set(parameter.getName(), parameterNode);
                        }

                        if (inNode.size() > 0)
                            parametersNode.set(parameter.getIn(), inNode);
                    });
        }

        if (parametersNode.size() > 0)
            node.set("parameters", parametersNode);

        ObjectNode responsesNode = mapper.createObjectNode();
        if(expected.getResponses() != null) {
            Map<String, Response> actualResponses = actual.getResponses();
            expected.getResponses().forEach((status, response) -> {
                log("Compare response with status: %s", status);
                Response actualResponse = actualResponses == null ? null : actualResponses.get(status);
                if (actualResponse == null)
                    responsesNode.put(status, "Not found");
                else {
                    ObjectNode responseNode = mapper.createObjectNode();
                    compareResponse(responseNode, response, actualResponse);
                    if (responseNode.size() > 0)
                        responsesNode.set(status, responseNode);
                }
            });
        }

        if (responsesNode.size() > 0)
            node.set("responses", responsesNode);
    }

    private void compareParameter(ObjectNode node, Parameter expected, Parameter actual) {
        if (expected instanceof BodyParameter) {
            compareModel(node, ((BodyParameter) expected).getSchema(), ((BodyParameter) actual).getSchema());
        }
        if (expected instanceof AbstractSerializableParameter) {
            compareAbstractSerializableParameter(node, ((AbstractSerializableParameter) expected), ((AbstractSerializableParameter) actual));
        }
    }

    private boolean compareAbstractSerializableParameter(ObjectNode node, AbstractSerializableParameter expected, AbstractSerializableParameter actual) {
        if (!Objects.equal(expected.getType(), actual.getType())) {
            node.put("type", String.format("Expected '%s' but actual is '%s'", expected.getType(), actual.getType()));
            return false;
        }

        if (!Objects.equal(expected.getFormat(), actual.getFormat())) {
            node.put("format", String.format("Expected '%s' but actual is '%s'", expected.getFormat(), actual.getFormat()));
            return false;
        }

        if (!Objects.equal(expected.getPattern(), actual.getPattern())) {
            node.put("pattern", String.format("Expected '%s' but actual is '%s'", expected.getPattern(), actual.getPattern()));
            return false;
        }

        String value = Utils.findNotContains((List<String>) expected.getEnum(), actual.getEnum());
        if (value != null) {
            node.put("enum", String.format("Expected contains '%s' but not found", value));
            return false;
        }

        if (expected.getItems() != null) {
            if (actual.getItems() == null) {
                node.put("items", "Not found");
                return false;
            }

            ObjectNode itemsNode = mapper.createObjectNode();
            if (!compareProperty(itemsNode, expected.getItems(), actual.getItems())) {
                node.set("items", itemsNode);
                return false;
            }
        }

        return true;
    }

    private void compareModel(ObjectNode node, Model expected, Model actual) {
        expected = dereferenceModel(expected, expectedSwagger);
        actual = dereferenceModel(actual, actualSwagger);
        final Map<String, Property> actualProperties = actual.getProperties();
        if(expected.getProperties() != null)
            expected.getProperties().forEach((key, property) -> {
                log("Compare property: %s", key);
                Property actualProperty = actualProperties == null ? null : actualProperties.get(key);
                if (actualProperty == null)
                    node.put(key, "Not found");
                else {
                    ObjectNode propertyNode = mapper.createObjectNode();
                    if (!compareProperty(propertyNode, property, actualProperty))
                        node.set(key, propertyNode);
                }
            });
    }

    private void compareResponse(ObjectNode node, Response expected, Response actual) {
        expected = dereferenceResponse(expected, expectedSwagger);
        actual = dereferenceResponse(actual, actualSwagger);
        if(expected.getSchema() != null) {
            if(actual.getSchema() == null) {
                node.put("schema", "Not found");
            }
            else
                compareProperty(node, expected.getSchema(), actual.getSchema());
        }
    }

    private boolean compareProperty(ObjectNode node, Property expected, Property actual) {
        expected = dereferenceProperty(expected, expectedSwagger);
        actual = dereferenceProperty(actual, actualSwagger);

        if (!Objects.equal(expected.getType(), actual.getType()) || !expected.getClass().equals(actual.getClass())) {
            node.put("type", String.format("Expected '%s' but actual is '%s'", expected.getType(), actual.getType()));
            return false;
        }

        if (!Objects.equal(expected.getFormat(), actual.getFormat())) {
            node.put("format", String.format("Expected '%s' but actual is '%s'", expected.getFormat(), actual.getFormat()));
            return false;
        }

        if (expected instanceof StringProperty)
            return compareStringProperty(node, (StringProperty) expected, (StringProperty) actual);

        if (expected instanceof BooleanProperty)
            return compareBooleanProperty(node, (BooleanProperty) expected, (BooleanProperty) actual);

        if (expected instanceof DateProperty)
            return compareDateProperty(node, (DateProperty) expected, (DateProperty) actual);

        if (expected instanceof DateTimeProperty)
            return compareDateTimeProperty(node, (DateTimeProperty) expected, (DateTimeProperty) actual);

        if (expected instanceof DoubleProperty)
            return compareDoubleProperty(node, (DoubleProperty) expected, (DoubleProperty) actual);

        if (expected instanceof FloatProperty)
            return compareFloatProperty(node, (FloatProperty) expected, (FloatProperty) actual);

        if (expected instanceof IntegerProperty)
            return compareIntegerProperty(node, (IntegerProperty) expected, (IntegerProperty) actual);

        if (expected instanceof LongProperty)
            return compareLongProperty(node, (LongProperty) expected, (LongProperty) actual);

        if (expected instanceof PasswordProperty)
            return comparePasswordProperty(node, (PasswordProperty) expected, (PasswordProperty) actual);

        if (expected instanceof UUIDProperty)
            return compareUUIDProperty(node, (UUIDProperty) expected, (UUIDProperty) actual);

        if (expected instanceof ArrayProperty)
            return compareArrayProperty(node, (ArrayProperty) expected, (ArrayProperty) actual);

        if (expected instanceof ObjectProperty)
            return compareObjectProperty(node, (ObjectProperty) expected, (ObjectProperty) actual);

        return true;
    }

    private boolean compareStringProperty(ObjectNode node, StringProperty expected, StringProperty actual) {
        if (!Objects.equal(expected.getPattern(), actual.getPattern())) {
            node.put("pattern", String.format("Expected '%s' but actual is '%s'", expected.getPattern(), actual.getPattern()));
            return false;
        }

        String value = Utils.findNotContains(expected.getEnum(), actual.getEnum());
        if (value != null) {
            node.put("enum", String.format("Expected contains '%s' but not found", value));
            return false;
        }

        return true;
    }

    private boolean compareBooleanProperty(ObjectNode node, BooleanProperty expected, BooleanProperty actual) {
        Boolean value = Utils.findNotContains(expected.getEnum(), actual.getEnum());
        if (value != null) {
            node.put("enum", String.format("Expected contains '%s' but not found", value));
            return false;
        }

        return true;
    }

    private boolean compareDateProperty(ObjectNode node, DateProperty expected, DateProperty actual) {
        String value = Utils.findNotContains(expected.getEnum(), actual.getEnum());
        if (value != null) {
            node.put("enum", String.format("Expected contains '%s' but not found", value));
            return false;
        }

        return true;
    }

    private boolean compareDateTimeProperty(ObjectNode node, DateTimeProperty expected, DateTimeProperty actual) {
        String value = Utils.findNotContains(expected.getEnum(), actual.getEnum());
        if (value != null) {
            node.put("enum", String.format("Expected contains '%s' but not found", value));
            return false;
        }

        return true;
    }

    private boolean compareDoubleProperty(ObjectNode node, DoubleProperty expected, DoubleProperty actual) {
        Double value = Utils.findNotContains(expected.getEnum(), actual.getEnum());
        if (value != null) {
            node.put("enum", String.format("Expected contains '%s' but not found", value));
            return false;
        }

        return true;
    }

    private boolean compareFloatProperty(ObjectNode node, FloatProperty expected, FloatProperty actual) {
        Float value = Utils.findNotContains(expected.getEnum(), actual.getEnum());
        if (value != null) {
            node.put("enum", String.format("Expected contains '%s' but not found", value));
            return false;
        }

        return true;
    }

    private boolean compareIntegerProperty(ObjectNode node, IntegerProperty expected, IntegerProperty actual) {
        Integer value = Utils.findNotContains(expected.getEnum(), actual.getEnum());
        if (value != null) {
            node.put("enum", String.format("Expected contains '%s' but not found", value));
            return false;
        }

        return true;
    }

    private boolean compareLongProperty(ObjectNode node, LongProperty expected, LongProperty actual) {
        Long value = Utils.findNotContains(expected.getEnum(), actual.getEnum());
        if (value != null) {
            node.put("enum", String.format("Expected contains '%s' but not found", value));
            return false;
        }

        return true;
    }

    private boolean comparePasswordProperty(ObjectNode node, PasswordProperty expected, PasswordProperty actual) {
        if (!Objects.equal(expected.getPattern(), actual.getPattern())) {
            node.put("pattern", String.format("Expected '%s' but actual is '%s'", expected.getPattern(), actual.getPattern()));
            return false;
        }

        String value = Utils.findNotContains(expected.getEnum(), actual.getEnum());
        if (value != null) {
            node.put("enum", String.format("Expected contains '%s' but not found", value));
            return false;
        }

        return true;
    }

    private boolean compareUUIDProperty(ObjectNode node, UUIDProperty expected, UUIDProperty actual) {
        if (!Objects.equal(expected.getPattern(), actual.getPattern())) {
            node.put("pattern", String.format("Expected '%s' but actual is '%s'", expected.getPattern(), actual.getPattern()));
            return false;
        }

        String value = Utils.findNotContains(expected.getEnum(), actual.getEnum());
        if (value != null) {
            node.put("enum", String.format("Expected contains '%s' but not found", value));
            return false;
        }

        return true;
    }

    private boolean compareArrayProperty(ObjectNode node, ArrayProperty expected, ArrayProperty actual) {
        ObjectNode itemNode = mapper.createObjectNode();
        if(expected.getItems() == null || actual.getItems() == null) {
            node.put("items", "Not found");
            return false;
        }
        boolean isSame = compareProperty(itemNode, expected.getItems(), actual.getItems());
        if (!isSame)
            node.set("items", itemNode);
        return isSame;
    }

    private boolean compareObjectProperty(ObjectNode node, ObjectProperty expected, ObjectProperty actual) {
        AtomicBoolean isSame = new AtomicBoolean(true);
        final Map<String, Property> actualProperties = actual.getProperties();
        if(expected.getProperties() != null)
            expected.getProperties().forEach((key, property) -> {
                log("Compare property: %s", key);
                Property actualProperty = actualProperties == null ? null : actualProperties.get(key);
                if (actualProperty == null) {
                    node.put(key, "Not found");
                    isSame.set(false);
                } else {
                    ObjectNode propertyNode = mapper.createObjectNode();
                    if (!compareProperty(propertyNode, property, actualProperty)) {
                        node.set(key, propertyNode);
                        isSame.set(false);
                    }
                }
            });
        return isSame.get();
    }

    private Path findPath(String path, Swagger swagger) {
        //TODO: handle path variable
        return swagger.getPath(path);
    }

    private Parameter findParameter(String in, String name, Operation operation) {
        if (operation.getParameters() != null)
            for (Parameter p : operation.getParameters()) {
                if (in.equals(p.getIn()) && name.equals(p.getName()))
                    return p;
            }
        return null;
    }

    private Parameter dereferenceParameter(Parameter parameter, Swagger swagger) {
        if (parameter instanceof RefParameter) {
            return (Parameter) dereference(((RefParameter) parameter).get$ref(), swagger);
        }
        return parameter;
    }

    private Model dereferenceModel(Model model, Swagger swagger) {
        if (model instanceof RefModel) {
            return dereference(((RefModel) model).get$ref(), swagger);
        }
        return model;
    }

    private Property dereferenceProperty(Property property, Swagger swagger) {
        if (property instanceof RefProperty) {
            Model model = dereference(((RefProperty) property).get$ref(), swagger);
            ObjectProperty objectProperty = new ObjectProperty();
            objectProperty.setProperties(model.getProperties());
            objectProperty.setType("object");
            return objectProperty;
        }
        return property;
    }

    private Response dereferenceResponse(Response response, Swagger swagger) {
        if (response instanceof RefResponse) {
            return dereference(((RefResponse) response).get$ref(), swagger);
        }
        return response;
    }

    private <T> T dereference(String $ref, Swagger swagger) {
        T result = null;
        if ($ref.startsWith(RefType.DEFINITION.getInternalPrefix()))
            result = (T)swagger.getDefinitions().get($ref.substring(RefType.DEFINITION.getInternalPrefix().length()));
        if ($ref.startsWith(RefType.PARAMETER.getInternalPrefix()))
            result = (T)swagger.getParameters().get($ref.substring(RefType.PARAMETER.getInternalPrefix().length()));
        if ($ref.startsWith(RefType.PATH.getInternalPrefix()))
            result = (T)swagger.getPaths().get($ref.substring(RefType.PATH.getInternalPrefix().length()));
        if ($ref.startsWith(RefType.RESPONSE.getInternalPrefix()))
            result = (T)swagger.getResponses().get($ref.substring(RefType.RESPONSE.getInternalPrefix().length()));

        if(result == null)
            throw new ReferenceNotFoundException("Not found definition of " + $ref);

        return result;
    }

    private void log(String msg, Object... args) {
        if(debug)
            System.out.println(String.format(msg, args));
    }
}
