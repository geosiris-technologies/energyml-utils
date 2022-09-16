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

import com.geosiris.energyml.exception.EPCPackageInitializationException;
import com.geosiris.energyml.utils.ContextBuilder;
import com.geosiris.energyml.utils.EPCGenericManager;
import com.geosiris.energyml.utils.ObjectController;
import com.geosiris.energyml.utils.Utils;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EPCPackageManager {
    public static Logger logger = LogManager.getLogger(EPCPackageManager.class);

    public static final String DEFAULT_EPC_PACKAGE_GROUPS_FILE_PATH = "/config/epcPackagesGroups.json";
    public static final String DEFAULT_ACCESSIBLE_DOR_FILE_PATH = "/config/resqmlAccessibleDORMapping.json";
    public static final String DEFAULT_XSD_COMMENTS_FOLDER_PATH = "/config/comments/";
    public static final String DEFAULT_EXT_TYPES_ATTRIBUTES_FOLDER_PATH = "/config/extTypesAttributes/";

    public final static String DEFAULT_CITATION_TITLE = "Empty";
    public final static String DEFAULT_CITATION_FORMAT = "Geosiris WebStudio";
    public final static String DEFAULT_CITATION_ORIGINATOR = "Geosiris user";

    public final static Pattern PATTERN_XML_SCHEMA_VERSION_ATTRIBUTE = Pattern
            .compile("schemaVersion=\"(?<schemaVersion>[^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private String xsdCommentsFolderPath;
    private final String accessibleDORFilePath;
    public final List<EPCPackage> PKG_LIST;

    public EPCPackageManager(String energymlPkgPrefix, String xsdCommentsFolderPath, String accessibleDORFilePath,
            String xsdMappingFilePath) {
        this(initPkgList(energymlPkgPrefix, xsdMappingFilePath), xsdCommentsFolderPath, accessibleDORFilePath);
    }

    public EPCPackageManager(List<EPCPackage> pkgList, String xsdCommentsFolderPath, String accessibleDORFilePath) {
        if (xsdCommentsFolderPath != null) {
            this.xsdCommentsFolderPath = xsdCommentsFolderPath.replace("\\", "/");
            if (!this.xsdCommentsFolderPath.endsWith("/")) {
                this.xsdCommentsFolderPath += "/";
            }
        } else {
            this.xsdCommentsFolderPath = "";
        }
        if (accessibleDORFilePath != null) {
            this.accessibleDORFilePath = accessibleDORFilePath.replace("\\", "/");
        } else {
            this.accessibleDORFilePath = "";
        }
        this.PKG_LIST = pkgList;
        logger.info("EPCPackageManager initialized found packages :");
        for(EPCPackage pkg : pkgList){
            logger.info(pkg.getPackageName() + " nb class found " + pkg.getPkgClasses().size());
        }
    }

    public static List<EPCPackage> initPkgList(String energymlPkgPrefix, String xsdMappingFilePath) {
        Map<String, String> xsdMapping = Utils.readJsonFileOrRessource(xsdMappingFilePath, HashMap.class);

		final ClassLoader sysLoader = Thread.currentThread().getContextClassLoader();

        return ContextBuilder.findAllEnergymlPackages(energymlPkgPrefix).parallelStream().map(
                (pkgPath) -> {
                    String xsdPath = null;
                    if (xsdMapping != null && xsdMapping.containsKey(pkgPath)) {
                        xsdPath = xsdMapping.get(pkgPath);
                    } else {
                        logger.error("@initPkgList: xsd path not found in mappin '" + xsdMappingFilePath
                                + "' for package '" + pkgPath + "'");
                    }
                    try {
                        return new EPCPackage(pkgPath, xsdPath, sysLoader);
                    } catch (EPCPackageInitializationException e) {
                        logger.error(
                                "@initPkgList: error during package instanciation for package path '" + pkgPath + "'");
                    }
                    return null;
                }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public static String getEPCPackagesGroups(String epcPackageGroupPath) {
        String fPath = Objects.requireNonNull(EPCPackageManager.class.getResource(epcPackageGroupPath)).getFile();
        fPath = fPath.replaceAll("%20", " ");
        fPath = fPath.replaceAll("%5", "\\");
        File file = new File(fPath);
        try {
            return Files.readAllLines(file.toPath()).stream().reduce("", String::concat);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return "[]";
    }

    public EPCPackage getMatchingPackage(Class<?> objClass) {
        return getMatchingPackage(objClass.getName());
    }

    public EPCPackage getMatchingPackage(String objClassName) {
        if (objClassName != null) {
            for (EPCPackage pkg : PKG_LIST) {
                if (pkg.isClassNameMatchesPackage(objClassName)) {
                    return pkg;
                }
            }
        }
        return null;
    }

    public Boolean hasDevVersion(EPCPackage refPkg) {
            for (EPCPackage pkg : PKG_LIST) {
                if (pkg.getName().compareToIgnoreCase(refPkg.getName()) == 0
                        && pkg.getPackagePath().compareTo(refPkg.getPackagePath()) !=0
                        && pkg.getVersionNum().compareTo(refPkg.getVersionNum()) == 0
                        && pkg.isDevVersion()) {
                    return true;
                }
            }
        return false;
    }

    public List<String> getPackagesPath() {
        return PKG_LIST.stream().map(EPCPackage::getPackagePath).collect(Collectors.toList());
    }

    public JAXBElement<?> unmarshal(String xmlContent) {
        String schemaVersionFound = null;
        Matcher matcherXMLSchemaV = PATTERN_XML_SCHEMA_VERSION_ATTRIBUTE.matcher(xmlContent);
        if (matcherXMLSchemaV.find()) {
            schemaVersionFound = EPCGenericManager.reformatSchemaVersion(matcherXMLSchemaV.group("schemaVersion"));
            logger.info("SchemaVersionFound " + schemaVersionFound);
        }
        for (EPCPackage pkg : PKG_LIST) {
            if (schemaVersionFound == null || pkg.getVersionNum().compareToIgnoreCase(schemaVersionFound) == 0) {
                logger.info("Trying to read with " + pkg.getPackagePath());
                try {
                    JAXBElement<?> obj = pkg.parseXmlContent(xmlContent, hasDevVersion(pkg));
                    if (obj != null) {
                        return obj;
                    }
                }catch (Exception e){
                    logger.debug(e.getMessage(), e);
                }
            }
        }
        // Testing with other package if failed with pkg matching schemaVersion
        if (schemaVersionFound != null) {
            for (EPCPackage pkg : PKG_LIST) {
                if (pkg.getVersionNum().compareToIgnoreCase(schemaVersionFound) != 0) {
                    try {
                        JAXBElement<?> obj = pkg.parseXmlContent(xmlContent, true);
                        if (obj != null) {
                            return obj;
                        }
                    }catch (Exception e){
                        logger.debug(e.getMessage(), e);
                    }
                }
            }
        }
        return null;
    }

    public JAXBElement<?> unmarshal(byte[] xmlContent) {
        return unmarshal(xmlContent, StandardCharsets.UTF_8);
    }

    public JAXBElement<?> unmarshal(byte[] xmlContent, Charset charsets) {
        return unmarshal(new String(xmlContent, charsets));
    }

    public String marshal(Object obj) {
        try {
            OutputStream os = new ByteArrayOutputStream();
            marshal(obj, os);
            return os.toString();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    public void marshal(Object obj, OutputStream os) {
        EPCPackage pkg = getMatchingPackage(obj.getClass());
        if (pkg != null) {
            pkg.marshal(obj, os);
        }
    }

    public boolean validate(Object obj) throws JAXBException {
        EPCPackage pkg = getMatchingPackage(obj.getClass());
        return pkg.validate(obj);
    }

    public Object createInstance(String objClassName, Map<String, Object> epcObjects,
            Object value, boolean nullable) {
        return createInstance(objClassName, epcObjects, value, null, nullable);
    }

    public Object createInstance(String objClassName, Map<String, Object> epcObjects,
            Object value) {
        return createInstance(objClassName, epcObjects, value, null, true);
    }

    public Object createInstance(String objClassName, Map<String, Object> epcObjects,
            Object value, String userName, boolean nullable) {
        Object objInstance = null;
        String objClassLower = objClassName.toLowerCase();

        if (objClassLower.endsWith("xmlgregoriancalendar")) {
            if (value != null) {
                return Utils.getCalendar(value.toString());
            }
        }

        Class<?> objectClass = getClassFromName(objClassName);

        if (objectClass != null) {
            objClassName = objectClass.getName();

            // Si c'est un enum
            if (objectClass.isEnum()) {
                // On commence par tester si une fonction 'fromValue' existe
                try {
                    if (value != null && (value + "").toLowerCase().compareTo("null") != 0) {
                        Method fromValueMethod = objectClass.getMethod("fromValue", String.class);
                        objInstance = fromValueMethod.invoke(null, value);
                    } else if (!nullable) {
                        objInstance = objectClass.getEnumConstants()[0];
                    }
                } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException e) {
                    for (Object enumVal : objectClass.getEnumConstants()) {
                        if ((enumVal + "").compareTo(value + "") == 0) {
                            objInstance = enumVal;
                            break;
                        }
                    }
                    if (objInstance == null) {
                        objInstance = objectClass.getEnumConstants()[0];
                    }
                    logger.error(e.getMessage(), e);
                }

            } else { // Not an enum type
                if (objClassName.compareTo(String.class.getName()) == 0) {
                    if (value != null && value != "null") {
                        objInstance = value + "";
                    } else {
                        objInstance = "";
                    }
                } else {

                    EPCPackage packageMatch = getMatchingPackage(objectClass);
                    logger.error("EPCManager : Matching package : " + packageMatch);
                    if (packageMatch != null) {
                        logger.error(
                                "EPCManager :  for package " + packageMatch.getName());
                        objInstance = packageMatch.createInstance(objClassName);
                        logger.error("objInstance " + objInstance);
                        modifyNewInstance(objInstance, epcObjects, value, userName);
                    } else {
                        StringBuilder stackTrace = new StringBuilder();
                        // TODO : ajouter les noms de fonction possible pour construire un objet
                        // a partir d'un string
                        String[] fromStringStaticFuncList = { "parse", "fromString", "fromValue" };
                        // TODO : ajouter les suffixes de nom de classe a tester : expl : "Impl" pour
                        // "XMLGregorianCalendar"
                        String[] sffixImplementationOfAbstract = { "Impl" };

                        try {
                            objInstance = objectClass.getConstructor(String.class).newInstance(value);
                        } catch (Exception nonConstructorException) {
                            stackTrace.append(nonConstructorException);

                            String simpleClassName = objectClass.getSimpleName();

                            // Si pas de constructeur prenant un string on test autre chose
                            for (String funcName : fromStringStaticFuncList) {
                                try {
                                    objInstance = objectClass.getMethod(funcName, String.class).invoke(null, value);
                                    break;
                                } catch (Exception e) {
                                    stackTrace.append(e.getStackTrace());
                                } // }stackTrace+="- " + e.getStackTrace()+"\n";}

                                // On essaie d'ajouter le nom du type aprÃ¨s (e.g. parseLong)
                                try {
                                    objInstance = objectClass
                                            .getMethod(funcName + simpleClassName.substring(0, 1).toUpperCase()
                                                    + simpleClassName.substring(1), String.class)
                                            .invoke(null, value);
                                    break;
                                } catch (Exception e) {
                                    stackTrace.append(e.getStackTrace());
                                }
                            }
                            if (objInstance == null) {
                                for (String suffix : sffixImplementationOfAbstract) {
                                    Class<?> nonAbstractClass = null;
                                    if (value != null) {
                                        nonAbstractClass = value.getClass();
                                    } else {
                                        try {
                                            nonAbstractClass = getClassFromName(objClassName + suffix);
                                        } catch (Exception e1) {
                                            logger.debug(e1.getMessage(), e1);
                                            logger.error(
                                                    "Error for class : " + getClassFromName(objClassName + suffix)
                                                            + " trying to parseFromInt");
                                        }
                                    }
                                    if (nonAbstractClass != null) {
                                        try {
                                            objInstance = nonAbstractClass.getConstructor(String.class)
                                                    .newInstance(value);
                                            break;
                                        } catch (Exception e) {
                                            stackTrace.append(e.getStackTrace());
                                        } // stackTrace+=e.getStackTrace()+"\n";}

                                        // Si pas de constructeur prenant un string on test autre chose
                                        for (String funcName : fromStringStaticFuncList) {
                                            try {
                                                objInstance = nonAbstractClass.getMethod(funcName, String.class)
                                                        .invoke(null, value);
                                                break;
                                            } catch (Exception e) {
                                                stackTrace.append(e.getStackTrace());
                                            } // stackTrace+=e.fillInStackTrace()+"\n";}
                                        }
                                        if (objInstance != null) {
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        if (objInstance == null) {
                            logger.error(stackTrace.toString());
                        }
                    }
                }
            }
        } else { // ELSE : la classe n'existe pas
            logger.error("Class : " + objClassName + " doesn't exist");
        }

        return objInstance;
    }

    public Object modifyNewInstance(Object instance, Map<String, Object> epcObjects, Object value,
            String userName) {
        logger.error("Modifying instance " + instance);
        if (instance != null) {
            String objClassNameLower = instance.getClass().getName().toLowerCase();
            if (objClassNameLower.endsWith("dataobjectreference")
                    || objClassNameLower.endsWith("contactelementreference")) {
                // value must be an uuid
                try {
                    ObjectController.editObjectAttribute(instance, "uuid", value);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }

                if (epcObjects.containsKey(value)) {
                    Object resqmlObj = epcObjects.get(value);
                    logger.debug(">> DOR : " + resqmlObj + " --> " + EPCGenericManager.getObjectContentType(resqmlObj));

                    try {
                        ObjectController.editObjectAttribute(instance, "title",
                                ObjectController.getObjectAttributeValue(resqmlObj, "Citation.Title"));
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                    try {
                        ObjectController.editObjectAttribute(instance, "ContentType",
                                EPCGenericManager.getObjectContentType(resqmlObj));
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                    try { // FOR new 2.2
                        ObjectController.editObjectAttribute(instance, "QualifiedType",
                                EPCGenericManager.getObjectQualifiedType(resqmlObj));
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            } else if (EPCGenericManager.isRootClass(instance.getClass())) {
                logger.debug("IsRoot modifying");
                String uuid = UUID.randomUUID() + "";
                try {
                    ObjectController.editObjectAttribute(instance, ".Uuid", uuid);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }

                try {
                    ObjectController.editObjectAttribute(instance, ".SchemaVersion",
                            EPCGenericManager.getSchemaVersion(instance));
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }

                if (ObjectController.getObjectAttributeValue(instance, ".Citation") == null) {
                    try {
                        Class<?> citationClass = ObjectController.getAttributeClass(instance, ".Citation");
                        ObjectController.editObjectAttribute(instance, ".Citation",
                                citationClass.getConstructor().newInstance());
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
                try {
                    ObjectController.editObjectAttribute(instance, ".Citation.Creation", Utils.getCalendarForNow());
                    ObjectController.editObjectAttribute(instance, ".Citation.LastUpdate", Utils.getCalendarForNow());
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }

                try {
                    ObjectController.editObjectAttribute(instance, ".Citation.Title", DEFAULT_CITATION_TITLE);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }

                try {
                    ObjectController.editObjectAttribute(instance, ".Citation.Format", DEFAULT_CITATION_FORMAT);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }

                try {
                    ObjectController.editObjectAttribute(instance, ".Citation.Originator", userName);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            } else {
                logger.error("not root neither DOR " + instance);
            }

            if (objClassNameLower.endsWith("parametertemplate")) {
                try {
                    ObjectController.editObjectAttribute(instance, "MaxOccurs", "1");
                    ObjectController.editObjectAttribute(instance, "MinOccurs", "0");
                    ObjectController.editObjectAttribute(instance, "IsInput", "true");
                } catch (Exception ignore) {
                }
            }
        }
        return instance;
    }

    public Class<?> getClassFromSuperAndName(Class<?> superClass, Class<?> currentClass) {
        if (!superClass.isAssignableFrom(currentClass)) {
            for (Class<?> pkgClass : getClasses()) {
                if (superClass.isAssignableFrom(pkgClass) && pkgClass.getSimpleName().toLowerCase()
                        .compareTo(currentClass.getSimpleName().toLowerCase()) == 0) {
                    return pkgClass;
                }
            }
        }
        return currentClass;
    }

    public static Class<?> getClassFromName(String className) {
        String simpleClassName = className;
        if (simpleClassName.contains(".")) {
            simpleClassName = simpleClassName.substring(simpleClassName.lastIndexOf(".") + 1);
        }
        final String f_simpleClassName = simpleClassName;

        Class<?> foundClass = null;
        if (f_simpleClassName.toLowerCase().compareTo("long") == 0) {
            foundClass = Long.class;
        } else if (f_simpleClassName.toLowerCase().compareTo("double") == 0) {
            foundClass = Double.class;
        } else if (f_simpleClassName.toLowerCase().compareTo("int") == 0) {
            foundClass = Integer.class;
        } else if (f_simpleClassName.toLowerCase().compareTo("float") == 0) {
            foundClass = float.class;
        } else if (f_simpleClassName.toLowerCase().compareTo("boolean") == 0) {
            foundClass = Boolean.class;
        }

        if (foundClass == null) {
            try {
                foundClass = Class.forName(className);
            } catch (ClassNotFoundException e) {
                /* logger.error(e.getMessage(), e); */
            }
        }
        return foundClass;
    }

    public List<Class<?>> getClasses() {
        return PKG_LIST.stream().map(EPCPackage::getPkgClasses).flatMap(List::stream).collect(Collectors.toList());
    }

    public List<Class<?>> getRootClasses() {
        return PKG_LIST.stream().map(EPCPackage::getRootsElementsClasses).flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public HashMap<Class<?>, List<Object>> getResqmlEnumValues() {
        HashMap<Class<?>, List<Object>> classAndSubs = new HashMap<>();

        List<Class<?>> enumLists = getClasses().parallelStream()
                .filter(Class::isEnum).collect(Collectors.toList());

        for (Class<?> objClass : enumLists) {
            ArrayList<Object> valueList = new ArrayList<>();
            valueList.add("null");
            Method valueMethod = null;
            try {
                valueMethod = objClass.getMethod("value");
            } catch (NoSuchMethodException | SecurityException ignore) {
            }
            if (objClass.getEnumConstants() != null) {
                if (valueMethod == null) {
                    valueList.addAll(Arrays.asList(objClass.getEnumConstants()));
                } else {
                    // On essaie de mettre la 'vraie' valeur dans l'enum, pas la denomination de la
                    // class
                    for (Object val : objClass.getEnumConstants()) {
                        try {
                            valueList.add(valueMethod.invoke(val));
                        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                            valueList.add(val);
                        }
                    }
                }
            }
            classAndSubs.put(objClass, valueList);
        }

        List<Object> booleanValues = new ArrayList<>();
        booleanValues.add(null);
        booleanValues.add(true);
        booleanValues.add(false);
        classAndSubs.put(Boolean.class, booleanValues);

        return classAndSubs;
    }

    public List<Class<?>> getInheritorClasses(String rootClassName) {
        Class<?> rootClass = getClassFromName(rootClassName);
        return getClasses().stream().filter(
                objClass -> !Modifier.isAbstract(objClass.getModifiers()) && rootClass.isAssignableFrom(objClass))
                .collect(Collectors.toList());
    }

    public HashMap<String, List<String>> getAccessibleDORTypes() {
        return Utils.readJsonFileOrRessource(accessibleDORFilePath, HashMap.class);
    }

    public HashMap<String, String> getClassesComment() {
        HashMap<String, String> result = new HashMap<>();

        for (EPCPackage pkg : PKG_LIST) {
            try {
                logger.info("@getClassesComment " + pkg);
                result.putAll(Utils.readJsonFileOrRessource(xsdCommentsFolderPath + pkg.getPackageName() + ".json",
                        HashMap.class));
            } catch (Exception ignore) {
            }
        }

        return result;
    }

    public Map<String, String> getExtTypesAsJson(String extTypeJsonFolderPath) {
        if (!extTypeJsonFolderPath.endsWith("/") && !extTypeJsonFolderPath.endsWith("\\"))
            extTypeJsonFolderPath += "/";
        logger.debug("Starting reading Ext types");
        Map<String, Map<String, String>> extTypesAttributes_perPkg = new HashMap<>();
        for (EPCPackage pkg : PKG_LIST) {
            try {
                extTypesAttributes_perPkg.putAll(
                        Utils.readJsonFileOrRessource(extTypeJsonFolderPath + pkg.getPackageName() + ".json",
                                HashMap.class));
            } catch (Exception ignore) {
            }
        }

        HashMap<String, String> extTypesAttributesResult = new HashMap<>();

        ArrayList<String> keys = new ArrayList<>(extTypesAttributes_perPkg.keySet());

        // On cherche les ext types herites
        for (String key : keys) {
            // System.out.print("\rReading " + (i+1) + "/" + keys.size() + "\r");
            // System.out.flush();
            // On ajoute deja les ext de la classe courante
            for (Map.Entry<String, String> paramNameAndType : extTypesAttributes_perPkg.get(key).entrySet()) {
                extTypesAttributesResult.put((key + "." + paramNameAndType.getKey()).toLowerCase(),
                        paramNameAndType.getValue());
            }
            try {
                Class<?> classA = Class.forName(key);

                // On parcours toutes les classes connues
                for (EPCPackage pkg : PKG_LIST) {
                    for (Class<?> classB : pkg.getPkgClasses()) {
                        if (!classB.isEnum() && classB.getSuperclass() != null) {
                            if (classB != classA && ObjectController.inherits(classB, classA, true)) {
                                for (Map.Entry<String, String> paramNameAndType : extTypesAttributes_perPkg.get(key)
                                        .entrySet()) {
                                    extTypesAttributesResult.put(
                                            (classB.getName() + "." + paramNameAndType.getKey()).toLowerCase(),
                                            paramNameAndType.getValue());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("\nERR> #getExtTypesAsJson");
                logger.error(e.getMessage(), e);
            }
        }
        logger.debug("\nEnd reading Ext types");
        return extTypesAttributesResult;
    }

    public HashMap<Class<?>, List<Class<?>>> getResqmlTypesInstanciableBy() {
        HashMap<Class<?>, List<Class<?>>> classAndSubs = new HashMap<>();
        try {
            List<Class<?>> classList = new ArrayList<>(getClasses());

            for (Class<?> objClass : classList) {
                classAndSubs.put(objClass, ObjectController.getResqmlInheritorClasses(objClass.getName(), classList));
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return classAndSubs;
    }
}
