// File: XmlSourceExtractor2.java

package com.angus.xliff.convert;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import com.angus.xliff.swing.XliffSwingUI;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static com.angus.xliff.convert.Constans.*;

public class XmlSourceExtractor {
	
    private static int extractedCount = 0;

    public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
        List<String> translateNoList = new ArrayList<>();

        try (FileWriter writer = new FileWriter(args[0] + SOURCE_FILE_NAME_POSTFIX)) {
            File inputFile = new File(args[0]);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputFile);
            doc.getDocumentElement().normalize();

            NodeList transUnitList = doc.getElementsByTagName("trans-unit");

            for (int i = 0; i < transUnitList.getLength(); i++) {
                Node transUnitNode = transUnitList.item(i);

                if (transUnitNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element transUnitElement = (Element) transUnitNode;
                    String transUnitId = transUnitElement.getAttribute("id");
                    String translateAttr = transUnitElement.getAttribute("translate");

                    NodeList targetList = transUnitElement.getElementsByTagName("target");
                    if (targetList.getLength() > 0) {
                        Node targetNode = targetList.item(0);
                        Element targetElement = (Element) targetNode;

                        String stateAttr = targetElement.getAttribute("state");
                        if ("needs-translation".equalsIgnoreCase(stateAttr)) {
                            NodeList sourceList = transUnitElement.getElementsByTagName("source");
                            if (sourceList.getLength() > 0) {
                                Node sourceNode = sourceList.item(0);
                                String sourceText = sourceNode.getTextContent();

                                StringBuilder sourceTag = new StringBuilder("<source id=\"" + transUnitId + "\"");

                                if ("no".equalsIgnoreCase(translateAttr)) {
                                    sourceTag.append(" translate=\"no\"");
                                    translateNoList.add(sourceTag.toString() + ">" + escapeXml(sourceText) + "</source>\n");
                                } else {
                                    sourceTag.append(">").append(replaceNewline(sourceText)).append("</source>\n");
                                    writer.write(sourceTag.toString());
                                    extractedCount++;
                                }
                            }
                        }
                    }
                }
            }

            for (String item : translateNoList) {
                writer.write(item);
            }

        }
    }

    public static int getExtractedCount() {
        return extractedCount;
    }

    private static String escapeXml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String replaceNewline(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\n", "${NEW_LINE}");
    }
}
