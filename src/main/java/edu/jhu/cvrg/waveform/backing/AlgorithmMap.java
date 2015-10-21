package edu.jhu.cvrg.waveform.backing;
/*
Copyright 2013 Johns Hopkins University Institute for Computational Medicine

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
/**
* @author Chris Jurado, Mike Shipway
* 
*/
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;

import org.apache.axiom.om.OMElement;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.jhu.cvrg.waveform.utility.ResourceUtility;
import edu.jhu.cvrg.waveform.utility.WebServiceUtility;

@ManagedBean(name = "algorithmMap")
@ViewScoped
public class AlgorithmMap implements Serializable {

	private static final long serialVersionUID = 8596023632774091195L;

	private Map<String, String> availableAlgorithms;

	public AlgorithmMap() {

		availableAlgorithms = new LinkedHashMap<String, String>();
		try {
			fetchAlgorithms();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	

	public void fetchAlgorithms() throws Exception {

		String xml="";
		try {

			String sServiceURL = ResourceUtility.getAnalysisServiceURL(); // e.g. "http://icmv058.icm.jhu.edu:8080/axis2/services"
			String sServiceName = ResourceUtility.getPhysionetAnalysisService(); // e.g. "/physionetAnalysisService"
			String sMethod = ResourceUtility.getAlgorithmDetailsMethod();
			LinkedHashMap<String, String> parameterMap = new LinkedHashMap<String, String>();
			parameterMap.put("verbose", String.valueOf(false));
			
			OMElement result = WebServiceUtility.callWebService(parameterMap, sMethod, sServiceName, 
					sServiceURL, null);

			StringWriter writer = new StringWriter();
			result.serialize(XMLOutputFactory.newInstance().createXMLStreamWriter(writer));
			writer.flush();
			xml = writer.toString();
			
			InputStream inStream = new ByteArrayInputStream(xml.getBytes("UTF-8"));
			DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
			Document document = docBuilder.parse(inStream);
			
			document.getDocumentElement().normalize();
			
			NodeList algorithmNodes = document.getElementsByTagName("AlgorithmServiceData");
			
			for(int i = 0; i < algorithmNodes.getLength(); i++){
				
				Node node = algorithmNodes.item(i);
				String name = "";
				String method = "";
				
				for(int s = 0; s < node.getChildNodes().getLength(); s++){
					Node childNode = node.getChildNodes().item(s);

					if(childNode.getNodeName().equals("sDisplayShortName")){
						name = childNode.getFirstChild().getNodeValue();
					}
					if(childNode.getNodeName().equals("sServiceMethod")){
						method = childNode.getFirstChild().getNodeValue();
					}
				}
				availableAlgorithms.put(name, method);
			}

		} catch(Exception ex) {
			ex.printStackTrace();
			throw ex;
		}	
	}

	public Map<String, String> getAvailableAlgorithms() {
		return availableAlgorithms;
	}
}
