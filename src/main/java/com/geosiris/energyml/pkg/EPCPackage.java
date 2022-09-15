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
package com.geosiris.energyml.pkg;

import com.geosiris.energyml.utils.ContextBuilder;
import com.geosiris.energyml.utils.ObjectController;
import com.geosiris.energyml.utils.Utils;
import jakarta.xml.bind.*;
import jakarta.xml.bind.util.ValidationEventCollector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class EPCPackage {
    public static Logger logger = LogManager.getLogger(EPCPackage.class);

    protected String xsdMappingFilePath;
    protected Map<String, JAXBContext> contextList;
    protected Map<String, Schema> xsdSchema;

    protected String name;
    protected String packagePath;


    public EPCPackage(String _name, String _path, String xsdMappingFilePath) {
        this.name = _name;
        this.xsdMappingFilePath = xsdMappingFilePath;
        this.packagePath = _path;
        contextList = ContextBuilder.createAllContext(packagePath);

        xsdSchema = new HashMap<>();
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        for(String pkg_name : contextList.keySet()){
            try{
                xsdSchema.put(pkg_name, sf.newSchema(new File(getXSDFilePathFromPkgName(pkg_name))));
            }catch (Exception e){
                logger.error("ERR: no xsd schema for package '" + pkg_name + "'");
            }
        }
    }

    public Map<String, String> getXSDFilePaths(){
        return Utils.readJsonFileOrRessource(xsdMappingFilePath, HashMap.class);
    }

    public String getXSDFilePathFromPkgName(String pkg){
        logger.debug("Searching schema for pkg : " + pkg);
        try {
            return getXSDFilePaths().get(pkg);
        }catch (Exception ignore){}
        return null;
    }

    public Schema getSchema(String pkg_name){
        try{
            return xsdSchema.get(pkg_name);
        }catch (Exception ignore){}
        return null;
    }

    protected static Map<String, List<Class<?>>> searchAllClassesForVersions(final String pkgPath) {
        return  ContextBuilder.getClassesForVersion(pkgPath);
    }

    public static String getSchemaVersionFromClassName(String pkgPath, String className) {
        if (className != null) {
            logger.debug("@getSchemaVersionFromClassName " + pkgPath + Utils.PATTERN_PKG_VERSION + " == " + className);
            Pattern pattern = Pattern.compile(pkgPath + Utils.PATTERN_PKG_VERSION);
            Matcher match = pattern.matcher(className);
            if (match.find()) {
                return match.group("version");
            }
        }
        return null;
    }

    public static String reformatSchemaVersion(String schemaVersion) {
        if (schemaVersion != null) {
            while (schemaVersion.startsWith(".") || schemaVersion.startsWith("_")) {
                schemaVersion = schemaVersion.substring(1);
            }
            Matcher match = Pattern.compile(Utils.PATTERN_SCHEMA_VERSION + "$").matcher(schemaVersion);
            if (match.find()) {
                return match.group("version").replaceAll("[\\._]+", ".");
            }
            return "";
        }
        return null;
    }

    abstract public List<String> getAllVersionsPackagePath();

    abstract public String getObjectContentType(Object obj);

    public String getObjectQualifiedType(Object obj) {
        StringBuilder version = new StringBuilder(getSchemaVersion(obj).replaceAll("\\.", ""));
        while (version.length() <= 1){
            version.append("0");
        }
        if(version.length()>2){
            version = new StringBuilder(version.substring(0, 2));
        }
        return this.name + version + "." + getObjectTypeForFilePath(obj);
    }

    abstract public List<Class<?>> getRootsElementsClasses();

    abstract public Map<String, List<Class<?>>> getAllClassesForVersion();

    public JAXBContext getContext(String pkgPath) {
        return contextList.get(pkgPath);
    }

    protected List<String> getAllVersionsPackagePath(Map<String, List<Class<?>>> pkgClasses) {
        return new ArrayList<>(pkgClasses.keySet());
    }

    public List<Class<?>> getRootsElementsClasses(Map<String, List<Class<?>>> pkgClasses) {
        List<Class<?>> rootClasses = new ArrayList<>();
        for (List<Class<?>> clList : pkgClasses.values()) {
            for (Class<?> cl : clList) {
                if (isRootClass(cl)) {
                    rootClasses.add(cl);
                }
            }
        }
        return rootClasses;
    }

    public String getObjectTypeForFilePath(Object obj) {
        String objType = obj.getClass().getSimpleName();
        String schemaVersion = getSchemaVersion(obj);
        if (schemaVersion.startsWith("2.0") && objType.startsWith("Obj")) {
            objType = objType.replace("Obj", "obj_");
        }
        objType = objType.replaceAll("(\\d+)D", "$1d");
        return objType;
    }

    public Object createInstance(String className, String version) {
        Object result = null;
        String simpleClassName = className;
        if (simpleClassName.contains(".")) {
            simpleClassName = simpleClassName.substring(simpleClassName.lastIndexOf(".") + 1);
        }
        try {
            Object objFactory = getObjectFactory(version);
            if (objFactory == null) {
                throw new Exception("No " + packagePath + " factory found");
            }
            Method mCreate = objFactory.getClass().getMethod("create" + simpleClassName);

            result = mCreate.invoke(objFactory);
        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.debug(e.getMessage(), e);
        }

        if (result == null) {
            // Si la methode createXXX n'existe pas on cree l'instance sans la factory
            try {
                result = Class.forName(className).getConstructor().newInstance();
            } catch (Exception e) {
                logger.error(e.getMessage());
                logger.debug(e.getMessage(), e);
            }
        }
        return result;
    }

    public Boolean isClassNameMatchesPackage(String className) {
        for (String pkgPath : getAllVersionsPackagePath()) {
            if (className.startsWith(pkgPath + "."))
                return true;
        }
        return false;
    }



    public Object getObjectFactory(String version) {
        try {
            String resqmlPackage = getPackagePath(version);
            Class<?> objClass = Class.forName(resqmlPackage + ".ObjectFactory");
            return objClass.getConstructor().newInstance();
        } catch (Exception e) {
            logger.error("Error in getResqmlFactory with schemaVersion '" + version + "'");
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    public String getPackagePath(String version) {
        return packagePath + version.replace(".", "_");
    }

    public String getSchemaVersionFromClassName(String className) {
        if (className != null) {
            Pattern pattern = Pattern.compile(this.packagePath + Utils.PATTERN_PKG_VERSION);
            Matcher match = pattern.matcher(className);
            if (match.find()) {
                return match.group("version");
            }
        }
        return null;
    }

    public String getSchemaVersion(Object obj) {
        String scheme = null;
        if (obj != null) {
            scheme = (String) ObjectController.getObjectAttributeValue(obj, "SchemaVersion");
            if (scheme == null || scheme.length() <= 0) {
                scheme = getSchemaVersionFromClassName(obj.getClass().getName());
            }
        }
        return reformatSchemaVersion(scheme);
    }

    public static Boolean isRootClass(Class<?> objClass) {
        if (objClass != null) {
            return ObjectController.hasSuperClassSuffix(objClass, "AbstractObject");
        }
        return false;
    }

    public String getNamespace() {
        return this.name;
    }

    public JAXBElement<?> parseXmlContent(String xmlContent) {
        long ticBegin = System.currentTimeMillis();

        List<String> schemaVersionFound = new ArrayList<>();

        long ticBeginVersStringMatcher = System.currentTimeMillis();
        Matcher match = Utils.PATTERN_SCHEMA_VERSION_IN_XML.matcher(xmlContent);
        boolean matchFound = match.find();
        long ticEndVersStringMatcher = System.currentTimeMillis();

        if (matchFound) {
            schemaVersionFound.add(reformatSchemaVersion(match.group("version")));
        } else {
            List<String> pkgVersion = ContextBuilder.getPackagesVersions(packagePath);
            Collections.reverse(pkgVersion); // Reversing to parse at first the last version
            schemaVersionFound.addAll(pkgVersion);
        }

        JAXBElement<?> result = null;
        for (String version : schemaVersionFound) {
            logger.debug(">Trying to parse from version found = '" + version + "' " + contextList.keySet());
            String pkgPath = getPackagePath(version);
            if (contextList.containsKey(pkgPath)) {
                JAXBContext context = contextList.get(pkgPath);
                Schema schema = null;
                try{schema = xsdSchema.get(pkgPath);}catch (Exception e){logger.error(e.getMessage(), e);}
                result = parseXmlFromContext(context, xmlContent, true, schema);
                if (result != null) {
                    logger.debug("Success reading with '" + pkgPath + "' object class : "
                            + result.getValue().getClass().getName());
                    break;
                } else {
                    logger.error("error reading with package " + pkgPath);
                }
            } else {
                logger.debug("no context found for " + pkgPath + " for file content : "
                        + xmlContent.substring(0, Math.min(xmlContent.length(), 200)));
            }
        }

        long ticEnd = System.currentTimeMillis();
        logger.debug("\t@parseXmlContent : Parsing file took " + (ticEnd - ticBegin) / 1000.0 + "s");
        logger.debug("\t\t : getSchemaVersion took " + (ticEndVersStringMatcher - ticBeginVersStringMatcher) / 1000.0 + "s");
        return result;
    }

    private JAXBElement<?> parseXmlFromContext(JAXBContext context, String xmlContent,
                                               boolean tryWithoutNamespaceIfFail,
                                               Schema schema) {
        Unmarshaller unmarshaller;
        ValidationEventCollector vec = null;

        try {
            long ticCreateUnmarshaller_b = System.currentTimeMillis();
            unmarshaller = context.createUnmarshaller();
            if(schema != null)
                unmarshaller.setSchema(schema);

            long ticCreateUnmarshaller_e = System.currentTimeMillis();
            logger.debug("\t@parseXmlFromContext : Creating unmarshaller " + (ticCreateUnmarshaller_e - ticCreateUnmarshaller_b) / 1000.0 + "s");

            vec = new ValidationEventCollector();
            unmarshaller.setEventHandler(vec);

            ByteArrayInputStream bais = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
            long ticUnmarshall_b = System.currentTimeMillis();
            JAXBElement<?> result = (JAXBElement<?>) unmarshaller.unmarshal(bais);
            long ticUnmarshall_e = System.currentTimeMillis();
            logger.debug("\t@parseXmlFromContext : Unmarshalling took " + (ticUnmarshall_e - ticUnmarshall_b) / 1000.0 + "s");

            logger.debug("result  " + result);
            if (vec.getEvents().length > 0) {
                for (ValidationEvent ev : vec.getEvents()) {
                    logger.debug("event : " + ev);
                }
                logger.debug("events found");
                return null;
            } else if (result != null) {

                logger.debug("no events found");
                long ticMarshall_b = System.currentTimeMillis();
                String xmlNewContent = marshal(result.getValue());
                long ticMarshall_e = System.currentTimeMillis();
                logger.debug("\t@parseXmlFromContext : Marshalling took " + (ticMarshall_e - ticMarshall_b) / 1000.0 + "s");


                long ticCountingChevron_b = System.currentTimeMillis();
                int countOld = 0;
                int countNew = 0;
                for (int i = 0; i < xmlNewContent.length(); i++)
                    if (xmlNewContent.charAt(i) == '<')
                        if (xmlNewContent.charAt(i + 1) != '?') // On ne compte pas le <?xml du dÃ©part qui n'est pas
                            // toujours la
                            countNew++;

                for (int i = 0; i < xmlContent.length(); i++)
                    if (xmlContent.charAt(i) == '<')
                        if (xmlContent.charAt(i + 1) != '?') // On ne compte pas le <?xml du dÃ©part qui n'est pas
                            // toujours la
                            countOld++;

                long ticCountingChevron_e = System.currentTimeMillis();

                logger.debug("\t@parseXmlFromContext : Counting chevron cost " + (ticCountingChevron_e - ticCountingChevron_b) / 1000.0 + "s");

                if (countOld > countNew) {
                    logger.error("Error reading object class : " + result.getValue().getClass().getName()
                            + " old count of '<' : " + countOld + " new is : " + countNew);
                    if (tryWithoutNamespaceIfFail) {

                        logger.debug("Trying without namespace");
                        String contentNoNamespace = xmlContent.replaceAll("<(/?)[a-zA-Z0-9_]+:([\\w\\d_]+)", "<$1$2");
                        return parseXmlFromContext(context, contentNoNamespace, false, schema);
                    } else {
                        return null;
                    }
                }
                return result;
            }
        } catch (Exception e) {
            if(schema != null){
                logger.error(e.getCause() + " " + e.getMessage());
                // try to parse without schema
                return parseXmlFromContext(context, xmlContent, tryWithoutNamespaceIfFail, null);
            }
            logger.debug(e.getMessage(), e);
            logger.debug("File not read : ");
            logger.debug(xmlContent);
            if(vec != null){
                logger.debug("\tRead event [" + vec.getEvents().length + "]");
                for (ValidationEvent ev : vec.getEvents()) {
                    logger.debug("event : " + ev);
                }
            }
        }
        return null;
    }

    public void marshal(Object obj, OutputStream os) {
        String pkgVersion = getSchemaVersionFromClassName(obj.getClass().getName());
        JAXBContext context = getContext(getPackagePath(getSchemaVersionFromClassName(obj.getClass().getName())));
        if (context != null) {
            try {
                Marshaller marshaller = context.createMarshaller();
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
                marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
                marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);

                Object factory = getObjectFactory(pkgVersion);
                try {
                    JAXBElement<?> elt = EPCPackageManager.wrap(obj, factory);
                    marshaller.marshal(elt, os);
                } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
                         | InvocationTargetException e) {
                    logger.error(e.getMessage(), e);
                }
            } catch (JAXBException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    public String marshal(Object obj) {
        try {
            OutputStream os = new ByteArrayOutputStream();
            marshal(obj, os);
            return os.toString();
        }catch (Exception e){
            logger.error("Error marshalling object " + obj);
            logger.debug(e.getMessage(), e);
        }
        return null;
    }

    public String getName() {
        return name;
    }

}
