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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EPCGenericManager {
    public static Logger logger = LogManager.getLogger(EPCGenericManager.class);

    private final static Pattern pattern_contentType_type = Pattern.compile("type=([\\w]+)");

    public static List<Object> getAccessibleDORsSimple(Object resqmlCurrentObj, Collection<Object> resqmlObjList) {
        List<Object> accessible = new ArrayList<>();

        ResqmlAbstractType dorPossibleClass;

        switch (Utils.getFIRPObjectType(resqmlCurrentObj.getClass())) {
            case Interpretation:
                dorPossibleClass = ResqmlAbstractType.Feature;
                break;
            case Representation:
                dorPossibleClass = ResqmlAbstractType.Interpretation;
                break;
            case Property:
                dorPossibleClass = ResqmlAbstractType.Representation;
                break;
            default:
                dorPossibleClass = ResqmlAbstractType.ALL;
                break;
        }

        for (Object obj : resqmlObjList) {
            if (dorPossibleClass == ResqmlAbstractType.ALL
                    || Utils.getFIRPObjectType(obj.getClass()) == dorPossibleClass
                    && !accessible.contains(obj)
                    && !obj.equals(resqmlCurrentObj)) {
                accessible.add(obj);
            }
        }

        return accessible;
    }

    /**
     * Retourne la liste des objets assignables pour un DOR en fonction d'un filtre
     * par type défini dans mapAccessibleDORTypes
     *
     * @param resqmlCurrentObj
     * @param resqmlObjList
     * @param mapAccessibleDORTypes
     * @return
     */
    public static List<Object> getAccessibleDORs(
            Object resqmlCurrentObj,
            Object subAttribute,
            String subAttributePath,
            Collection<Object> resqmlObjList,
            Map<String, List<String>> mapAccessibleDORTypes) {
        List<Object> accessible = new ArrayList<>();
        Class<?> subAttributeClass = subAttribute != null ? subAttribute.getClass() : null;

        logger.info("#getAccessibleDORs [resqmlCurrentObj:" + resqmlCurrentObj + "] [subAttribute: " + subAttribute + "] [subAttributePath: " + subAttributePath + "]");
        if (subAttributeClass == null) {
            subAttributeClass = resqmlCurrentObj.getClass();
        }

        List<String> accessiblesTypes = new ArrayList<>();
        if (mapAccessibleDORTypes != null) {
            for (String k : mapAccessibleDORTypes.keySet()) {
                if (ObjectController.hasSuperClassSuffix(subAttributeClass, k)) {
                    accessiblesTypes.addAll(mapAccessibleDORTypes.get(k));
                }
            }
        }

        if (accessiblesTypes.size() > 0) {
            for (Object obj : resqmlObjList) {
                for (String acType : accessiblesTypes) {
                    if (ObjectController.hasSuperClassSuffix(obj.getClass(), acType)
                            && !accessible.contains(obj)
                            && !obj.equals(resqmlCurrentObj)) {
                        accessible.add(obj);
                    }
                }
            }
        } else {
            accessible = getAccessibleDORsSimple(subAttributeClass, resqmlObjList);
        }

        if (subAttributeClass.getName().endsWith("SingleCollectionAssociation")) {
            if (subAttributePath.toLowerCase().endsWith("dataobject")) {
                List<Object> dataObject = (List<Object>) ObjectController.getObjectAttributeValue(subAttribute, "Dataobject");
                Boolean isHomogeneous = (Boolean) ObjectController.getObjectAttributeValue(subAttribute, "IsHomogeneous");

                if (dataObject != null && dataObject.size() > 0) {
                    if (isHomogeneous != null && isHomogeneous) {

                        String contentType = null;
                        try{
                            contentType = (String) ObjectController.getObjectAttributeValue(dataObject.get(0), "contentType");
                        }catch(Exception ignore){}
                        try{
                            contentType = (String) ObjectController.getObjectAttributeValue(dataObject.get(0), "qualifiedType");
                        }catch(Exception ignore){}
                        Matcher m = pattern_contentType_type.matcher(contentType);
                        if (m.find()) {
                            String dorType = m.group(1);
                            if (dorType.startsWith("obj_")) {
                                dorType = dorType.substring(4);
                            }
                            final String dorTypeF = dorType;
                            accessible = accessible.stream().filter(o -> o.getClass().getName().contains(dorTypeF)).collect(Collectors.toList());
                        }
                    }

                    // On enleve ceux deja présents
                    accessible = accessible.stream().filter(o -> {
                        String o_uuid = (String) ObjectController.getObjectAttributeValue(o, "uuid");
                        for (Object obj_in : dataObject) {
                            String obj_in_uuid = (String) ObjectController.getObjectAttributeValue(obj_in, "uuid");
                            if (obj_in_uuid.compareTo(o_uuid) == 0) {
                                return false;
                            }
                        }
                        return true;
                    }).collect(Collectors.toList());
                }
            } else if (subAttributePath.toLowerCase().endsWith("collection")) {
                accessible = accessible.stream().filter(o -> o.getClass().getName().endsWith("DataobjectCollection")).collect(Collectors.toList());
            }
        }

        return accessible;
    }

    private static Boolean isReferencer(String uuid, Object resqmlObj) {
        List<Object> referencedDOR = ObjectController.findSubObjects(resqmlObj, "DataObjectReference");
        for (Object dor : referencedDOR) {
            String refUuid = (String) ObjectController.getObjectAttributeValue(dor, "uuid");
            if (refUuid != null && uuid.compareTo(refUuid) == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retourne la liste des object qui référence l'uuid passé en paramÃ¨tre
     */
    public static List<Object> getAllReferencersObjects(final String uuid, Map<String, Object> loadedObjects) {
        return loadedObjects.keySet().parallelStream()
                .filter(objUUID -> isReferencer(uuid, loadedObjects.get(objUUID)))
                .map(loadedObjects::get).collect(Collectors.toList());
    }

    /**
     * Retourne une liste de pair des objets qui sont référencés par l'objet dont l'uuid est passé en paramÃ¨tre
     * La paire contient le nom du parametre et l'uuid qu'il reference
     *
     * @param uuid
     * @param loadedObjects
     * @return
     */
    public static List<Pair<String, String>> getAllReferencedObjects(String uuid, Map<String, Object> loadedObjects) {
        List<Pair<String, String>> result = new ArrayList<>();
        HashSet<String> uuidfound = new HashSet<>();

        if (loadedObjects.containsKey(uuid)) {
            Map<String, Object> referencedDOR = ObjectController.findSubObjectsAndPath(loadedObjects.get(uuid), "DataObjectReference");

            for (String dorPath : referencedDOR.keySet()) {
                Object dor = referencedDOR.get(dorPath);

                String refUuid = (String) ObjectController.getObjectAttributeValue(dor, "uuid");
                if (refUuid != null && uuid.compareTo(refUuid) != 0
                        && !uuidfound.contains(refUuid)) {
                    String dorName = dorPath;
                    if (dorName.contains(".")) {
                        dorName = dorName.substring(dorName.lastIndexOf(".") + 1);
                    }
                    uuidfound.add(refUuid);
                    result.add(new Pair<>(dorName, refUuid));
                }
            }
        }
        return result;
    }

    /**
     * Retourne la liste des object qui reference l'uuid passe en parametre
     * avec des Pair<ParameterName, Referencer_Object>
     */
    public static List<Pair<String, Object>> getAllReferencersDORParameters(String uuid, Map<String, Object> loadedObjects) {
        List<Pair<String, Object>> result = new ArrayList<>();
        HashSet<String> keySet = new HashSet<>(loadedObjects.keySet());
        for (String objUUID : keySet) {
            Map<String, Object> referencedDOR = ObjectController.findSubObjectsAndPath(loadedObjects.get(uuid), "DataObjectReference");
            for (String dorPath : referencedDOR.keySet()) {
                Object dor = referencedDOR.get(dorPath);
                String refUuid = (String) ObjectController.getObjectAttributeValue(dor, "uuid");
                if (refUuid != null && uuid.compareTo(refUuid) == 0) {
                    String paramName = dorPath;
                    if (paramName.contains(".")) {
                        paramName = paramName.substring(paramName.lastIndexOf(".") + 1);
                    }
                    try {
                        int index = Integer.parseInt(paramName);
                        // Si le nom est un nombre, on cherche le nom precedent pour faire un nom de parametre
                        // du style : 'FeatureInterpretation[5]';
                        paramName = dorPath.substring(0, dorPath.lastIndexOf("."));
                        paramName = paramName.substring(paramName.lastIndexOf(".") + 1);
                        paramName += "[" + index + "]";
                    } catch (Exception e) { /*logger.error(e.getMessage(), e);*/}

                    result.add(new Pair<>(paramName, loadedObjects.get(objUUID)));
                    break;    // On en a trouve au moins un, on sort
                }
            }
        }

        return result;
    }

    /**
     * Les relation UP sont celle qui vont du bas vers le haut (e.g. Representation vers Interpretation) et les
     * DOWN sont dans l'autre sens.
     * Retourn une hasmap {key = UUID , Value = { relation_UP : [ Pair<paramName, uuid> ], relation_DOWN : [ Pair<paramName, uuid> ] }
     */
    public static HashMap<String, Pair<List<Pair<String, String>>, List<Pair<String, String>>>>
    getEpcRelationshipsWithParamName(Map<String, Object> loadedObjects) {
        HashMap<String, Pair<List<Pair<String, String>>, List<Pair<String, String>>>> relationships = new HashMap<>();

        // Variable temporaire pour eviter les potentielles modifications en cours de parcours par les fonctions
        // appelee lors de ce parcours
        List<String> resqmlObjectList = new ArrayList<>(loadedObjects.keySet());

        // On crée toutes les listes de relations
        for (String resqmlObjUUID : resqmlObjectList) {
            Pair<List<Pair<String, String>>, List<Pair<String, String>>> objRelations = new Pair<>(new ArrayList<>(), new ArrayList<>());
            relationships.put(resqmlObjUUID, objRelations);
        }

        resqmlObjectList.forEach(resqmlObjUUID ->
        {
            List<Pair<String, String>> listRef = getAllReferencedObjects(resqmlObjUUID, loadedObjects);
            for (Pair<String, String> dor : listRef) {
                relationships.get(resqmlObjUUID).l().add(dor);
                if (relationships.containsKey(dor.r())) {
                    relationships.get(dor.r()).r().add(new Pair<>(dor.l(), resqmlObjUUID));
                } else {
                    logger.error("ERROR while checking relations : " + dor.r() + " not found ");
                }
            }
        });


        return relationships;
    }
}
