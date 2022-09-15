/*
Copyright 2019 GEOSIRIS

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
package com.geosiris.energyml.utils;

import com.geosiris.energyml.exception.NoSuchAccessibleParameterFound;
import com.geosiris.energyml.exception.NoSuchEditableParameterFound;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.*;

public class Utils {
	public static Logger logger = LogManager.getLogger(Utils.class);

	public static ResqmlAbstractType getFIRPObjectType(Class<?> resqmlclass) {
		if(resqmlclass != null) {
			String className = resqmlclass.getName().toLowerCase();
			if(className.endsWith("feature")) {
				return ResqmlAbstractType.Feature;
			}else if(className.endsWith("interpretation")) {
				return ResqmlAbstractType.Interpretation;
			}else if(className.endsWith("representation")) {
				return ResqmlAbstractType.Representation;
			}else if(className.endsWith("property")) {
				return ResqmlAbstractType.Property;
			}else {
				return getFIRPObjectType(resqmlclass.getSuperclass());
			}
		}
		return ResqmlAbstractType.OTHERS;
	}

	public static String calendarToW3CDTF(XMLGregorianCalendar calendar) {
		return String.format("%04d", calendar.getYear()) + "-"
				+ String.format("%02d", calendar.getMonth()) + "-"
				+ String.format("%02d", calendar.getDay())
				+ "T"
				+ String.format("%02d", calendar.getHour()) + ":"
				+ String.format("%02d", calendar.getMinute()) + ":"
				+ String.format("%02d", calendar.getSecond())
				+ "Z";
	}

	public static javax.xml.datatype.XMLGregorianCalendar getCalendar(String date) {
		try {
			return DatatypeFactory.newInstance().newXMLGregorianCalendar(date);
		} catch (DatatypeConfigurationException e) {
			return null;
		}

	}

	public static javax.xml.datatype.XMLGregorianCalendar getCalendarForNow() {
		GregorianCalendar c = new GregorianCalendar();
		c.setTime(new Date());
		try {
			return DatatypeFactory.newInstance().newXMLGregorianCalendar(c);
		} catch (DatatypeConfigurationException e) {
			return null;
		}

	}

	public static<T> T createDor(Object obj, Class<T> dorClass) throws InstantiationException, IllegalAccessException, NoSuchAccessibleParameterFound, NoSuchEditableParameterFound, InvocationTargetException, NoSuchMethodException {
		T dor = dorClass.getConstructor().newInstance();
		ObjectController.editObjectAttribute(dor, "uuid", ObjectController.getObjectAttributeValue(obj, "uuid"));
		ObjectController.editObjectAttribute(dor, "title", ObjectController.getObjectAttributeValue(obj, "citation.title"));
		try {
			ObjectController.editObjectAttribute(dor, "contentType", EPCGenericManager.getObjectContentType(obj));
		}catch (Exception e){
			logger.debug(e.getMessage(), e);
			try {
				ObjectController.editObjectAttribute(dor, "qualifiedType", EPCGenericManager.getObjectQualifiedType(obj));
			}catch (Exception e2){
				logger.debug(e2.getMessage(), e2);
			}
		}
		return dor;
	}

	/**
	 * Compute the links between resqml objects
	 * @param contextObjects all object present in the current workspace
	 * @return Map<X, List<Y>> where X is an uuid and the list<Y> is a list of uuid of the object that refers to X
	 */
	public static Map<String, List<String>> getRelationShips(Map<String, Object> contextObjects) {
		Map<String, List<String>> relationships = new HashMap<>();

		for(Map.Entry<String, Object> entry : contextObjects.entrySet()){
			List<Object> dorList = ObjectController.findSubObjects(entry.getValue(), "DataObjectReference");
			for(Object dor : dorList){
				String dor_uuid = (String) ObjectController.getObjectAttributeValue(dor, "uuid");
				if(!relationships.containsKey(dor_uuid)){
					relationships.put(dor_uuid, new ArrayList<>());
				}
				relationships.get(dor_uuid).add(entry.getKey());
			}
		}

		return relationships;
	}



	public static <T> T readJsonFileOrRessource(String path, Class<?> mainClass){
		Gson gson = new Gson();
		T res = null;

		URL ressourceURL = Utils.class.getResource(path);
		BufferedReader reader = null;

		try {
			reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(Utils.class.getResourceAsStream(path))));
		} catch (Exception e) {
			try {
				reader = new BufferedReader(new FileReader(path));
			} catch (FileNotFoundException e2) {
				logger.error("Error reading " + path + " = " + ressourceURL);
				logger.debug(e.getMessage(), e);
				logger.debug(e2.getMessage(), e2);
			}
		}
		if(reader != null) {
			try {
				res = (T) gson.fromJson(reader, mainClass);
			} catch (Exception e) {
				logger.error("Error reading " + path + " = " + ressourceURL);
				logger.debug(e.getMessage(), e);
			}
		}
		return res;
	}

	public static String readFileOrRessource(String path){
		Gson gson = new Gson();
		StringBuilder res = new StringBuilder();

		URL ressourceURL = Utils.class.getResource(path);
		BufferedReader reader = null;

		try {
			reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(Utils.class.getResourceAsStream(path))));
		} catch (Exception e) {
			try {
				reader = new BufferedReader(new FileReader(path));
			} catch (FileNotFoundException e2) {
				logger.error("Error reading " + path + " = " + ressourceURL);
				logger.debug(e.getMessage(), e);
				logger.debug(e2.getMessage(), e2);
			}
		}
		if(reader != null) {
			try {
				String strCurrentLine = "";
				while ((strCurrentLine = reader.readLine()) != null) {
					res.append(strCurrentLine);
				}
			} catch (Exception e) {
				logger.error("Error reading " + path + " = " + ressourceURL);
				logger.debug(e.getMessage(), e);
			}
		}
		return res.toString();
	}

}
