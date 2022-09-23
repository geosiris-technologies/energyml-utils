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

import com.geosiris.energyml.pkg.EPCRelsRelationshipType;
import com.geosiris.energyml.pkg.OPCContentType;
import com.geosiris.energyml.pkg.OPCCorePackage;
import com.geosiris.energyml.pkg.OPCRelsPackage;
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

import javax.xml.datatype.XMLGregorianCalendar;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
public class EPCGenericManager {
    public static Logger logger = LogManager.getLogger(EPCGenericManager.class);

    public static final String REGEX_DOMAIN_VERSION = "(?<domainVersion>(?<versionNum>([\\d]+[\\._])*\\d)\\s*(?<dev>dev\\s*(?<devNum>[\\d]+))?)";
    public static final String REGEX_DOMAIN_VERSION_FLAT = "(?<domainVersion>(?<versionNumFlat>([\\d]+)*\\d)\\s*(?<dev>dev\\s*(?<devNum>[\\d]+))?)";
    public static final String REGEX_ENERGYML_CLASS_NAME = "(?<prefix>[\\w\\.]+)\\.(?<packageName>(?<domain>"
            + ContextBuilder.getPkgNamePattern()
            + ")(?<version>(?<devPrefix>_(?<dev>dev(?<devNum>[\\d]+))x_)?(?<versionNum>([\\d]+[\\._])*\\d)))(\\.(?<type>\\w+))?";

    public static final String REGEX_ENERGYML_SCHEMA_VERSION ="(?<name>"
            + ContextBuilder.getPkgNamePattern()
            + ")?\\s*v?" + REGEX_DOMAIN_VERSION + "\\s*$";

    public static final Pattern PATTERN_CONTENT_TYPE =
            Pattern.compile("application/(?<xmlDomain>x\\-(?<domain>[\\w]+)\\+xml);version=" + REGEX_DOMAIN_VERSION + ";type=(?<type>\\w+)\\s*$");
    public static final Pattern PATTERN_QUALIFIED_TYPE = Pattern.compile("(?<domain>[a-zA-Z]+)" + REGEX_DOMAIN_VERSION_FLAT + "\\.(?<type>[\\w_]+)");

    public static final Pattern PATTERN_UUID = Pattern.compile("(?<uuid>[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");

    public static final Pattern PATTERN_PROJECT_VERSION = Pattern.compile("(?<n0>[\\d]+)(.(?<n1>[\\d]+)(.(?<n2>[\\d]+))?)?");

