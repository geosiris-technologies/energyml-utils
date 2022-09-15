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
import com.geosiris.energyml.pkg.EPCRelsRelationshipType;
import com.geosiris.energyml.pkg.OPCContentType;
import com.geosiris.energyml.pkg.OPCCorePackage;
import com.geosiris.energyml.pkg.OPCRelsPackage;
import com.google.gson.Gson;
import energyml.content_types.Default;
import energyml.content_types.Override;
import energyml.content_types.Types;
import energyml.core_properties.CoreProperties;
import energyml.relationships.Relationship;
import energyml.relationships.Relationships;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.purl.dc.elements._1.SimpleLiteral;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Utils {
	public static Logger logger = LogManager.getLogger(Utils.class);

	public static final Pattern PATTERN_SCHEMA = Pattern.compile("energyml\\.(?<pkgId>(?<pkgName>[a-zA-Z\\d]+)(?<devVersion>_dev[\\d]+[a-zA-Z]+_)?(?<pkgVersion>[\\d]+([\\._][0-9]+)+))\\.(?<className>[\\w\\d]+)$");
	public final static Pattern PATTERN_PKG_VERSION = Pattern.compile("(?<version>_?((?<dev>dev[\\d]+)x_)?(?<versionNum>([\\d]+[_])*\\d))");
	public final static Pattern PATTERN_SCHEMA_VERSION = Pattern.compile("_?(?<version>((?<dev>dev[\\d]+)x_)?(?<versionNum>([\\d]+[\\._])*\\d))");
	public final static Pattern PATTERN_SCHEMA_VERSION_IN_XML = Pattern.compile("schemaVersion=\"(?<version>[^\"]+)\"");
	public static Pattern PATTERN_MATCH_ETP_VERSION = Pattern.compile("(?<digit2Version>[\\d]+(\\.[\\d]+)?)(\\.[\\d]+)*");

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
	public static Boolean isResqml201(Object obj){
		String className = obj.getClass().getName();
		return className.contains("resqml2_0") || className.contains("common2_0");
	}

	public static Boolean isResqml22_final(Object obj){
		String className = obj.getClass().getName();
		return className.contains("resqml2_2") || className.contains("common2_3");
	}

	public static String getSchemaVersion(Object obj){
		return getSchemaVersionFromClassName(obj.getClass().getName());
	}

	public static String getSchemaVersionFromClassName(String className){
		String pkg = className.substring(0, className.lastIndexOf("."));
		Pattern pat = Pattern.compile(Utils.PATTERN_PKG_VERSION + "$");
		Matcher match = pat.matcher(pkg);
		if(match.find()){
			return match.group("versionNum").replace("_", ".") + (match.group("dev")!=null? match.group("dev"):"");
		}
		return null;
	}

	public static<T> T createDor(Object obj, Class<T> dorClass) throws InstantiationException, IllegalAccessException, NoSuchAccessibleParameterFound, NoSuchEditableParameterFound, InvocationTargetException, NoSuchMethodException {
		T dor = dorClass.getConstructor().newInstance();
		ObjectController.editObjectAttribute(dor, "uuid", ObjectController.getObjectAttributeValue(obj, "uuid"));
		ObjectController.editObjectAttribute(dor, "title", ObjectController.getObjectAttributeValue(obj, "citation.title"));
		try {
			ObjectController.editObjectAttribute(dor, "contentType", getContentType(obj));
		}catch (Exception e){
			logger.debug(e.getMessage(), e);
			try {
				ObjectController.editObjectAttribute(dor, "qualifiedType", getQualifiedType(obj));
			}catch (Exception e2){
				logger.debug(e2.getMessage(), e2);
			}
		}
		return dor;
	}

	public static String getContentType(Object obj){
		StringBuilder contentType = new StringBuilder();
		if(obj != null){
			contentType.append("application/x-");
			if(obj.getClass().getName().contains("energyml.common")){
				contentType.append("eml");
			}else {
				Matcher match = PATTERN_SCHEMA.matcher(obj.getClass().getName());
				if (match.find()) {
					contentType.append(match.group("pkgName"));
				}
			}
			contentType.append("+xml;version=");
			contentType.append(getSchemaVersionFromClassName(obj.getClass().getName()));
			contentType.append(";type=");
			contentType.append(obj.getClass().getSimpleName());
		}
		return contentType.toString();
	}


	public static String getQualifiedType(Object obj){
		return getEnergymlPackageName(obj) + "." + getObjectTypeForFilePath(obj);
	}

	public static String getResqmlObjectType(Object o) {
		String objType = o.getClass().getSimpleName();
		String resqmlVersion = getSchemaVersion(o);
		if(resqmlVersion.startsWith("2.0") && objType.startsWith("Obj")) {
			objType = objType.replace("Obj", "obj_");
		}
		objType = objType.replaceAll("([0-9]+)D", "$1d");
		return objType;
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

	public static String genPathInEPC(Object obj, ExportVersion version){
		StringBuilder sb = new StringBuilder();
		if (obj != null) {
			switch (version) {
				case EXPANDED:
					try {
						String objVersion = (String) ObjectController.getObjectAttributeValue(obj, "ObjectVersion");
						sb.append("namespace_").append(getEnergymlPackageName(obj));
						if (objVersion != null && objVersion.length() > 0) {
							sb.append("/");
							sb.append("version_");
							sb.append(objVersion);
						}
						sb.append("/");
					}catch (Exception e) {
						logger.error("Error reading object verson");
					}
					break;
				case CLASSIC:
				default:
					break;
			}
		}
		sb.append(getEPCObjectFileName(obj));
		return sb.toString();
	}

	public static String getEnergymlPackageName(Object obj){
		Matcher m1 = Utils.PATTERN_SCHEMA.matcher(obj.getClass().getName());
		if (m1.find()){
			String version = m1.group("pkgVersion").replaceAll("[\\._]", "");
			if(version.length()<=1){
				version += "0";
			}
			return m1.group("pkgName") + version;
		}
		return "";
	}

	public static String getEPCObjectFileName(Object ao){
		if(ao != null)
			return ao.getClass().getSimpleName() + "_" + ObjectController.getObjectAttributeValue(ao, "uuid") + ".xml";
		return "";
	}

	public static String getObjectTypeForFilePath(Object obj) {
		String objType = obj.getClass().getSimpleName();
		String schemaVersion = getSchemaVersion(obj);
		if (schemaVersion.startsWith("2.0") && objType.startsWith("Obj")) {
			objType = objType.replace("Obj", "obj_");
			objType = objType.replaceAll("([\\d]+)D", "$1d");
		}
		return objType;
	}

	public static void marshal(JAXBContext context, Object factory, Object obj, OutputStream out){
		try {
			Marshaller marshaller = context.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
			marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);

			out.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>".getBytes());

			if (factory != null) {
				try {
					JAXBElement<?> elt = wrap(obj, factory);
					marshaller.marshal(elt, out);
				} catch (SecurityException | IllegalArgumentException  e) {
					logger.error(e.getMessage(), e);
				}
			}else{
				marshaller.marshal(obj, out);
			}
		} catch (JAXBException | IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public static JAXBElement<?> wrap(Object resqmlObject, Object factory) throws
			SecurityException, IllegalArgumentException {
		final Class<?> class22 = resqmlObject.getClass();
		final String typename = class22.getSimpleName();

		Class<?> classFactory = factory.getClass();

		try {
			Method create = classFactory.getMethod("create" + typename, class22);
			return (JAXBElement<?>) create.invoke(factory, resqmlObject);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}

	public static Map<String, String> exportRels(
			Map<String, Object> workspace,
			Map<String, Relationships> previousExternalPartRels,
			ExportVersion exportVersion,
			ZipOutputStream out,
			String creator) throws IOException {

		Map<String, List<String>> relationsDest = Utils.getRelationShips(workspace);

		HashMap<String, String> relsFiles = new HashMap<>();

		/* [CONTENT_TYPES].xml */
		Types contentTypeFile = new Types();

		/* Computing rels */

		Default defaultRels = new Default();
		defaultRels.setContentType(OPCCorePackage.getCoreContentType());
		defaultRels.setExtension(OPCRelsPackage.getRelsExtension());
		contentTypeFile.getDefaultOrOverride().add(defaultRels);

		for (String uuid : workspace.keySet()) {
			Object resqmlObj = workspace.get(uuid);

			List<String> dest = relationsDest.get(uuid);
			if (dest == null) dest = new ArrayList<>();

			List<String> source = ObjectController.findSubObjects(resqmlObj, "DataObjectReference").stream()
					.map(obj -> {
						try{ return (String) ObjectController.getObjectAttributeValue(obj, "uuid");
						}catch (Exception ignore){}
						return null;
					}).filter(Objects::nonNull).collect(Collectors.toList());

			Relationships rels = new Relationships();

			for(String dest_rel : dest){
				Relationship rel = new Relationship();
				rel.setType(EPCRelsRelationshipType.DestinationObject.getType());
				rel.setId("_" + Utils.genPathInEPC(workspace.get(dest_rel), exportVersion));
				rel.setTarget(Utils.genPathInEPC(workspace.get(dest_rel), exportVersion));
				rels.getRelationship().add(rel);
			}
			for(String source_rel : source){
				Relationship rel = new Relationship();
				rel.setType(EPCRelsRelationshipType.SourceObject.getType());
				rel.setId("_" + Utils.genPathInEPC(workspace.get(source_rel), exportVersion));
				rel.setTarget(Utils.genPathInEPC(workspace.get(source_rel), exportVersion));
				rels.getRelationship().add(rel);
			}


			if (resqmlObj.getClass().getSimpleName().toLowerCase().endsWith("externalpartreference")) {
//				String nameInPartRef = (String) ObjectController.getObjectAttributeValue(resqmlObj, "Filename");

				// Searching in previous rels (exists if an epc has been loaded and then modified)
				for(String oldRelName: previousExternalPartRels.keySet()){
					if(oldRelName.contains(uuid)){
						Relationships externalPartRels = previousExternalPartRels.get(oldRelName);
						List<Relationship> h5rels = externalPartRels.getRelationship().stream().filter((oldRel) -> oldRel.getId().toLowerCase().contains("hdf5file")).collect(Collectors.toList());
						for (Relationship h5Rel : h5rels) {
							Relationship rel = new Relationship();
							rel.setId("Hdf5File");
//							rel.setTarget(nameInPartRef);
							rel.setTarget(h5Rel.getTarget());
							rel.setType(EPCRelsRelationshipType.ExternalResource.getType());
							rels.getRelationship().add(rel);
						}
					}
				}
			}

			String objectFilePath = OPCRelsPackage.genRelsPathInEPC(resqmlObj, exportVersion);
			ZipEntry ze_resqml = new ZipEntry(objectFilePath);
			out.putNextEntry(ze_resqml);
			marshal(OPCRelsPackage.JAXB_CONTEXT, null, rels, out);
			out.closeEntry();

			Override overrideObjRels = new Override();
			overrideObjRels.setContentType(Utils.getContentType(resqmlObj));
			overrideObjRels.setPartName("/" + Utils.genPathInEPC(resqmlObj, exportVersion)); // On ajoute le "/" car obligatoire pour etre lu dans ResqmlCAD
			contentTypeFile.getDefaultOrOverride().add(overrideObjRels);
		}

		// Rels Root File
		Relationships root_rels = new Relationships();
		Relationship root_core_rel = new Relationship();
		root_core_rel.setId("CoreProperties");
		root_core_rel.setTarget(OPCCorePackage.genCorePath());
		root_core_rel.setType(EPCRelsRelationshipType.ExtendedCoreProperties.getType());
		root_rels.getRelationship().add(root_core_rel);

		ZipEntry ze_root_rels = new ZipEntry(OPCRelsPackage.genRelsFolderPath(exportVersion) + "/." + OPCRelsPackage.getRelsExtension());
		out.putNextEntry(ze_root_rels);
		marshal(OPCRelsPackage.JAXB_CONTEXT, null, root_rels, out);
		out.closeEntry();

		/* Core file */
		Override overrideCore = new Override();
		overrideCore.setContentType(OPCCorePackage.getCoreContentType());
		overrideCore.setPartName("/" + OPCCorePackage.genCorePath());
		contentTypeFile.getDefaultOrOverride().add(overrideCore);

		CoreProperties coreProperties = new CoreProperties();
		coreProperties.setVersion("1.0");

		SimpleLiteral sl_creator = new SimpleLiteral();
		sl_creator.getContent().add(creator);
		coreProperties.setCreator(sl_creator);

		SimpleLiteral sl_creationDate = new SimpleLiteral();
		XMLGregorianCalendar now = Utils.getCalendarForNow();
		sl_creationDate.getContent().add(Utils.calendarToW3CDTF(now));
		coreProperties.setCreated(sl_creationDate);

		ZipEntry ze_core = new ZipEntry(OPCCorePackage.genCorePath());
		out.putNextEntry(ze_core);
		marshal(OPCCorePackage.JAXB_CONTEXT, null, coreProperties, out);
		out.closeEntry();


		// ContentTypeFile
		ZipEntry ze_contentType = new ZipEntry(OPCContentType.genContentTypePath());
		out.putNextEntry(ze_contentType);
		marshal(OPCContentType.JAXB_CONTEXT, null, contentTypeFile, out);
		out.closeEntry();

		return relsFiles;
	}

	public static <T> T readJsonFileOrRessource(String path, Class mainClass){
		Gson gson = new Gson();
		T res = null;

		URL ressourceURL = Utils.class.getResource(path);
		Reader reader = null;

		try {
			reader = new InputStreamReader(Utils.class.getResourceAsStream(path));
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

}
