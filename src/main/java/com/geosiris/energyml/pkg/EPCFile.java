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

import com.geosiris.energyml.utils.*;
import energyml.content_types.Default;
import energyml.content_types.Override;
import energyml.content_types.Types;
import energyml.core_properties.CoreProperties;
import energyml.relationships.Relationship;
import energyml.relationships.Relationships;
import jakarta.xml.bind.JAXBException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.purl.dc.elements._1.SimpleLiteral;

import javax.xml.datatype.XMLGregorianCalendar;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class EPCFile implements EnergymlWorkspace{
    public static Logger logger = LogManager.getLogger(EPCFile.class);

    /**
     * Energyml files mapped by <Identifier>. The different versions of the object identified by the identifier (see. @getIdentifier) are stored in a list.
     */
    Map<String, Object> energymlObjects;

    Map<String, InputStream> otherFiles;
    Map<Object, List<Relationship>> additionalRels;

    // Key is (Uuid;ObjectVersion)
//    Map<Pair<String, String>, List<Relationship>> readRels;

    ExportVersion version;
    CoreProperties coreProperties;
    EPCPackageManager pkgManager;

    String filePath;

    public EPCFile(EPCPackageManager pkgManager, ExportVersion version, CoreProperties coreProperties, Map<String, Object> energymlObjects, Map<String, InputStream> otherFiles, Map<Object, List<Relationship>> additionalRels ) {
        this.energymlObjects = energymlObjects;
        this.otherFiles = otherFiles;
        this.additionalRels = additionalRels;
        this.version = version;
        this.coreProperties = coreProperties;
        this.pkgManager = pkgManager;
        this.filePath = null;
    }

    public EPCFile(EPCPackageManager pkgManager, ExportVersion version, CoreProperties coreProperties){
        this(pkgManager, version, coreProperties, new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    public EPCFile(EPCPackageManager pkgManager, ExportVersion version){
        this(pkgManager, version, new CoreProperties());
    }

    public EPCFile(EPCPackageManager pkgManager, String filePath){
        this(pkgManager);
        this.filePath = filePath;
    }

    public EPCFile(EPCPackageManager pkgManager){
        this(pkgManager, ExportVersion.EXPANDED);
        coreProperties.setVersion("1.0");
        SimpleLiteral sl_creator = new SimpleLiteral();
        sl_creator.getContent().add("energyml-utils (Geosiris: http://www.geosiris.com)");
        coreProperties.setCreator(sl_creator);
    }

    public void export(OutputStream os) throws IOException {
        try(ZipOutputStream zos = new ZipOutputStream(os)){
            // Non energyml entries :
            for(Map.Entry<String, InputStream> e : otherFiles.entrySet()){
                ZipEntry zipEntry = new ZipEntry(e.getKey());
                zos.putNextEntry(zipEntry);
                e.getValue().transferTo(zos);
                zos.closeEntry();
            }

            // Content Type file
            Types contentTypeFile = new Types();

            // Core file
            SimpleLiteral sl_creationDate = new SimpleLiteral();
            XMLGregorianCalendar now = Utils.getCalendarForNow();
            assert now != null;
            sl_creationDate.getContent().add(Utils.calendarToW3CDTF(now));
            coreProperties.setCreated(sl_creationDate);
            String corePath = OPCCorePackage.genCorePath();

            Override overrideCore = new Override();
            overrideCore.setContentType(OPCCorePackage.getCoreContentType());
            overrideCore.setPartName("/" + corePath);
            contentTypeFile.getDefaultOrOverride().add(overrideCore);

            ZipEntry ze_core = new ZipEntry(corePath);
            zos.putNextEntry(ze_core);
            EPCGenericManager.marshal(OPCCorePackage.JAXB_CONTEXT, null, coreProperties, zos);
            zos.closeEntry();

            // Energyml Objects
            for(String identifier : energymlObjects.keySet()){
                List<Object> toImport = new ArrayList<>();
                if(this.version == ExportVersion.CLASSIC){
                    // Only import the last version
                    toImport.add(getLastModifiedObject(identifier));
                }else{
                    toImport.add(energymlObjects.get(identifier));
                }

                for(Object o : toImport){
                    String pathInEPC = EPCGenericManager.genPathInEPC(o, this.version);
                    ZipEntry ze_obj = new ZipEntry(pathInEPC);
                    zos.putNextEntry(ze_obj);
                    this.pkgManager.marshal(o, zos);
                    zos.closeEntry();

                    Override overrideObjContentType = new Override();
                    overrideObjContentType.setContentType(EPCGenericManager.getObjectContentType(o, true));
                    overrideObjContentType.setPartName("/" + pathInEPC); // '/' at start is mandatory for ResqmlCAD
                    contentTypeFile.getDefaultOrOverride().add(overrideObjContentType);
                }
            }

            // Rels
            Default relsDefaultCT = new Default();
            relsDefaultCT.setContentType(OPCRelsPackage.getRelsContentType());
            relsDefaultCT.setExtension("rels");
            contentTypeFile.getDefaultOrOverride().add(relsDefaultCT);

            Map<Object, Relationships> relsList = computeRelations();
            for(Map.Entry<Object, Relationships> rels: relsList.entrySet()){
                String pathInEPC = OPCRelsPackage.genRelsPathInEPC(rels.getKey(), this.version);
                ZipEntry ze_objRels = new ZipEntry(pathInEPC);
                zos.putNextEntry(ze_objRels);
                EPCGenericManager.marshal(OPCRelsPackage.JAXB_CONTEXT, null, rels.getValue(), zos);
                zos.closeEntry();
            }

            Relationships rootRels = new Relationships();
            Relationship root_core_rel = new Relationship();
            root_core_rel.setId("CoreProperties");
            root_core_rel.setTarget(corePath);
            root_core_rel.setType(EPCRelsRelationshipType.ExtendedCoreProperties.getType());
            rootRels.getRelationship().add(root_core_rel);

            ZipEntry ze_rootRels = new ZipEntry(OPCRelsPackage.genRelsFolderPath(this.version) + "/." + OPCRelsPackage.getRelsExtension());
            zos.putNextEntry(ze_rootRels);
            EPCGenericManager.marshal(OPCRelsPackage.JAXB_CONTEXT, null, rootRels, zos);
            zos.closeEntry();

            // ContentTypeFile
            ZipEntry ze_contentType = new ZipEntry(OPCContentType.genContentTypePath());
            zos.putNextEntry(ze_contentType);
            EPCGenericManager.marshal(OPCContentType.JAXB_CONTEXT, null, contentTypeFile, zos);
            zos.closeEntry();

            // Other files
            for(Map.Entry<String, InputStream> otherFile: otherFiles.entrySet()){
                ZipEntry ze_otherFile = new ZipEntry(otherFile.getKey());
                zos.putNextEntry(ze_otherFile);
                otherFile.getValue().transferTo(zos);
                zos.closeEntry();
            }
        }
    }

    public Object getLastModifiedObject(String uuid){
        List<Object> objects = energymlObjects.entrySet().stream()
                .filter(e -> uuid.equals(getUuidFromIdentifier(e.getKey())))
                .sorted((a, b) -> {
                    XMLGregorianCalendar a_lastModif = (XMLGregorianCalendar) ObjectController.getObjectAttributeValue(a, "Citation.LastUpdate");
                    XMLGregorianCalendar b_lastModif = (XMLGregorianCalendar) ObjectController.getObjectAttributeValue(b, "Citation.LastUpdate");
                    if (a_lastModif == null) {
                        return 1;
                    } else if (b_lastModif == null) {
                        return -1;
                    } else {
                        return b_lastModif.compare(a_lastModif);
                    }
                }).collect(Collectors.toList());
        if(objects.size() > 0){
            return objects.get(0);
        }
        return null;
    }

    public Object getObject(String uuid, String objectVersion){
        String identifier = getIdentifier(uuid, objectVersion);
        return getObjectByIdentifier(identifier);
    }

    @java.lang.Override
    public Object getObjectByIdentifier(String identifier) {
        if(energymlObjects.containsKey(identifier)){
            return energymlObjects.get(identifier);
        }
        return null;
    }

    @java.lang.Override
    public Object getObjectByUUID(String uuid) {
        return getObject(uuid, null);
    }

    @java.lang.Override
    public List<?> readExternalArray(Object energyml_array, Object root_obj, String pathInRoot) {
        return List.of();
    }

    public List<String> getAllVersions(String uuid){
        return energymlObjects.keySet().stream()
            .filter(o -> uuid.equals(getUuidFromIdentifier(o)))
                .map(EPCFile::getObjVersionFromIdentifier)
                .collect(Collectors.toList());
    }

    public Map<Object, Relationships> computeRelations(){
        Map<Object, Relationships> relations = new HashMap<>();

        Map<Object, List<Object>> sourceRels = new HashMap<>();
        Map<Object, List<Object>> destRels = new HashMap<>();

        for(Object o: this.energymlObjects.values()){
            destRels.put(o, ObjectController.findSubObjects(o, "DataObjectReference", true).stream()
                    .map(obj -> {
                        try{
                            Object rel = getObjectByIdentifier(getIdentifier(obj));
                            if(!sourceRels.containsKey(rel)){
                                sourceRels.put(rel, new ArrayList<>());
                            }
                            sourceRels.get(rel).add(o);
                            return rel;
                        }catch (Exception ignore){}
                        return null;
                    }).filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }

        for(Object o: this.energymlObjects.values()){
            Path o_parentFolder = Paths.get(EPCGenericManager.genPathInEPC(o, version)).getParent();
            Relationships rels = new Relationships();
            relations.put(o, rels);
            if (sourceRels.containsKey(o)){
                for(Object source: new HashSet<>(sourceRels.get(o))){
                    String s_uuid = getUuid(source);
                    String s_objVersion = getObjectVersion(source);
                    Relationship rel = new Relationship();
                    rel.setType(EPCRelsRelationshipType.SourceObject.getType());
                    rel.setId(URLEncoder.encode(s_uuid + (s_objVersion!= null ? "_" + s_objVersion : ""), Charset.defaultCharset()));
                    if(o_parentFolder != null) {
                        rel.setTarget(o_parentFolder.relativize(Paths.get(EPCGenericManager.genPathInEPC(source, version))).toString());
                    }else{
                        rel.setTarget(EPCGenericManager.genPathInEPC(source, version));
                    }
                    rels.getRelationship().add(rel);
                }
            }

            if (destRels.containsKey(o)){
                for(Object dest: new HashSet<>(destRels.get(o))){
                    String s_uuid = getUuid(dest);
                    String s_objVersion = getObjectVersion(dest);
                    Relationship rel = new Relationship();
                    rel.setType(EPCRelsRelationshipType.DestinationObject.getType());
                    rel.setId(URLEncoder.encode(s_uuid + (s_objVersion!= null ? "_" + s_objVersion : ""), Charset.defaultCharset()));
                    if(o_parentFolder != null) {
                        rel.setTarget(o_parentFolder.relativize(Paths.get(EPCGenericManager.genPathInEPC(dest, version))).toString());
                    }else{
                        rel.setTarget(EPCGenericManager.genPathInEPC(dest, version));
                    }
                    rels.getRelationship().add(rel);
                }
            }
            String oId = getIdentifier(o);
            if (additionalRels.containsKey(oId)){
                for(Relationship r: additionalRels.get(oId)){
                    rels.getRelationship().add(r);
                }
            }
        }
        return relations;
    }

    public static EPCFile read(String filePath, EPCPackageManager pkgManager) throws FileNotFoundException {
        EPCFile file = read(new FileInputStream(filePath), pkgManager);
        file.filePath = filePath;
        return file;
    }

    public static EPCFile read(InputStream input, EPCPackageManager pkgManager){
        EPCFile epc = new EPCFile(pkgManager);
        byte[] buffer = new byte[2048];

        Map<String, Object> mapPathToObject = new HashMap<>();
        Map<String, Relationships> mapPathToRelationships = new HashMap<>();
        boolean foundNamespaceFolder = false;

        try(ZipInputStream zip = new ZipInputStream(input)){
            String corePath = OPCCorePackage.genCorePath();
            ZipEntry entry = null;
            while ((entry = zip.getNextEntry()) != null){
                ByteArrayOutputStream entryBOS = new ByteArrayOutputStream();
                int len;
                while ((len = zip.read(buffer)) > 0) {
                    entryBOS.write(buffer, 0, len);
                }

                // Other files
                if(!entry.isDirectory() ) {
                    logger.debug("Reading " + entry.getName());
                    if (corePath.compareToIgnoreCase(entry.getName()) == 0) {
                        epc.coreProperties = (CoreProperties) OPCCorePackage.unmarshal(new ByteArrayInputStream(entryBOS.toByteArray()));
                    }else if (entry.getName().compareToIgnoreCase(OPCContentType.genContentTypePath()) == 0) {
                        // contentTypes = (Types) OPCContentType.unmarshal(new ByteArrayInputStream(entryBOS.toByteArray()));
                    }else if (entry.getName().endsWith(".xml")){
                        try{
                            Object o = pkgManager.unmarshal(entryBOS.toByteArray()).getValue();
                            String identifier = getIdentifier(o);
                            if(epc.energymlObjects.containsKey(identifier)){
                                logger.debug("Duplicate object found for identifier {}", identifier);
                            }
                            epc.energymlObjects.put(identifier, o);
                            mapPathToObject.put(entry.getName(), o);
                            if(entry.getName().toLowerCase().startsWith("namespace_")){
                                foundNamespaceFolder = true;
                            }
                        }catch (Exception e){logger.error("Error for {}: {}", entry.getName(), e);logger.error(e);};
                    }else if (entry.getName().endsWith("." + OPCRelsPackage.getRelsExtension())){
                        Relationships rels = (Relationships) OPCRelsPackage.unmarshal(new ByteArrayInputStream(entryBOS.toByteArray()));
                        String objPath = entry.getName()
                                .substring(0, entry.getName().lastIndexOf(".")) // removing rels extension
                                .replace(OPCRelsPackage.genRelsFolderPath(epc.version) + "/", "")
                                .replace(OPCRelsPackage.genRelsFolderPath(epc.version) + "\\", "");
                        mapPathToRelationships.put(objPath, rels);
                    }
                }
            }
        } catch (IOException | JAXBException e) {
            throw new RuntimeException(e);
        }

        for(Map.Entry<String, Relationships> rels: mapPathToRelationships.entrySet()) {
            if (mapPathToObject.containsKey(rels.getKey())){
                Object target = mapPathToObject.get(rels.getKey());
                String targId = getIdentifier(target);

                Pair<String, String> obj_pair = new Pair<>((String) ObjectController.getObjectAttributeValue(target, "uuid"),
                        getObjectVersion(target));
//                if(!epc.readRels.containsKey(obj_pair)){
//                    epc.readRels.put(obj_pair, new ArrayList<>());
//                }
//                epc.readRels.get(obj_pair).addAll(rels.getValue().getRelationship());

                for(Relationship r: rels.getValue().getRelationship()){
                    if(EPCRelsRelationshipType.DestinationObject.getType() .compareToIgnoreCase(r.getType()) != 0
                            && EPCRelsRelationshipType.SourceObject.getType().compareToIgnoreCase(r.getType()) != 0){
                        if(!epc.additionalRels.containsKey(targId)){
                            epc.additionalRels.put(targId, new ArrayList<>());
                        }
                        epc.additionalRels.get(targId).add(r);
                    }
                }
            }else{
                logger.error("Object " + rels.getKey() + " not found for rels");
                for(String k: mapPathToObject.keySet()){
                    logger.debug("\t" + k + " ==> " + mapPathToObject.containsKey(rels.getKey()) + "--" + mapPathToObject.get(rels.getKey()));
                }
            }
        }
        logger.debug("EPC " + epc.energymlObjects.size());

        epc.version = foundNamespaceFolder ? ExportVersion.EXPANDED : ExportVersion.CLASSIC;
        return epc;
    }

//    public String findNumericalDataLocation(String uuid, String objectVersion){
//        Object related = getObject(uuid, objectVersion);
//        if(related != null){
//            Relationships rels = computeRelations().get(related);
//            for(Relationship rel : rels.getRelationship()){
//                if(rel.getType().compareToIgnoreCase(EPCRelsRelationshipType.ExternalResource.label) == 0){
//                    if(rel.getTarget().toLowerCase().endsWith(".h5") || rel.getTarget().toLowerCase().endsWith(".hdf5")){
//                        return rel.getTarget();
//                    }
//                }
//            }
//            List<Object> references = new ArrayList<>();
//            references.addAll(ObjectController.findAllAttributesFromName(related, "EpcExternalPartReference", true, false));
//            references.addAll(ObjectController.findAllAttributesFromName(related, "HdfProxy", true, false));
//
//            for(Object ref: references){
//
//            }
//        }
//        return null;
//    }

    /* --------------------------------------------------- */

    public Map<String, Object> getEnergymlObjects() {
        return energymlObjects;
    }

    public Map<String, InputStream> getOtherFiles() {
        return otherFiles;
    }

    public Map<Object, List<Relationship>> getAdditionalRels() {
        return additionalRels;
    }

//    public Map<Pair<String, String>, List<Relationship>> getReadRels() {
//        return readRels;
//    }

    public ExportVersion getVersion() {
        return version;
    }

    public CoreProperties getCoreProperties() {
        return coreProperties;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getEpcFileFolder() {
        if (filePath != null && filePath.length() > 0) {
            return Paths.get(filePath).getParent().toString();
        }
        return null;
    }

    public static String getUuid(Object obj){
        return (String) ObjectController.getObjectAttributeValueRgx(obj, "uuid").get(0);
    }

    public static String getSchemaVersion(Object obj){
        return (String) ObjectController.getObjectAttributeValue(obj, "schemaVersion");
    }

    public static String getObjectVersion(Object obj){
        try {
            return (String) ObjectController.getObjectAttributeValueRgx(obj, "objectVersion|versionString").get(0);
        }catch (Exception e){
            logger.error("@getObjectVersion {}", e);
        }
        return null;
//        return (String) ObjectController.getObjectAttributeValue(obj, "objectVersion");
    }

    /**
     * Generates an objet identifier as : 'OBJ_UUID.OBJ_VERSION'
     * If the object version is None, the result is 'OBJ_UUID.'
     * @param obj
     * @return
     */
    public static String getIdentifier(Object obj){
        String objVersion = getObjectVersion(obj);
        if (objVersion == null) {
            objVersion = "";
        }
        String objUuid = getUuid(obj);
        return getIdentifier(objUuid, objVersion);
    }

    public static String getIdentifier(String uuid, String objVersion){
        return uuid + "." + (objVersion != null ? objVersion: "");
    }

    public static String getUuidFromIdentifier(String identifier){
        return identifier.substring(0, identifier.indexOf("."));
    }

    public static String getObjVersionFromIdentifier(String identifier){
        String version = identifier.substring(identifier.indexOf(".") + 1);
        if(!version.isEmpty())
            return version;
        return null;
    }
}
