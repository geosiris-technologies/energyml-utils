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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.datatype.XMLGregorianCalendar;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ObjectController {
    public static Logger logger = LogManager.getLogger(ObjectController.class);

    public static List<String> getAllAttributeNameVariations(String attName) {
        while (attName.startsWith(".")) { // security
            attName = attName.substring(1);
        }
        List<String> variation = new ArrayList<>();
        variation.add(attName);

        // > First letter uppercase
        variation.add(attName.substring(0, 1).toUpperCase() + attName.substring(1));

        // > First letter lowercase
        variation.add(attName.substring(0, 1).toLowerCase() + attName.substring(1));

        // > Uppercase
        variation.add(attName.toUpperCase());

        // > Uppercase
        variation.add(attName.toLowerCase());

        // Cas moins courants, mais pour les attributs portant des noms reserves ('throw', 'class' etc.)
        List<String> uderscored = new ArrayList<>();
        for (String v : variation) {
            uderscored.add("_" + v);
        }
        variation.addAll(uderscored);
        // Cas reverse ou on doit enlever les '_'
        if(attName.startsWith("_")){
            variation.addAll(getAllAttributeNameVariations(attName.substring(1)));
        }

        return new LinkedHashSet<>(variation).stream().collect(Collectors.toList()); // on enleve les doublons
    }

    public static Object stringToEnum(String value, Class<?> enumClass){
        List<String> methodsNames = Arrays.asList(new String[]{"fromString", "valueOf", "fromValue"});
        List<Method> potentialMethod = new ArrayList<>();
        for(Method m : enumClass.getMethods()){
            for(String name : methodsNames) {
                if (m.getName().compareToIgnoreCase(name) == 0){
                    potentialMethod.add(m);
                }
            }
        }
        for(Method m : potentialMethod){
            try{
                return m.invoke(null, value);
            }catch (Exception ignore){}
        }
        return null;
    }

    public static Object createInstance(String objClassName, Object value) {
        return createInstance(objClassName, value, true);
    }

    /**
     * Create an instance of a class.
     *
     * @param objClassName Class name
     * @param value        default value use if the class of the object to create is an enum or a {@link String}
     * @param nullable     if true, the return value may be null (for an enum type), but if false, if the @value is null or
     *                     not recognized, the return value will be the first declared value of the enum type
     * @return an instance of the object
     */
    public static Object createInstance(String objClassName, Object value, boolean nullable) {
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


            if (objectClass.isEnum()) { // Si c'est un enum
                // On commence par tester si une fonction 'fromValue' existe
                try {
                    if (value != null && (value + "").toLowerCase().compareTo("null") != 0) {
                        Method fromValueMethod = objectClass.getMethod("fromValue", String.class);
                        objInstance = fromValueMethod.invoke(null, value);
//                    } else if (nullable) {
//                        objInstance = null;
                    } else {
                        objInstance = objectClass.getEnumConstants()[0];
                    }
                } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    for (Object enumVal : objectClass.getEnumConstants()) {
                        if ((enumVal + "").compareTo(value + "") == 0) {
                            objInstance = enumVal;
                            break;
                        }
                    }
                    if (objInstance == null) {
                        objInstance = objectClass.getEnumConstants()[0];
                    }
                    logger.error(e.getMessage());
                    logger.debug(e.getMessage(), e);
                }

            } else {    // Not an enum type
                if (objClassName.compareTo(String.class.getName()) == 0) {
                    if (value != null) {
                        objInstance = value + "";
                    } else {
                        objInstance = "";
                    }
                } else {
                    try {
                        return Class.forName(objClassName).getDeclaredConstructor().newInstance();
                    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException | InvocationTargetException e) {
                        logger.error(e.getMessage());
                        logger.debug(e.getMessage(), e);
                    }
                }
            }
        } else { // ELSE : la classe n'existe pas
            logger.error("Class : " + objClassName + " doesn't exist");
        }

        return objInstance;
    }

    /**
     * @param obj
     * @param attribPath
     * @param objClass
     * @throws Exception
     */
    public static void createObjectAttribute(Object obj, String attribPath, Class<?> objClass) throws NoSuchAccessibleParameterFound, NoSuchEditableParameterFound, InvocationTargetException, IllegalAccessException {
        logger.info("Trying to create attibute '" + attribPath + "' in object '" + (obj != null ? obj.getClass() : obj) + "' as type '" + objClass + "'");

        while (attribPath.startsWith(".")) {
            attribPath = attribPath.substring(1);
        }
        String attribParentPath = attribPath;
        String attribName = attribPath.contains(".") ? attribPath.substring(attribPath.lastIndexOf(".") + 1) : attribPath;

        Object parentObject = obj;
        if (attribPath.contains(".")) {
            attribParentPath = attribPath.substring(0, attribPath.lastIndexOf("."));
            // Si on a d'autres points on va chercher le parent direct de l'attribut a creer
            parentObject = getObjectAttributeValue(obj, attribParentPath);
        }

        Object newObject = null;
        if (List.class.isAssignableFrom(parentObject.getClass())) {

            if (objClass != null)
                newObject = createInstance(objClass.getName(), null);
            else {
                String listName = attribParentPath.contains(".") ? attribParentPath.substring(attribParentPath.lastIndexOf(".")) : attribParentPath;
                String listParentPath = attribParentPath;
                if (attribParentPath.contains(".")) {
                    listParentPath = attribParentPath.substring(0, attribParentPath.lastIndexOf("."));
                }
                Object listParent = getObjectAttributeValue(obj, listParentPath);
                List<Class<?>> ptypes = getClassTemplatedTypeofSubParameter(listParent.getClass(), listName);
                newObject = createInstance(ptypes.get(0).getName(), null);
            }
            ((List) parentObject).add(newObject);
        } else {
            Method mget = getAttributeAccessMethod(parentObject, attribName);

            if (List.class.isAssignableFrom(mget.getReturnType())) {
                // Si on est sur un attribut qui est une list, alors l'objet cree doit être un element de la liste
                createObjectAttribute(parentObject, attribName + ".0", objClass);
            } else {
                if (objClass != null) {
                    newObject = createInstance(objClass.getName(), null);
                } else {
                    newObject = createInstance(mget.getReturnType().getName(), null);
                }
                logger.info("newObject " + newObject + " " + mget.getReturnType());
                getAttributeEditMethod(parentObject, attribName).invoke(parentObject, newObject);
            }
        }
    }

    /**
     * Displace an element in a list at "dot-path" @eltPath, to the new index @newIdx.
     * Example : eltPath = ".Geometry.TrianglePatch.1" and newIdx = 3
     *
     * @param rootObject the root object on which we search a list at path @eltPath
     * @param eltPath    the "dot-path" of the list in the object @rootObject
     * @param newIdx     the new index of the element
     * @return true if the element has been displaced, else false
     */
    public static boolean displaceEltInList(Object rootObject, String eltPath, int newIdx) {
        Object o = getObjectAttributeValue(rootObject, eltPath.substring(0, eltPath.lastIndexOf(".")));
//        logger.info(o + " for path : " + eltPath);
        try {
            if (o != null && List.class.isAssignableFrom(o.getClass())) {
                int oldIdx = Integer.parseInt(eltPath.substring(eltPath.lastIndexOf(".") + 1));
                Object oToDisplace = ((List<?>) o).get(oldIdx);
                ((List<?>) o).remove(oldIdx);
                ((List) o).add(newIdx, oToDisplace);
                return true;
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.debug(e.getMessage(), e);
        }
        return false;
    }


    /**
     * Gives all fields of a class (including ones declared in super classes)
     *
     * @param type the class where to search the fields
     * @return a list of all fields of a class
     */
    public static List<Field> getAllFields(Class<?> type) {
        return getAllFields(new ArrayList<>(), type);
    }

    /**
     * @param fields --
     * @param type   --
     * @return --
     */
    private static List<Field> getAllFields(List<Field> fields, Class<?> type) {
        fields.addAll(Arrays.asList(type.getDeclaredFields()));
        if (type.getSuperclass() != null) {
            getAllFields(fields, type.getSuperclass());
        }
        return fields;
    }

    /**
     *
     * @param objClass starting class
     * @param fieldPath a dotted path. Write [] for a list or a map index.
     * @param caseSensitive test the case of the fields name
     * @return the field at path @{fieldPath}
     */
    public static Field getField(Class<?> objClass, String fieldPath, boolean caseSensitive){
        String fieldName = fieldPath;
        if(fieldName.contains(".")){
            fieldName = fieldPath.substring(0, fieldName.indexOf("."));
        }
        for(Field f : getAllFields(objClass)){
            if((caseSensitive && f.getName().compareTo(fieldName) == 0)
                    || f.getName().compareToIgnoreCase(fieldName) == 0){
                if(fieldName.length() == fieldPath.length()){
                    return f;
                }else{
                    // On passe au suivant.
                    String nextField = fieldPath.substring(fieldName.length() + 1);
                    if(nextField.startsWith("[].")){

                    }else{
                        return getField(f.getType(), nextField, caseSensitive);
                    }
                }
            }
        }
        return null;
    }

    public static Map<String, Field> getAllAccessibleFields_Deep(Class<?> objClass){
        return getAllAccessibleFields_Deep(objClass, null, null);
    }

    private static Map<String, Field> getAllAccessibleFields_Deep(Class<?> objClass, Class<?> parentClass, Field thisField){
        Map<String, Field> res = new HashMap<>();
// TODO:
        for(Method m : objClass.getMethods()){
            if(m.getName().compareTo("get") == 0){
                if(m.getParameters().length == 1){
                    try {
                        // try to add field for list or map
//                        m.getReturnType().isAnonymousClass()
                    }catch (Exception e){
                        logger.error(e.getMessage(), e);
                    }
                }
            }else if(m.getName().startsWith("get")){

            }
        }

        return res;
    }

    /**
     * Get the @{@link Method} to edit an attribute called @paramName.
     * /!\ The attribute must be directly accessible in the class of the object. (It can not be an attribute of an attribute with a dot-syntax)
     * /!\ The getter method must follow java commonly used syntax : for an attribute called "myPersonal_attribute", the getter must be called :
     * - "isMyPersonal_attribute" for a boolean
     * - "getMyPersonal_attribute" for other types
     * Note : the method name can also contain the attribute name in uppercase : "isMYPERSONAL_ATTRIBUTE" or "getMYPERSONAL_ATTRIBUTE"
     *
     * @param resqmlObj
     * @param paramName
     * @return
     * @throws NoSuchAccessibleParameterFound
     */
    public static Method getAttributeAccessMethod(Object resqmlObj, String paramName) throws NoSuchAccessibleParameterFound {
        while (paramName.startsWith(".")) {
            paramName = paramName.substring(1);
        }

        if (paramName.contains(".")) {
            return getAttributeAccessMethod(getObjectAttributeValue(resqmlObj, paramName.substring(0, paramName.lastIndexOf("."))), paramName.substring(paramName.lastIndexOf(".") + 1));
        }
        Class<?> resqmlObjClass = resqmlObj.getClass();

        for (String paramNameVariation : getAllAttributeNameVariations(paramName)) {
            try {
                return resqmlObjClass.getMethod("get" + paramNameVariation);
            } catch (NoSuchMethodException | SecurityException ignored) {
            }
            try {
                return resqmlObjClass.getMethod("is" + paramNameVariation);
            } catch (NoSuchMethodException | SecurityException ignored) {
            }
            try { // try without "get" prefix
                return resqmlObjClass.getMethod("" + paramNameVariation);
            } catch (NoSuchMethodException | SecurityException ignored) {
            }
        }
        throw new NoSuchAccessibleParameterFound(paramName, resqmlObj);
    }

    /**
     * @param resqmlObj the root object in which to search the attribute
     * @param paramName the name of the attribute
     * @return the class of the attribute
     * @throws NoSuchAccessibleParameterFound raised if the attribute is not found
     */
    public static Class<?> getAttributeClass(Object resqmlObj, String paramName) throws NoSuchAccessibleParameterFound {
        Method accessMethod = getAttributeAccessMethod(resqmlObj, paramName);
        return accessMethod.getReturnType();
    }

    public static void editObjectAttribute(Object rootObject, String attribPath, Object value) throws NoSuchAccessibleParameterFound, NoSuchEditableParameterFound, InvocationTargetException, IllegalAccessException {
        while (attribPath.startsWith(".")) {
            attribPath = attribPath.substring(1);
        }
        int dotPos = attribPath.indexOf(".");
        if (dotPos > 0) {
            // On est pas au bout du chemin
            editObjectAttribute(getObjectAttributeValue(rootObject, attribPath.substring(0, dotPos)), attribPath.substring(dotPos), value);
        } else {
            // On est sur le denier attribut
            getAttributeEditMethod(rootObject, attribPath).invoke(rootObject, value);
        }
    }

    public static Method getAttributeEditMethod(Object resqmlObj, String paramName) throws NoSuchEditableParameterFound, NoSuchAccessibleParameterFound {
        while (paramName.startsWith(".")) {
            paramName = paramName.substring(1);
        }

        StringBuilder stackTrace = new StringBuilder();
        Class<?> resqmlObjClass = resqmlObj.getClass();
        Class<?> paramClass = getAttributeClass(resqmlObj, paramName);

        for (String paramNameVariation : getAllAttributeNameVariations(paramName)) {
            try {
                return resqmlObjClass.getMethod("set" + paramNameVariation, paramClass);
            } catch (NoSuchMethodException | SecurityException e) {
                stackTrace.append(Arrays.toString(e.getStackTrace()));
            }
            try {
                return resqmlObjClass.getMethod("is" + paramNameVariation, paramClass);
            } catch (NoSuchMethodException | SecurityException e) {
                stackTrace.append(Arrays.toString(e.getStackTrace()));
            }
            // Test on primitiveClasses
            try {
                return resqmlObjClass.getMethod("set" + paramNameVariation, getPrimitivClass(paramClass));
            } catch (NoSuchMethodException | SecurityException e) {
                stackTrace.append(Arrays.toString(e.getStackTrace()));
            }
            try {
                return resqmlObjClass.getMethod("is" + paramNameVariation, getPrimitivClass(paramClass));
            } catch (NoSuchMethodException | SecurityException e) {
                stackTrace.append(Arrays.toString(e.getStackTrace()));
            }
        }

        logger.error(stackTrace);
        throw new NoSuchEditableParameterFound(paramName, resqmlObj);
    }


    public static List<Pair<Class<?>, String>> getClassAttributes(Class<?> type) {
        ArrayList<Pair<Class<?>, String>> attributes = new ArrayList<>();


        Method[] methods = type.getMethods();

        List<String> methGet = Arrays.stream(methods).filter(m -> {
                    return m.getName().startsWith("get");
                })
                .map(m -> m.getName().substring(3)).collect(Collectors.toList());

        List<String> methIs = Arrays.stream(methods).filter(m -> {
                    return m.getName().startsWith("is");
                })
                .map(m -> m.getName().substring(2)).collect(Collectors.toList());

        List<String> props = Arrays.stream(methods).filter(m -> {
                    return m.getName().startsWith("set");
                })
                .filter(m -> {
                    String n = m.getName().substring(3);
                    return methGet.contains(n) || methIs.contains(n);
                })
                .map(m -> m.getName().substring(3)).collect(Collectors.toList());

        List<String> listsProps = Arrays.stream(methods).filter(m -> {
                    return m.getName().startsWith("get");
                })
                .filter(m -> {
                    return List.class.isAssignableFrom(m.getReturnType());
                })
                .map(m -> m.getName().substring(3)).collect(Collectors.toList());

        java.util.Collections.sort(props);
        java.util.Collections.sort(listsProps);

        for (String property : props) {
            try {
                if (methGet.contains(property)) {
                    //TODO : verifier si mandatory, ajouter l'attribut!
                    attributes.add(new Pair<Class<?>, String>(type.getMethod("get" + property).getReturnType(), property));
                } else {
                    attributes.add(new Pair<Class<?>, String>(type.getMethod("is" + property).getReturnType(), property));
                }
            } catch (NoSuchMethodException | SecurityException ignored) {
            }
        }

        for (String property : listsProps) {
            try {
                if (methGet.contains(property)) {
                    attributes.add(new Pair<Class<?>, String>(type.getMethod("get" + property).getReturnType(), property));
                }
            } catch (NoSuchMethodException | SecurityException ignored) {
            }
        }

        return attributes;
    }

    public static Field getClassField(String name, Class<?> type) {
        for (Field f : getAllFields(type)) {
            if (f.getName().compareTo(name) == 0) {
                return f;
            }
        }
        return null;
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
            } catch (ClassNotFoundException e) {/*logger.error(e.getMessage(), e);*/ }
        }
        return foundClass;
    }

    /**
     * Try to find all objects of type "className", in the object "obj"
     * @param obj The object to search in
     * @param className The class of sub object to search
     * @return All sub object with type "className" found in "obj"
     */
    public static List<Object> findSubObjects(Object obj, String className, boolean searchClassNameInSuperClass){
        List<Object> res = new ArrayList<>();

        if(obj!=null){
            if(obj.getClass().getSimpleName().compareToIgnoreCase(className) == 0
                    && (!searchClassNameInSuperClass || hasSuperClassSuffix(obj.getClass(), className))){
                res.add(obj);
            }else{
                if(obj instanceof Collection){
                    for(Object c_o: ((Collection<?>)obj)){
                        res.addAll(findSubObjects(c_o, className, searchClassNameInSuperClass));
                    }
                }else if(obj instanceof Map){
                    for(Object c_o: ((Map<?, ?>)obj).values()){
                        res.addAll(findSubObjects(c_o, className, searchClassNameInSuperClass));
                    }
                }else{
                    for(Pair<Class<?>, String> attribute : ObjectController.getClassAttributes(obj.getClass())){
                        res.addAll(findSubObjects(ObjectController.getObjectAttributeValue(obj, attribute.r()), className, searchClassNameInSuperClass));
                    }
                }
            }
        }
        return res;
    }

    public static List<Object> findAllAttributesFromName(Object obj, String attributeName, boolean caseSensitive, boolean searchInsideResults){
        List<Object> res = new ArrayList<>();

        if(obj!=null){
            if(obj instanceof Collection){
                for(Object c_o: ((Collection<?>)obj)){
                    res.addAll(findAllAttributesFromName(c_o, attributeName, caseSensitive, searchInsideResults));
                }
            }else if(obj instanceof Map){
                for(Object c_o: ((Map<?, ?>)obj).values()){
                    res.addAll(findAllAttributesFromName(c_o, attributeName, caseSensitive, searchInsideResults));
                }
            }else{
                for(Pair<Class<?>, String> attribute : ObjectController.getClassAttributes(obj.getClass())){
                    boolean isMatch = attribute.r().compareToIgnoreCase(attributeName) == 0 && (!caseSensitive || attribute.r().substring(1).compareTo(attributeName.substring(1)) == 0);
                    if(isMatch){
                        res.add(ObjectController.getObjectAttributeValue(obj, attribute.r()));
                    }
                    if(!isMatch || searchInsideResults){
                        res.addAll(findAllAttributesFromName(ObjectController.getObjectAttributeValue(obj, attribute.r()), attributeName, caseSensitive, searchInsideResults));
                    }
                }

            }
        }
        return res;
    }

    public static Map<String, Object> findSubObjectsAndPath(Object obj, String className){
        return findSubObjectsAndPath(obj, className, "");
    }

    private static Map<String, Object> findSubObjectsAndPath(Object obj, String className, String currentPath){
        Map<String, Object> res = new HashMap<>();

        if(obj!=null){
            if(obj.getClass().getSimpleName().compareToIgnoreCase(className) == 0){
                res.put(currentPath, obj);
            }else{
                if(obj instanceof Collection){
                    Object[] array = ((Collection<?>)obj).toArray();
                    for(int i = 0; i<array.length; i++){
                        res.putAll(findSubObjectsAndPath(array[i], className, currentPath + "." + i));
                    }
                }else if(obj instanceof Map){
                    for(Map.Entry<?,?> c_o: ((Map<?, ?>)obj).entrySet()){
                        res.putAll(findSubObjectsAndPath(c_o.getValue(), className, currentPath + "." + c_o.getKey()));
                    }
                }else{
                    for(Pair<Class<?>, String> attribute : ObjectController.getClassAttributes(obj.getClass())){
                        res.putAll(findSubObjectsAndPath(ObjectController.getObjectAttributeValue(obj, attribute.r()), className, currentPath + "." + attribute.r()));
                    }
                }
            }
        }
        return res;
    }


    private static Class<?> getClassMatchFromPrefix(String prefix, List<Class<?>> possibleClasses) {
        for (Class<?> cl : possibleClasses) {
            if (cl.getSimpleName().toLowerCase().startsWith(prefix.toLowerCase())) {
                return cl;
            }
        }
        // If not found, try to remove '_' character
        for (Class<?> cl : possibleClasses) {
            if (cl.getSimpleName().replace("_", "").toLowerCase().startsWith(prefix.toLowerCase())) {
                return cl;
            }
        }
        // If not found, try to check only with prefix start
        int minPrefixSize = 4;
        for (Class<?> cl : possibleClasses) {
            if (cl.getSimpleName().toLowerCase().startsWith(prefix.substring(0, Math.min(minPrefixSize, prefix.length())).toLowerCase())) {
                return cl;
            }
        }

        return null;
    }

    public static String getClassName(String classInitialName) {
        if (classInitialName.toLowerCase().compareTo("long") == 0) {
            return Long.class.getName();
        } else if (classInitialName.toLowerCase().compareTo("double") == 0) {
            return Double.class.getName();
        } else if (classInitialName.toLowerCase().compareTo("int") == 0) {
            return Integer.class.getName();
        } else if (classInitialName.toLowerCase().compareTo("float") == 0) {
            return float.class.getName();
        } else if (classInitialName.toLowerCase().compareTo("boolean") == 0) {
            return Boolean.class.getName();
        }
        return classInitialName;
    }


    public static Class<?> getPrimitivClass(Class<?> initialClass) {
        if (initialClass.getSimpleName().toLowerCase().compareTo("long") == 0) {
            return long.class;
        } else if (initialClass.getSimpleName().toLowerCase().compareTo("double") == 0) {
            return double.class;
        } else if (initialClass.getSimpleName().toLowerCase().compareTo("int") == 0
                || initialClass.getSimpleName().toLowerCase().compareTo("integer") == 0) {
            return int.class;
        } else if (initialClass.getSimpleName().toLowerCase().compareTo("float") == 0) {
            return float.class;
        } else if (initialClass.getSimpleName().toLowerCase().compareTo("boolean") == 0) {
            return boolean.class;
        }
        return null;
    }

    public static List<Class<?>> getClassTemplatedTypeofSubParameter(Class<?> dataClass, String paramName) {
        List<Class<?>> templateClassList = new ArrayList<>();
        ParameterizedType tempType = getClassTemplates(dataClass, paramName);
        if (tempType != null) {
            for (Type contentClass : tempType.getActualTypeArguments()) {
                templateClassList.add((Class<?>) contentClass);
            }
        }
        return templateClassList;
    }

    public static ParameterizedType getClassTemplates(Class<?> parentElementClass, String paramName) {
        Field listField = null;

        for(String paramNameVar : getAllAttributeNameVariations(paramName)) {
            try {
                listField = getClassField(paramNameVar, parentElementClass);
                if(listField != null)
                    break;
            } catch (Exception ignore){}
        }
        if (listField != null) {
            try {
                return (ParameterizedType) listField.getGenericType();
            } catch (Exception ignore){}
        }
        return null;
    }

    public static Object getObjectAttributeValue(Object obj, String pathAttribute) {
        String attribute = pathAttribute;
        while (attribute.startsWith(".")) {
            attribute = attribute.substring(1);
        }
        pathAttribute = attribute;
        if (attribute.contains("."))
            attribute = attribute.substring(0, attribute.indexOf("."));

        Object result = null;
        // Cas des indices de liste
        if (attribute.replaceAll("[\\d]+", "").length() == 0) {
            try {
                result = obj.getClass().getMethod("get", int.class).invoke(obj, Integer.parseInt(attribute));
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                     | NoSuchMethodException | SecurityException e) {
                logger.debug(e.getMessage(), e);
                for (Method m : obj.getClass().getMethods()) {
                    logger.debug(m);
                }
            }
        } else {
            // Si pas un entier on a une exception donc ce n'est pas une liste
            try {
                result = getAttributeAccessMethod(obj, attribute).invoke(obj);
            } catch (Exception e) {
                logger.debug(e.getMessage(), e);
                logger.debug("obj was " + obj + " path was '" + pathAttribute + "' and attribute " + " '" + attribute + "'");
                logger.debug("Now we try to get the attribute as a map key");
                try {
                    result = obj.getClass().getMethod("get", Object.class).invoke(obj, attribute);
                } catch (Exception emap) {
                    logger.debug(emap.getMessage(), emap);
                }
            }
        }

        if (result != null && pathAttribute.contains(".")) {
            return getObjectAttributeValue(result, pathAttribute.substring(attribute.length() + 1));
        } else {
            return result;
        }
    }

    public static List<Method> getAttributeAccessMethodRgx(Object obj, String attributeRgx){
        List<Method> res = new ArrayList<>();
        Pattern pat = Pattern.compile("(get|is)?(" + attributeRgx +")", Pattern.CASE_INSENSITIVE);
        for(Method m : obj.getClass().getMethods()){
            if (m.getParameterCount() == 0) {
                Matcher matcher = pat.matcher(m.getName());
                if (matcher.matches()) {
                    res.add(m);
                }
            }
        }
        res = res.stream().sorted(Comparator.comparing(Method::getName)).collect(Collectors.toList());
        return res;
    }

    public static List<Object> getObjectAttributeValueRgx(Object obj, String pathAttribute) {
        if(obj == null) return new ArrayList<>();
        String attribute = pathAttribute;
        while (attribute.startsWith(".")) {
            attribute = attribute.substring(1);
        }
        pathAttribute = attribute;
        if (attribute.contains("."))
            attribute = attribute.substring(0, attribute.indexOf("."));

        List result = new ArrayList();
        // Cas des indices de liste
        if (attribute.replaceAll("[\\d]+", "").length() == 0) {
            try {
                result.add(obj.getClass().getMethod("get", int.class).invoke(obj, Integer.parseInt(attribute)));
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                     | NoSuchMethodException | SecurityException e) {
                logger.debug(e.getMessage(), e);
                for (Method m : obj.getClass().getMethods()) {
                    logger.debug(m);
                }
            }
        }else if (attribute.equals("*") && obj instanceof Collection) {
            result.addAll((Collection)obj);
        }else if(obj instanceof Map) {
            Map<String, ?> map_obj = (Map) obj;
            if(attribute.matches("[\\w]+") && map_obj.containsKey(attribute)){ // Optimization if not a regex but a simple word
                result.add(map_obj.get(attribute));
            }else{
                Pattern patAttribute = Pattern.compile(attribute);
                for(String k : map_obj.keySet()){
                    Matcher matcher = patAttribute.matcher(k);
                    if(matcher.find()) {
                        result.add(map_obj.get(k));
                    }
                }
            }
        }else {
            // Si pas un entier on a une exception donc ce n'est pas une liste
            for(Method m : getAttributeAccessMethodRgx(obj, attribute)){
                try {
                    result.add(m.invoke(obj));
                } catch (Exception e) {
                    logger.debug(e.getMessage(), e);
                    logger.debug("obj was " + obj + " path was '" + pathAttribute + "' and attribute " + " '" + attribute + "'");
                }
            }
        }

        if (pathAttribute.contains(".")) {
            final String nextAttrib = pathAttribute.substring(attribute.length() + 1);
            List<Object> flatten = new ArrayList<>();
//            return result.stream().map(r -> getObjectAttributeValueRgx(r, nextAttrib)).flatMap(List::stream).collect(Collectors.toList());
            result.stream().map(r -> getObjectAttributeValueRgx(r, nextAttrib)).filter(l -> l != null).forEach(l -> flatten.addAll((List<Object>) l));
            return flatten;
        } else {
            return result;
        }
    }

    public static Boolean hasAttribute(Object obj, String pathAttribute) {
        String attribute = pathAttribute;
        while (attribute.startsWith(".")) {
            attribute = attribute.substring(1);
        }
        pathAttribute = attribute;
        if (attribute.contains("."))
            attribute = attribute.substring(0, attribute.indexOf("."));

        Object result = null;
        // Cas des indices de liste
        if (attribute.replaceAll("[\\d]+", "").length() == 0) {
            if(pathAttribute.contains(".")) {
                try {
                    result = obj.getClass().getMethod("get", int.class).invoke(obj, Integer.parseInt(attribute));
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                         | NoSuchMethodException | SecurityException e) {
                    logger.debug(e.getMessage(), e);
                    for (Method m : obj.getClass().getMethods()) {
                        logger.debug(m);
                    }
                }
            }else{
                try {
                    return obj.getClass().getMethod("get", int.class) != null;
                } catch (IllegalArgumentException | NoSuchMethodException | SecurityException ignore) {}
            }
        } else {

            if(pathAttribute.contains(".")) {
                // Si pas un entier on a une exception donc ce n'est pas une liste
                try {
                    result = getAttributeAccessMethod(obj, attribute).invoke(obj);
                } catch (Exception e) {
                    logger.debug(e.getMessage(), e);
                    logger.debug("obj was " + obj + " path was '" + pathAttribute + "' and attribute " + " '" + attribute + "'");
                    logger.debug("Now we try to get the attribute as a map key");
                    try {
                        result = obj.getClass().getMethod("get", Object.class).invoke(obj, attribute);
                    } catch (Exception emap) {
                        logger.debug(emap.getMessage(), emap);
                    }
                }
            }else{
                try {
                    if(getAttributeAccessMethod(obj, attribute) != null)
                        return true;
                } catch (Exception e) {
                    try {
                        return obj.getClass().getMethod("get", Object.class) != null;
                    } catch (Exception ignore) {}
                }
            }
        }

        if (result != null && pathAttribute.contains(".")) {
            return hasAttribute(result, pathAttribute.substring(attribute.length() + 1));
        } else {
            return false;
        }
    }


    public static Boolean hasSuperClassSuffix(Class<?> resqmlclass, String classSuffix) {
        if (resqmlclass != null) {
            if (resqmlclass.getName().toLowerCase().endsWith(classSuffix.toLowerCase())) {
                return true;
            } else {
                return hasSuperClassSuffix(resqmlclass.getSuperclass(), classSuffix);
            }
        }
        return false;
    }

    public static List<Class<?>> getSuperClasses(Class<?> resqmlClass) {
        Class<?> currentClass = resqmlClass.getSuperclass();
        List<Class<?>> superList = new ArrayList<>();
        while (currentClass != null) {
            superList.add(currentClass);
            currentClass = currentClass.getSuperclass();
        }
        return superList;
    }

    public static Class<?> getSubAttributeClass(String path, Class<?> objClass, ParameterizedType templates) throws Exception {
        // TODO: utiliser les variations d'attributs
        String currentParam = path;
        while (currentParam.startsWith(".")) {
            currentParam = currentParam.substring(1);
        }
        path = currentParam;

        currentParam = currentParam.substring(0, 1).toUpperCase();
        if (path.length() > 1) {
            currentParam += path.substring(1);
        }

        if (currentParam.contains(".")) {
            currentParam = currentParam.substring(0, currentParam.indexOf("."));
        }

        Method mGet = null, mIs = null;
        try {
            Integer.parseInt(currentParam);
            try {
                mGet = objClass.getMethod("get", int.class);
            } catch (NoSuchMethodException | SecurityException e) {
                try {
                    mGet = objClass.getMethod("get", Integer.class);
                } catch (NoSuchMethodException | SecurityException ignored) {
                }
            }
        } catch (NumberFormatException nfe) {
            // currentParam n'est pas un nombre donc on est pas sur l'acces d'une liste
            try {
                mGet = objClass.getMethod("get" + currentParam);
            } catch (NoSuchMethodException | SecurityException ignored) {
            }

            try {
                mIs = objClass.getMethod("is" + currentParam);
            } catch (NoSuchMethodException | SecurityException ignored) {
            }
        }


        if (mGet != null) {
            Class<?> getReturnClass = mGet.getReturnType();

            // Si on est au bout du chemin
            if (!path.contains(".")) {
                return getReturnClass;
            } else {

                int templatedTypeIdx = -1;
                String potentialTemplTypeName = mGet.getAnnotatedReturnType().getType().getTypeName();
                TypeVariable<?>[] tvList = objClass.getTypeParameters();
                for (int tvi = 0; tvi < tvList.length; tvi++) {
                    //logger.error(tv.getName() + " -- " + tv.getTypeName());
                    if (tvList[tvi].getName().compareTo(potentialTemplTypeName) == 0) {
                        templatedTypeIdx = tvi;
                        break;
                    }
                }
                if (templatedTypeIdx >= 0) {
                    // Si on est sur l'acces Ã  un element dont le type est un template
                    getReturnClass = (Class<?>) templates.getActualTypeArguments()[templatedTypeIdx];
                }
                ParameterizedType tempType = getClassTemplates(objClass, currentParam);
                return getSubAttributeClass(path.substring(currentParam.length()), getReturnClass, tempType);

            }
        } else if (mIs != null) {
            return Boolean.class;
        }


        throw new Exception("No such parameter type found in #getAttributeClass with parameter : "
                + path + " and class " + objClass.getName() + " -- " + currentParam + "\n");
    }


    /**
     * Return true if the class cl is of type "type" or inherits from a class named "type"
     *
     * @param cl
     * @param type
     * @return
     */
    public static boolean inherits(Class<?> cl, String type, boolean caseSensitive, boolean searchInSuperclass) {
        if (cl != null)
            if (cl.getSimpleName().contains(type) || (!caseSensitive && cl.getSimpleName().compareToIgnoreCase(type) == 0))
                return true;
            else if (searchInSuperclass && cl.getGenericSuperclass() != null)
                try {
                    return inherits(Class.forName(cl.getGenericSuperclass().getTypeName()), type, caseSensitive, searchInSuperclass);
                } catch (ClassNotFoundException e) {
                }
        return false;
    }

    public static boolean inherits(Class<?> cl1, Class<?> cl2, boolean searchInSuperclass) {
        if (cl1 != null && cl2 != null)
            if (cl1 == cl2)
                return true;
            else if (searchInSuperclass && cl1.getGenericSuperclass() != null)
                try {
                    return inherits(Class.forName(cl1.getGenericSuperclass().getTypeName()), cl2,
                            searchInSuperclass);
                } catch (ClassNotFoundException e) {
                }
        return false;
    }

    public static List<Class<?>> getResqmlInheritorClasses(String rootClassName, List<Class<?>> classList) {
        List<Class<?>> classListResult = new ArrayList<>();
        try {
            Class<?> rootClass = Class.forName(rootClassName);
            // on filtre les objets racine qui héritent de AbstractObject et qui ne sont pas des classes abstraites
            classListResult.addAll(classList.stream().filter(objClass -> !Modifier.isAbstract(objClass.getModifiers())
                            && rootClass.isAssignableFrom(objClass))
                    .collect(Collectors.toList()));
        } catch (ClassNotFoundException e) {
            logger.error(e.getMessage());
            logger.debug(e.getMessage(), e);
        }
        return classListResult;
    }

    public static boolean isPrimitiveClass(Object obj) {
        return obj != null && isPrimitiveClass(obj.getClass());
    }

    public static boolean isPrimitiveClass(Class<?> initialClass) {
        return getAllJavaPrimitiveObjectClasses().stream().map(cl -> initialClass.getName().compareToIgnoreCase(cl.getName()) == 0).reduce(Boolean.FALSE, Boolean::logicalOr);
    }

    public static List<Class<?>> getAllJavaPrimitiveObjectClasses() {
        return Arrays.asList(
                Boolean.class,
                Byte.class,
                Character.class,
                Double.class,
                Float.class,
                Integer.class,
                Long.class,
                Short.class,
                String.class
        );
    }

    public static boolean classMatchRgx(
            Object obj,
            String rgx,
            boolean superClassSearch,
            int reFlags
    ) {
        if(obj != null)
            return classMatchRgx(obj.getClass(), rgx, superClassSearch, reFlags);
        return false;
    }

    public static boolean classMatchRgx(
            Class<?> cls,
            String rgx,
            boolean superClassSearch,
            int reFlags
    ) {
        if (cls == null) {
            cls = Object.class;
        }

        Pattern pattern = Pattern.compile(rgx, reFlags);
        Matcher matcher = pattern.matcher(cls.getSimpleName());
        if (matcher.find()) {
            return true;
        }

        if (!isPrimitiveClass(cls) && superClassSearch) {
            for (Class<?> base : cls.getSuperclass().getClasses()) {
                if (classMatchRgx(base, rgx, true, reFlags)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isPropertyClass(Class<?> type) {
        return (!List.class.isAssignableFrom(type) && !type.getName().endsWith("Array"))
                && (
                (!Modifier.isAbstract(type.getModifiers()) && getClassAttributes(type).size() <= 0)
                        || XMLGregorianCalendar.class.isAssignableFrom(type)
                        || type.getSimpleName().compareTo(BigInteger.class.getSimpleName()) == 0
        );

        // On filtre ces classes pour les mettre en propriete;
    }

    public static List<Object> searchAttributeMatchingName(
            Object obj,
            String nameRegex
    ) {
        return new ArrayList<>(searchAttributeMatchingNameWithPath(obj, nameRegex).values());
    }

    public static Map<String, Object> searchAttributeMatchingNameWithPath(
            Object obj,
            String nameRegex
    ) {
        return searchAttributeMatchingNameWithPath(obj, nameRegex, Pattern.CASE_INSENSITIVE, "", true, true);
    }

    public static Map<String, Object> searchAttributeMatchingNameWithPath(
            Object obj,
            String nameRegex,
            boolean searchInSubObj
    ) {
        return searchAttributeMatchingNameWithPath(obj, nameRegex, Pattern.CASE_INSENSITIVE, "", true, searchInSubObj);
    }

    public static List<Object> searchAttributeMatchingName(
            Object obj,
            String nameRegex,
            int reFlags,
            String currentPath,
            boolean deepSearch,
            boolean searchInSubObj
    ) {
        return new ArrayList<>(searchAttributeMatchingNameWithPath(obj, nameRegex, reFlags, currentPath, deepSearch, searchInSubObj).values());
    }

    public static Map<String, Object> searchAttributeMatchingNameWithPath(
            Object obj,
            String nameRegex,
            int reFlags,
            String currentPath,
            boolean deepSearch,
            boolean searchInSubObj
    ) {
//        logger.info("searchAttributeMatchingNameWithPath : {}", obj);
//        logger.info("\tpath : {}", currentPath);
        if(obj == null){
            return new HashMap<>();
        }
        while (nameRegex.startsWith(".")) {
            nameRegex = nameRegex.substring(1);
        }
        String currentMatch = nameRegex;
        String nextMatch = currentMatch;
        if (nameRegex.contains(".")) {
            String[] attributeList = nameRegex.split("(?<!\\\\)\\.+");
//            logger.info("\t{}", attributeList);
            currentMatch = attributeList[0];
            nextMatch = String.join(".", List.of(attributeList).subList(1, attributeList.length));
        }
//        currentMatch = currentMatch.replaceAll("\\.", ".");

//        logger.info("\tcurrentMatch : {}", currentMatch);

        Map<String, Object> result = new HashMap<>();

        Map<String, Object> matchPathAndObj = new HashMap<>();
        Map<String, Object> notMatchPathAndObj = new HashMap<>();
        if (obj instanceof List) {
            int cpt = 0;
            for (Object subObj : (List<?>) obj) {
                Matcher match = Pattern.compile(currentMatch, reFlags).matcher(String.valueOf(cpt));
                if (match.find()) {
                    matchPathAndObj.put(currentPath + "." + cpt, subObj);
                } else {
                    notMatchPathAndObj.put(currentPath + "." + cpt, subObj);
                }
                cpt++;
            }
        } else if (obj instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                Matcher match = Pattern.compile(currentMatch, reFlags).matcher(String.valueOf(entry.getKey()));
                if (match.find()) {
                    matchPathAndObj.put(currentPath + "." + entry.getKey(), entry.getValue());
                } else {
                    notMatchPathAndObj.put(currentPath + "." + entry.getKey(), entry.getValue());
                }
            }
        } else if (!isPrimitiveClass(obj)) {

            for (Pair<Class<?>, String> attributeName : getClassAttributes(obj.getClass())) {
                Matcher match = Pattern.compile(currentMatch, reFlags).matcher(attributeName.r());
                if (match.find()) {
//                    logger.info("\t=====: {}, {}, {}", attributeName.r(), match.matches(), attributeName);
                    matchPathAndObj.put(currentPath + "." + attributeName.r(), getObjectAttributeValue(obj, attributeName.r()));
                }else{
                    notMatchPathAndObj.put(currentPath + "." + attributeName.r(), getObjectAttributeValue(obj, attributeName.r()));
                }
            }
        }

//        if(!matchPathAndObj.isEmpty())
//            logger.info("\tmatchPathAndObj: {}", matchPathAndObj);
//        if(!notMatchPathAndObj.isEmpty())
//            logger.info("\tnotMatchPathAndObj: {}", notMatchPathAndObj);

        for (Map.Entry<String, Object> matched : matchPathAndObj.entrySet()) {
//            logger.info("\tcurrentMatch: {}", currentMatch);
//            logger.info("\tnextMatch: {}", nextMatch);
            if (!currentMatch.equals(nextMatch) && nextMatch.length() > 0) { // next_match is different, match is not final
                result.putAll(searchAttributeMatchingNameWithPath(
                        matched.getValue(),
                        nextMatch,
                        reFlags,
                        matched.getKey(),
                        false, // no deep with partial
                        false // no partial search in sub obj with no match
                ));
            } else { // a complete match
                result.put(matched.getKey(), matched.getValue());
//                logger.info("\tresult: {}", result);
                if (deepSearch) {
                    result.putAll(searchAttributeMatchingNameWithPath(
                            matched.getValue(),
                            nameRegex,
                            reFlags,
                            matched.getKey(),
                            deepSearch,
                            true
                    ));
                }
            }
        }
        if (searchInSubObj) {
            for (Map.Entry<String, Object> notMatched : notMatchPathAndObj.entrySet()) {
                result.putAll(searchAttributeMatchingNameWithPath(
                        notMatched.getValue(),
                        nameRegex,
                        reFlags,
                        notMatched.getKey(),
                        deepSearch,
                        true
                ));
            }
        }

        return result;
    }

    public static Object searchAttributeInUpperMatchingName(
            Object obj,
            String nameRgx,
            Object rootObj,
            String currentPath
    ) {
        return searchAttributeInUpperMatchingName(obj, nameRgx, rootObj, Pattern.CASE_INSENSITIVE, currentPath);
    }

    public static Object searchAttributeInUpperMatchingName(
            Object obj,
            String nameRgx,
            Object rootObj,
            int reFlags,
            String currentPath
    ) {
        List<Object> eltList = searchAttributeMatchingName(obj, nameRgx, reFlags, "", false, false);
        if (eltList != null && !eltList.isEmpty()) {
            return eltList;
        }

        if (!currentPath.isEmpty()){  //(obj != rootObj) {
            String upperPath = currentPath.substring(0, currentPath.lastIndexOf("."));
            if (!upperPath.isEmpty()) {
                return searchAttributeInUpperMatchingName(
                        getObjectAttributeValue(rootObj, upperPath),
                        nameRgx,
                        rootObj,
                        reFlags,
                        upperPath
                );
            }else{
                return searchAttributeInUpperMatchingName(
                        rootObj,
                        nameRgx,
                        rootObj,
                        reFlags,
                        upperPath
                );
            }
        }

        return null;
    }

    public static List<Object> searchAttributeMatchingType(
            Object obj,
            String typeRgx,
            int reFlags,
            boolean returnSelf,
            boolean deepSearch,
            boolean superClassSearch
    ) {
        return searchAttributeMatchingTypeWithPath(
                obj,
                typeRgx,
                reFlags,
                returnSelf,
                deepSearch,
                superClassSearch,
                ""
        ).stream()
                .map(pathVal -> pathVal.getValue())
                .collect(Collectors.toList());
    }

    public static List<Map.Entry<String, Object>> searchAttributeMatchingTypeWithPath(
            Object obj,
            String typeRgx,
            int reFlags,
            boolean returnSelf,
            boolean deepSearch,
            boolean superClassSearch,
            String currentPath
    ) {
        List<Map.Entry<String, Object>> res = new ArrayList<>();
        if (obj != null) {
            if (returnSelf && classMatchRgx(obj, typeRgx, superClassSearch, reFlags)) {
                res.add(new AbstractMap.SimpleEntry<>(currentPath, obj));
                if (!deepSearch) {
                    return res;
                }
            }
        }else{
            return new ArrayList<>();
        }

        if (obj instanceof List) {
            int cpt = 0;
            for (Object s_o : (List<?>) obj) {
                res.addAll(searchAttributeMatchingTypeWithPath(
                        s_o,
                        typeRgx,
                        reFlags,
                        true,
                        deepSearch,
                        superClassSearch,
                        currentPath + "." + cpt
                ));
                cpt++;
            }
        } else if (obj instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                res.addAll(searchAttributeMatchingTypeWithPath(
                        entry.getValue(),
                        typeRgx,
                        reFlags,
                        true,
                        deepSearch,
                        superClassSearch,
                        currentPath + "." + entry.getKey()
                ));
            }
        } else if (!isPrimitiveClass(obj)) {
            for (Pair<Class<?>, String> att : getClassAttributes(obj.getClass())) {
                res.addAll(searchAttributeMatchingTypeWithPath(
                        getObjectAttributeValueRgx(obj, att.r()),
                        typeRgx,
                        reFlags,
                        true,
                        deepSearch,
                        superClassSearch,
                        currentPath + "." + att.r()
                ));
            }
        }

        return res;
    }

}
