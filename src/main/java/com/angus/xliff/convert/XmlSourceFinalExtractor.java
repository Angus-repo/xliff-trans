package com.angus.xliff.convert;

import static com.angus.xliff.convert.Constans.SOURCE_FILE_NAME_POSTFIX;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class XmlSourceFinalExtractor {

	public static void main(String[] args) throws IOException, SAXException, ParserConfigurationException {
		List<String> translateNoList = new ArrayList<>(); // 存儲有 translate="no" 的 trans-unit

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
					String transUnitId = transUnitElement.getAttribute("id"); // 提取 trans-unit 的 id
					String translateAttr = transUnitElement.getAttribute("translate"); // 提取 translate 屬性

					NodeList targetList = transUnitElement.getElementsByTagName("target");
					if (targetList.getLength() > 0) {
						Node targetNode = targetList.item(0);
						Element targetElement = (Element) targetNode;

						String stateAttr = targetElement.getAttribute("state");
						if ("final".equalsIgnoreCase(stateAttr)) {

							NodeList sourceList = transUnitElement.getElementsByTagName("source");
							if (sourceList.getLength() > 0) {
								Node sourceNode = sourceList.item(0);
								String sourceText = sourceNode.getTextContent(); // 提取 source 內容

								StringBuilder sourceTag = new StringBuilder("<source id=\"" + transUnitId + "\"");

								if ("no".equalsIgnoreCase(translateAttr)) {
									sourceTag.append(" translate=\"no\"");
									translateNoList
											.add(sourceTag.toString() + ">" + escapeXml(sourceText) + "</source>\n");
								} else {
//									sourceTag.append(">").append(escapeXml(sourceText)).append("</source>\n");
									sourceTag.append(">").append(replaceNewline(sourceText)).append("</source>\n");
									writer.write(sourceTag.toString()); // 直接寫入無 translate="no" 的元素
								}
							}
						}
					}
				}
			}

			for (String item : translateNoList) {
				writer.write(item);
			}

			System.out.println("source_text.txt 已成功生成。");

		}
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
