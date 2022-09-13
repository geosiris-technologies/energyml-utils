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
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.validation.Schema;
import java.io.*;
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

    private String xsdCommentsFolderPath;
    private final String accessibleDORFilePath;
    public final List<EPCPackage> PKG_LIST;

    public EPCPackageManager(String xsdCommentsFolderPath, String accessibleDORFilePath, String xsdMappingFilePath){
        this(xsdCommentsFolderPath, accessibleDORFilePath, initPkgList(xsdMappingFilePath));
    }

    public EPCPackageManager(String xsdCommentsFolderPath, String accessibleDORFilePath, List<EPCPackage> pkgList){
        this.xsdCommentsFolderPath = xsdCommentsFolderPath.replace("\\", "/");
        if(!this.xsdCommentsFolderPath.endsWith("/")){
            this.xsdCommentsFolderPath += "/";
        }
        this.accessibleDORFilePath = accessibleDORFilePath;

        this.PKG_LIST = pkgList;
    }

    public static List<EPCPackage> initPkgList(String xsdMappingFilePath) {
        ArrayList<EPCPackage> pkgs = new ArrayList<>();
        if(EPCPackage_Resqml.pkgClasses.size() > 0){
            pkgs.add(new EPCPackage_Resqml(xsdMappingFilePath));
        }
        if(EPCPackage_Resqml_Dev3.pkgClasses.size() > 0){
            pkgs.add(new EPCPackage_Resqml_Dev3(xsdMappingFilePath));
        }
        if(EPCPackage_Common.pkgClasses.size() > 0){
            pkgs.add(new EPCPackage_Common(xsdMappingFilePath));
        }
        if(EPCPackage_Witsml.pkgClasses.size() > 0){
            pkgs.add(new EPCPackage_Witsml(xsdMappingFilePath));
        }
        if(EPCPackage_Prodml.pkgClasses.size() > 0){
            pkgs.add(new EPCPackage_Prodml(xsdMappingFilePath));
        }
        return pkgs;
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

    public List<String> getPackagesPath() {
        List<String> pkgPathList = new ArrayList<>();
        for (EPCPackage pkg : PKG_LIST) {
            pkgPathList.addAll(pkg.getAllVersionsPackagePath());
        }
        return pkgPathList;
    }

    public JAXBElement<?> unmarshal(String xmlContent) {
        for (EPCPackage pkg : PKG_LIST) {
            JAXBElement<?> obj = pkg.parseXmlContent(xmlContent);
            if (obj != null) {
                return obj;
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


    public String getSchemaVersion(Object obj){
        EPCPackage pkg = getMatchingPackage(obj.getClass());
        return pkg.getSchemaVersionFromClassName(obj.getClass().getName());
    }

    public String marshal(Object obj) {
        try {
            OutputStream os = new ByteArrayOutputStream();
            marshal(obj, os);
            return os.toString();
        }catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    public void marshal(Object obj, OutputStream os) {
        EPCPackage pkg = getMatchingPackage(obj.getClass());
        if(pkg != null){
            pkg.marshal(obj, os);
        }
    }

    public boolean validate(Object obj) throws JAXBException {
        EPCPackage pkg = getMatchingPackage(obj.getClass());
        String pkg_Path = pkg.getPackagePath(pkg.getSchemaVersionFromClassName(obj.getClass().getName()));
        JAXBContext context = pkg.getContext(pkg_Path);

        Schema schema = pkg.getSchema(pkg_Path);

        Unmarshaller jaxbUnmarshaller = context.createUnmarshaller();
        jaxbUnmarshaller.setSchema(schema);

        String xmlObj = marshal(obj);
        ByteArrayInputStream bais = new ByteArrayInputStream(xmlObj.getBytes(StandardCharsets.UTF_8));

        jaxbUnmarshaller.unmarshal(bais);
        return true;
    }

    public static JAXBElement<?> wrap(Object resqmlObject, Object factory) throws NoSuchMethodException,
            SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        final Class<?> class22 = resqmlObject.getClass();
        final String typename = class22.getSimpleName();

        String[] listOfPotentialRemovablePrefix = {"Obj"};

        Class<?> classFactory22 = factory.getClass();

        try {
            Method create = classFactory22.getMethod("create" + typename, class22);
            return (JAXBElement<?>) create.invoke(factory, resqmlObject);
        } catch (Exception e) {
            for (String prefixToRemove : listOfPotentialRemovablePrefix) {
                if (typename.startsWith(prefixToRemove)) {
                    try {
                        Method create = classFactory22.getMethod("create" + typename.substring(prefixToRemove.length()),
                                class22);
                        return (JAXBElement<?>) create.invoke(factory, resqmlObject);
                    } catch (Exception e2) {
                        // logger.error(e.getMessage(), e);
                    }
                }
            }
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * @param objClassName class name of the object to create
     * @param epcObjects   list of all object in the current epc file
     * @param value        the value to set to the instance (if possible)
     * @param nullable     given to know if the value can be set to null or not
     * @return an instance of class given by the name objClassName
     */
    public Object createInstance(String objClassName, Map<String, Object> epcObjects, String schemaVersion,
                                 Object value, boolean nullable) {
        return createInstance(objClassName, epcObjects, schemaVersion, value, null, nullable);
    }

    public Object createInstance(String objClassName, Map<String, Object> epcObjects, String schemaVersion,
                                 Object value) {
        return createInstance(objClassName, epcObjects, schemaVersion, value, null, true);
    }

    /**
     * @param objClassName class name of the object to create
     * @param epcObjects   list of all object in the current epc file
     * @param value        the value to set to the instance (if possible)
     * @param nullable     given to know if the value can be set to null or not
     * @return an instance of class given by the name objClassName
     */
    public Object createInstance(String objClassName, Map<String, Object> epcObjects, String schemaVersion,
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
                        if (schemaVersion == null)
                            schemaVersion = packageMatch.getSchemaVersionFromClassName(objClassName);
                        // On a trouve un package qui match
                        logger.error(
                                "EPCManager : Version " + schemaVersion + " for package " + packageMatch.getName());
                        objInstance = packageMatch.createInstance(objClassName, schemaVersion);
                        logger.error("objInstance " + objInstance);
                        modifyNewInstance(objInstance, epcObjects, value, userName);
                    } else {
                        StringBuilder stackTrace = new StringBuilder();
                        // TODO : ajouter les noms de fonction possible pour construire un objet
                        // a partir d'un string
                        String[] fromStringStaticFuncList = {"parse", "fromString", "fromValue"};
                        // TODO : ajouter les suffixes de nom de classe a tester : expl : "Impl" pour
                        // "XMLGregorianCalendar"
                        String[] sffixImplementationOfAbstract = {"Impl"};

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
                    logger.debug(">> DOR : " + resqmlObj + " --> " + getObjectContentType(resqmlObj));

                    try {
                        ObjectController.editObjectAttribute(instance, "title", ObjectController.getObjectAttributeValue(resqmlObj, "Citation.Title"));
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                    try {
                        ObjectController.editObjectAttribute(instance, "ContentType", getObjectContentType(resqmlObj));
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                    try { // FOR new 2.2
                        ObjectController.editObjectAttribute(instance, "QualifiedType", getObjectContentType(resqmlObj));
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            } else if (EPCPackage.isRootClass(instance.getClass())) {
                logger.debug("IsRoot modifying");
                String uuid = UUID.randomUUID() + "";
                try {
                    ObjectController.editObjectAttribute(instance, ".Uuid", uuid);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }

                try {
                    ObjectController.editObjectAttribute(instance, ".SchemaVersion", getSchemaVersion(instance));
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }

                if(ObjectController.getObjectAttributeValue(instance, ".Citation") == null) {
                    try {
                        Class<?> citationClass = ObjectController.getAttributeClass(instance, ".Citation");
                        ObjectController.editObjectAttribute(instance, ".Citation",citationClass.getConstructor().newInstance());
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
        List<Class<?>> classList = new ArrayList<>();
        for (EPCPackage pkg : PKG_LIST) {
            for (Collection<Class<?>> classCol : pkg.getAllClassesForVersion().values()) {
                classList.addAll(classCol);
            }
        }
        return classList;
    }

    public List<Class<?>> getRootClasses() {
        List<Class<?>> classList = new ArrayList<>();
        for (final EPCPackage pkg : PKG_LIST) {
            for (Collection<Class<?>> classCol : pkg.getAllClassesForVersion().values()) {
                classList.addAll(classCol.stream().filter(EPCPackage::isRootClass).collect(Collectors.toList()));
            }
        }
        return classList;
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
            } catch (NoSuchMethodException | SecurityException ignore) {}
            if (objClass.getEnumConstants() != null) {
                if (valueMethod == null) {
                    valueList.addAll(Arrays.asList(objClass.getEnumConstants()));
                } else {
                    // On essaie de mettre la 'vraie' valeur dans l'enum, pas la denomination de la class
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
                objClass -> !Modifier.isAbstract(objClass.getModifiers()) && rootClass.isAssignableFrom(objClass)).collect(Collectors.toList());
    }

    public  String getEnerygmlNamespace(Object resqmlObj) {

        for (EPCPackage epc_pkg : PKG_LIST) {
            if (epc_pkg.isClassNameMatchesPackage(resqmlObj.getClass().getName())) {
                return epc_pkg.getNamespace();
            }
        }
        return "###error_unkown_object_" + resqmlObj.getClass().getTypeName() + "###";
    }

    public  String getEnerygmlNamespaceFromClassName(String energymlObjClass) {

        for (EPCPackage epc_pkg : PKG_LIST) {
            if (epc_pkg.isClassNameMatchesPackage(energymlObjClass)) {
                return epc_pkg.getNamespace();
            }
        }
        return "###error_unkown_object_" + energymlObjClass + "###";
    }

    public String getEnerygmlNamespaceETP(Object resqmlObj) {
        return getEnerygmlNamespaceETPFromClassName(resqmlObj.getClass().getName());
    }

    public String getEnerygmlNamespaceETPFromClassName(String engymlObjClassName) {
        String pkg = engymlObjClassName.substring(0, engymlObjClassName.lastIndexOf("."));
        Pattern pat = Pattern.compile(Utils.PATTERN_PKG_VERSION + "$");
        Matcher match = pat.matcher(pkg);
        String schemaVersion = "";
        if(match.find()){
            schemaVersion = match.group("versionNum").replace("_", ".");
        }
        Matcher matchEtpVersion = Utils.PATTERN_MATCH_ETP_VERSION.matcher(schemaVersion);
        if(matchEtpVersion.find()){
            schemaVersion = matchEtpVersion.group("digit2Version");
        }
        return getEnerygmlNamespaceFromClassName(engymlObjClassName) + schemaVersion.replace(".", "_");
    }

    public String getObjectContentType(Object resqmlObj) {
        for (EPCPackage epc_pkg : PKG_LIST) {
            if (epc_pkg.isClassNameMatchesPackage(resqmlObj.getClass().getName())) {
                return epc_pkg.getObjectContentType(resqmlObj);
            }
        }
        return "ERROR CONTENT TYPE";
    }
    public String getQualifiedType(Object resqmlObj) {
        for (EPCPackage epc_pkg : PKG_LIST) {
            if (epc_pkg.isClassNameMatchesPackage(resqmlObj.getClass().getName())) {
                return epc_pkg.getObjectQualifiedType(resqmlObj);
            }
        }
        return "ERROR QUALIFIED TYPE";
    }

    public HashMap<String, List<String>> getAccessibleDORTypes() {
        return Utils.readJsonFileOrRessource(accessibleDORFilePath, HashMap.class);
    }

    public HashMap<String, String> getClassesComment() {
        HashMap<String, String> result = new HashMap<>();

        for(String pkg : getPackagesPath()) {
            try {
                logger.info("@getClassesComment " + pkg);
                Pattern patPkg = Pattern.compile("(resqml|common|prodml|witsml)" + Utils.PATTERN_PKG_VERSION +"$");
                Matcher match = patPkg.matcher(pkg);
                if (match.find()) {
                    String matched = match.group(0);
                    result.putAll(Utils.readJsonFileOrRessource(xsdCommentsFolderPath + matched + ".json", HashMap.class));
                }
            }catch (Exception ignore) {}
        }

        return result;
    }
    public Map<String, String> getExtTypesAsJson(String extTypeJsonFolderPath) {
        logger.debug("Starting reading Ext types");
        Map<String, Map<String, String>> extTypesAttributes_perPkg = new HashMap<>();
        for (EPCPackage pkg : PKG_LIST) {
            for (String pkgPath : pkg.getAllVersionsPackagePath()) {
                if (pkgPath.contains(".")) {
                    pkgPath = pkgPath.substring(pkgPath.lastIndexOf(".") + 1);
                }
                try {
                    extTypesAttributes_perPkg.putAll(Utils.readJsonFileOrRessource(extTypeJsonFolderPath + pkgPath + ".json", HashMap.class));
                }catch (Exception ignore){}
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
                extTypesAttributesResult.put((key + "." + paramNameAndType.getKey()).toLowerCase(), paramNameAndType.getValue());
            }
            try {
                Class<?> classA = Class.forName(key);

                // On parcours toutes les classes connues
                for (EPCPackage pkg : PKG_LIST) {
                    for (List<Class<?>> pkgClasses : pkg.getAllClassesForVersion().values()) {
                        for (Class<?> classB : pkgClasses) {
                            if (!classB.isEnum() && classB.getSuperclass() != null) {
                                if (classB != classA && ObjectController.inherits(classB, classA, true)) {
                                    for (Map.Entry<String, String> paramNameAndType : extTypesAttributes_perPkg.get(key).entrySet()) {
                                        extTypesAttributesResult.put((classB.getName() + "." + paramNameAndType.getKey()).toLowerCase(), paramNameAndType.getValue());
                                    }
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
        }catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        return classAndSubs;
    }


    public static void main(String[] argv){
        EPCPackageManager pkgManager = new EPCPackageManager(EPCPackageManager.DEFAULT_XSD_COMMENTS_FOLDER_PATH.replace("config/", "config/data/"),
                EPCPackageManager.DEFAULT_ACCESSIBLE_DOR_FILE_PATH.replace("config/", "config/data/"),
                "");
        System.out.println(pkgManager.getMatchingPackage("energyml.resqml2_0_1.ObjHorizonInterpretation"));



    }
}
