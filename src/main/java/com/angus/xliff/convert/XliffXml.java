package com.angus.xliff.convert;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/** Shared parser settings for every XLIFF import path. */
final class XliffXml {
    private static final String JAXP_PROPERTY_PREFIX = "http://www.oracle.com/xml/jaxp/properties/";
    private static final String ENTITY_SIZE_LIMIT = "500000";

    private XliffXml() {
    }

    static DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        // JDK 24+ also counts decoded character references in the [xml] entity.
        // Raise both limits: changing only the first leaves JAXP00010004 at 100,000.
        // Use parser-local attributes so no JVM-wide settings are changed.
        factory.setAttribute(JAXP_PROPERTY_PREFIX + "maxGeneralEntitySizeLimit", ENTITY_SIZE_LIMIT);
        factory.setAttribute(JAXP_PROPERTY_PREFIX + "totalEntitySizeLimit", ENTITY_SIZE_LIMIT);
        return factory.newDocumentBuilder();
    }
}