    public static final Pattern PATTERN_ENERGYML_CLASS_NAME = Pattern.compile(REGEX_ENERGYML_CLASS_NAME);
    public static final Pattern PATTERN_ENERGYML_SCHEMA_VERSION = Pattern.compile(REGEX_ENERGYML_SCHEMA_VERSION, Pattern.CASE_INSENSITIVE);

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
                        String contentType = "";
                        try{
                            contentType = (String) ObjectController.getObjectAttributeValue(dataObject.get(0), "contentType");
                        }catch(Exception ignore){}
                        try{
                            contentType = (String) ObjectController.getObjectAttributeValue(dataObject.get(0), "qualifiedType");
                        }catch(Exception ignore){}
                        Matcher m = PATTERN_CONTENT_TYPE.matcher(contentType);
                        if (m.find()) {
                            String dorType = m.group("type");
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
        List<Object> referencedDOR = ObjectController.findSubObjects(resqmlObj, "DataObjectReference", true);
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

    public static Boolean isResqml201(Object obj){
        String className = obj.getClass().getName();
        return className.contains("resqml2_0") || className.contains("common2_0");
    }

    public static Boolean isResqml22_final(Object obj){
        String className = obj.getClass().getName();
        return className.contains("resqml2_2") || className.contains("common2_3");
    }
    public static String genPathInEPC(Object obj, ExportVersion version){
        StringBuilder sb = new StringBuilder();
        if (obj != null) {
            switch (version) {
                case EXPANDED:
                    try {
                        String objVersion = (String) ObjectController.getObjectAttributeValue(obj, "ObjectVersion");
                        sb.append("namespace_").append(getPackageDomain_withVersionForETP(obj, 2, 2, true));
                        if (objVersion != null && objVersion.length() > 0) {
                            sb.append("/");
                            sb.append("version_");
                            sb.append(objVersion);
                        }
                        sb.append("/");
                    }catch (Exception e) {
                        logger.error("Error reading object version");
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

    public static String getEPCObjectFileName(Object ao){
        if(ao != null)
            return getObjectTypeForFilePath(ao) + "_" + ObjectController.getObjectAttributeValue(ao, "uuid") + ".xml";
        return "";
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

            List<String> source = ObjectController.findSubObjects(resqmlObj, "DataObjectReference", true).stream()
                    .map(obj -> {
                        try{ return (String) ObjectController.getObjectAttributeValue(obj, "uuid");
                        }catch (Exception ignore){}
                        return null;
                    }).filter(Objects::nonNull).collect(Collectors.toList());

            Relationships rels = new Relationships();

            for(String dest_rel : dest){
                Relationship rel = new Relationship();
                rel.setType(EPCRelsRelationshipType.DestinationObject.getType());
                rel.setId(uuid + "_" + genPathInEPC(workspace.get(dest_rel), exportVersion));
                rel.setTarget(genPathInEPC(workspace.get(dest_rel), exportVersion));
                rels.getRelationship().add(rel);
            }
            for(String source_rel : source){
                Relationship rel = new Relationship();
                rel.setType(EPCRelsRelationshipType.SourceObject.getType());
                rel.setId(uuid + "_" + genPathInEPC(workspace.get(source_rel), exportVersion));
                rel.setTarget(genPathInEPC(workspace.get(source_rel), exportVersion));
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
                            rel.setId(h5Rel.getId());
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
            overrideObjRels.setContentType(getObjectContentType(resqmlObj, true));
            overrideObjRels.setPartName("/" + genPathInEPC(resqmlObj, exportVersion)); // On ajoute le "/" car obligatoire pour etre lu dans ResqmlCAD
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
        assert now != null;
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

    public static Boolean isRootClass(Class<?> objClass) {
        if (objClass != null) {
            return ObjectController.hasSuperClassSuffix(objClass, "AbstractObject");
        }
        return false;
    }

    public static String findUUID(String content){
        if(content != null) {
            Matcher m = PATTERN_UUID.matcher(content);
            if (m.find()) {
                return m.group("uuid");
            }
        }
        return null;
    }

    public static Pair<String, String> getDomainAndVersionFromContentType(String contentType){
        Matcher m = PATTERN_CONTENT_TYPE.matcher(contentType);
        if(m.find()){
            return new Pair<>(m.group("domain"), m.group("domainVersion"));
        }
        return null;
    }
    
    public static String getSchemaVersionFromClassName(String className) {
        return getSchemaVersionFromClassName(className, false);
    }

    public static String getSchemaVersionFromClassName(String className, boolean printDevVersion) {
        return getSchemaVersionFromClassName(className, printDevVersion, 2);
    }

    public static String getSchemaVersionFromClassName(String className, boolean printDevVersion, int nbMaxVersionDigits) {
        if (className != null) {
            logger.debug("@getSchemaVersionFromClassName " + PATTERN_ENERGYML_SCHEMA_VERSION + " == " + className);
            Matcher pkgMatch = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(className);
            if(pkgMatch.find()) {
                return (reshapeVersion(pkgMatch.group("versionNum"), nbMaxVersionDigits) + (pkgMatch.group("dev") != null  && printDevVersion? pkgMatch.group("dev"): "")).replace("_", ".");
            }
        }
        logger.error("@getSchemaVersionFromClassName error generating schema version for " + className);
        return null;
    }

    public static String getSchemaVersion(Object obj) {
        return getSchemaVersion(obj, false);
    }

    public static String getSchemaVersion(Object obj, boolean printDevVersion) {
        return getSchemaVersion(obj, printDevVersion, 2);
    }

    public static String getSchemaVersion(Object obj, boolean printDevVersion, int nbMaxVersionDigits) {
        if (obj != null) {
            if(obj instanceof String){
                return getSchemaVersionFromClassName((String) obj, printDevVersion, nbMaxVersionDigits);
            }else{
                return getSchemaVersionFromClassName(obj.getClass().getName(), printDevVersion, nbMaxVersionDigits);
            }
        }
        logger.error("@getSchemaVersion error generating schema version for null object");
        return null;
    }

    /**
     * Takes a schema version like "Resqml v2.2", "v2.2", "resqml2.2" etc. and normalize it like : "2.2".
     * For dev version of a package, the result will be like : "2.2dev3"
     * @param schemaVersion a non normalize schema version.
     * @return a normalizes schema version.
     */
    public static String reformatSchemaVersion(String schemaVersion) {
        if (schemaVersion != null) {
            Matcher pkgMatch = PATTERN_ENERGYML_SCHEMA_VERSION.matcher(schemaVersion);
            if(pkgMatch.find()) {
                return (pkgMatch.group("versionNum") + (pkgMatch.group("dev") != null ? pkgMatch.group("dev"): "")).replace("_", ".");
            }
            return "";
        }
        return null;
    }

    public static String getPackageDomain(Object obj){
        if (obj != null) {
            if(obj instanceof String){
                return getPackageDomain_fromClassName((String) obj);
            }else{
                return getPackageDomain_fromClassName(obj.getClass().getName());
            }
        }
        logger.error("@getPackageDomain error generating object content Type for null object ");
        return "";
    }

    public static String getPackageDomain_fromClassName(String className){
        Matcher pkgMatch = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(className);
        if(pkgMatch.find()) {
            String domain = pkgMatch.group("domain");
            return domain.compareToIgnoreCase("common") == 0 ? "eml" : domain;
        }
        return "###error_unkown_object_" + className + "###";
    }

    public static String getPackageDomain_withVersionForETP(Object obj, int minVersionDigit, int maxVersionDigit, boolean printDevVersion){
        if(obj instanceof String){
            return getPackageDomain_withVersionForETP_fromClassName((String) obj, minVersionDigit, maxVersionDigit, printDevVersion);
        }else {
            return getPackageDomain_withVersionForETP_fromClassName(obj.getClass().getName(), minVersionDigit, maxVersionDigit, printDevVersion);
        }
    }
    public static String getPackageDomain_withVersionForETP_fromClassName(String className, int minVersionDigit, int maxVersionDigit, boolean printDevVersion){
        if(maxVersionDigit<minVersionDigit){
            maxVersionDigit = minVersionDigit;
        }
        Matcher pkgMatch = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(className);

        if(pkgMatch.find()) {
            StringBuilder version = new StringBuilder(pkgMatch.group("versionNum").replaceAll("[\\._]", ""));
            while (version.length() < minVersionDigit){
                version.append("0");
            }
            version = new StringBuilder(version.substring(0, Math.min(version.length(), maxVersionDigit)));
            if(printDevVersion) {
                version.append((pkgMatch.group("dev") != null ? pkgMatch.group("dev") : ""));
            }
            return getPackageDomain_fromClassName(className) + version;
        }
        return "###error_unkown_object_" + className + "###";
    }

    public static String getObjectContentType(Object obj){
        return getObjectContentType(obj, true);
    }

    public static String getObjectContentType(Object obj, boolean printDevVersion){
        return getObjectContentType(obj, printDevVersion, 2);
    }

    public static String getObjectContentType(Object obj, boolean printDevVersion, int nbMaxVersionDigits){
        if (obj != null) {
            if(obj instanceof String){
                return getObjectContentType_fromClassName((String) obj, printDevVersion, nbMaxVersionDigits);
            }else{
                return getObjectContentType_fromClassName(obj.getClass().getName(), printDevVersion, nbMaxVersionDigits);
            }
        }
        logger.error("@getObjectContentType error generating object content Type for null object ");
        return "";
    }

    public static String getObjectContentType_fromClassName(String className){
        return getObjectContentType_fromClassName(className, false);
    }
    public static String getObjectContentType_fromClassName(String className, boolean printDevVersion){
        return getObjectContentType_fromClassName(className, printDevVersion, 2);
    }
    public static String getObjectContentType_fromClassName(String className, boolean printDevVersion, int nbMaxVersionDigits){
        if (className != null) {
            return "application/x-" + getPackageDomain_fromClassName(className)
                    +"+xml;version=" + getSchemaVersionFromClassName(className, printDevVersion, nbMaxVersionDigits) + ";type="
                    + getObjectTypeForFilePath_fromClassName(className);
        }
        logger.error("@getObjectContentType error generating object content Type for null object ");
        return "";
    }

    public static String getObjectQualifiedType(Object obj) {
        return getObjectQualifiedType(obj, false);
    }

    public static String getObjectQualifiedType(Object obj, boolean printDevVersion) {
        return getObjectQualifiedType(obj, printDevVersion, 2);
    }

    public static String getObjectQualifiedType(Object obj, boolean printDevVersion, int nbMaxVersionDigits) {
        if (obj != null) {
            if(obj instanceof String){
                return getObjectQualifiedType_fromClassName((String) obj, printDevVersion, nbMaxVersionDigits);
            }else{
                return getObjectQualifiedType_fromClassName(obj.getClass().getName(), printDevVersion, nbMaxVersionDigits);
            }

        }
        logger.error("@getObjectQualifiedType error generating object qualified Type for null object ");
        return "";
    }

    public static String getObjectQualifiedType_fromClassName(String className, boolean printDevVersion, int nbMaxVersionDigits) {
        if (className != null) {
            return getPackageDomain_withVersionForETP_fromClassName(className, 2, nbMaxVersionDigits, printDevVersion) + "." + getObjectTypeForFilePath_fromClassName(className);
        }
        logger.error("@getObjectQualifiedType error generating object qualified Type for null object ");
        return "";
    }

    public static String getObjectTypeForFilePath(Object obj) {
        return getObjectTypeForFilePath_fromClassName(obj.getClass().getName());
    }

    public static String getObjectTypeForFilePath_fromClassName(String className) {
        String objType = className.substring(className.lastIndexOf(".") + 1);
        String schemaVersion = getSchemaVersionFromClassName(className);
        assert schemaVersion != null;
        if (schemaVersion.startsWith("2.0") && objType.startsWith("Obj")) {
            objType = objType.replace("Obj", "obj_");
        }
        objType = objType.replaceAll("(\\d+)D", "$1d");
        return objType;
    }

    /**
     * Reshape a project version to have only specific number of digits. If 0 < nbDigit < 4 then the reshape is done,
     * else, the original version is returned.
     * Example : reshapeVersion("v2.0.1", 2) ==> "2.0" and reshapeVersion("version2.0.1.3.2.5", 4) ==> "version2.0.1.3.2.5"
     * @param version
     * @param nbDigit
     * @return
     */
    public static String reshapeVersion(String version, int nbDigit){
        Matcher m = PATTERN_PROJECT_VERSION.matcher(version);
        if(m.find()) {
            String n0 = m.group("n0");
            String n1 = m.group("n1");
            String n2 = m.group("n2");
            if (nbDigit == 1) {
                return n0;
            } else if (nbDigit == 2) {
                return n0 + (n1!=null? "." + n1 : "");
            } else if (nbDigit == 3) {
                return n0 + (n1!=null? "." + n1 + (n2!=null? "." + n2 : "") : "");
            }
        }
        return version;
    }
}
