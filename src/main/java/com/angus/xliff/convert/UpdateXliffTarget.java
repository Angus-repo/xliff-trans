package com.angus.xliff.convert;

import static com.angus.xliff.convert.Constans.TARGET_FILE_NAME_POSTFIX;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class UpdateXliffTarget {

	private static final Logger logger = LoggerFactory.getLogger(UpdateXliffTarget.class);
	
    private static int updatedCount = 0;
    private static String updatedFilePath = "";
    private static ResourceBundle messages = ResourceBundle.getBundle("MessagesBundle", Locale.getDefault());

    public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException, TransformerException {
        if (args.length == 0) {
            logger.error(messages.getString("file_path_required"));
            return;
        }

        Map<String, String> sourceMap = new HashMap<>();
        Files.lines(Paths.get(args[0] + TARGET_FILE_NAME_POSTFIX)).forEach(line -> {
            if (line.contains("<source") && line.contains("</source>")) {
                String id = extractAttribute(line, "id");
                String content = extractContent(line, "source");
                sourceMap.put(id, content.trim());
            }
        });

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

                if (sourceMap.containsKey(transUnitId)) {
                    String newTargetContent = sourceMap.get(transUnitId);
                    NodeList targetList = transUnitElement.getElementsByTagName("target");

                    if (targetList.getLength() > 0) {
                        Element targetElement = (Element) targetList.item(0);
                        targetElement.setTextContent(recoverNewline(newTargetContent));
                        targetElement.setAttribute("state", "translated");
                        updatedCount++;
                    }
                }
            }
        }

        String outputFilePath = args[0] + "_update.xliff";
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(outputFilePath));
        transformer.transform(source, result);

        updatedFilePath = outputFilePath;
        logger.info(messages.getString("update_success") + updatedFilePath);
    }

    public static int getUpdatedCount() {
        return updatedCount;
    }

    public static String getUpdatedFilePath() {
        return updatedFilePath;
    }

    private static String extractAttribute(String line, String attributeName) {
        int attrStart = line.indexOf(attributeName + "=\"") + (attributeName + "=\"").length();
        int attrEnd = line.indexOf("\"", attrStart);
        return line.substring(attrStart, attrEnd);
    }

    private static String extractContent(String line, String tagName) {
        int start = line.indexOf(">") + 1;
        int end = line.indexOf("</" + tagName + ">");
        return line.substring(start, end);
    }

    private static String recoverNewline(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("${NEW_LINE}", "\n");
    }
}
