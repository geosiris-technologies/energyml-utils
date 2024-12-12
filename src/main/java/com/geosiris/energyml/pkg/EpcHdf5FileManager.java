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

import com.geosiris.energyml.exception.ObjectNotFoundNotError;
import com.geosiris.energyml.utils.EnergymlWorkspace;
import com.geosiris.energyml.utils.ObjectController;
import energyml.relationships.Relationship;
import io.jhdf.HdfFile;
import io.jhdf.api.Dataset;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.geosiris.energyml.utils.EnergymlWorkspaceHelper.getHdfReference;
import static com.geosiris.energyml.utils.ObjectController.searchAttributeMatchingName;
import static com.geosiris.energyml.utils.Utils.rawArrayToList;

public class EpcHdf5FileManager implements EnergymlWorkspace {
    public static Logger logger = LogManager.getLogger(EpcHdf5FileManager.class);

    private final EPCFile epcFile;

    private EpcHdf5FileManager(EPCFile epc){
        super();
        this.epcFile = epc;
    }

    public static EpcHdf5FileManager readEpc(String epcFilePath) throws FileNotFoundException {
        return readEpc(epcFilePath, new EPCPackageManager());
    }

    public static EpcHdf5FileManager readEpc(String epcFilePath, EPCPackageManager manager) throws FileNotFoundException {
        return new EpcHdf5FileManager(EPCFile.read(epcFilePath, manager));
    }

    public static Number asNumber(Object o, Class<?> o_class){
        if(o_class == Integer.class || o_class == int.class){
            return (Integer) o;
        } else if (o_class == Long.class || o_class == long.class) {
            return (Long) o;
        } else if (o_class == Double.class || o_class == double.class ) {
            return (Double) o;
        } else if (o_class == Float.class || o_class == float.class) {
            return (Float) o;
        }
        return null;
    }

    public static List<?> getDatasetValues(String filePath, String pathInHdf5){
        logger.debug("{}, {}", filePath, pathInHdf5);
        try (HdfFile hdfFile = new HdfFile(Paths.get(filePath))) {
            return getDatasetValues(hdfFile, pathInHdf5);
        }catch (Exception e){
            logger.error(e);
            throw e;
        }
    }

    public static List<?> getDatasetValues(HdfFile hdfFile, String pathInHdf5){
        Dataset dataset = hdfFile.getDatasetByPath(pathInHdf5);
        // data will be a Java array with the dimensions of the HDF5 dataset
        Object data = dataset.getData();
        logger.debug("@getDatasetValues {} {} {}", data.getClass().getSimpleName(), data.getClass().isArray(), dataset.getDimensions());
        return rawArrayToList(data);
    }


    public String getExternalFilePathFromExternalPartRef(Object epr){
        List<Relationship> epr_rels = epcFile.getAdditionalRels().get(epr);
        if(epr_rels != null) {
            for (Relationship r : epr_rels) {
                if (r.getType().compareToIgnoreCase(EPCRelsRelationshipType.ExternalResource.getType()) == 0) {
                    return r.getTarget();
                }
            }
        }
        return (String) ObjectController.getObjectAttributeValue(epr, "Filename");
    }

    /**
     Maybe the path in the epc file objet was given as an absolute one : 'C:/my_file.h5'
     but if the epc has been moved (e.g. in 'D:/a_folder/') it will not work. Thus, the function
     energyml.utils.data.hdf.get_hdf5_path_from_external_path return the value from epc objet concatenate to the
     real epc folder path.
     With our example we will have : 'D:/a_folder/C:/my_file.h5'
     this function returns (following our example):
     [ 'C:/my_file.h5', 'D:/a_folder/my_file.h5', 'my_file.h5']
     @param valueInXml
     @param epc
     @return:
     **/
    public static List<String> getH5PathPossibilities(String valueInXml, EPCFile epc) {

        String epcFolder = epc.getEpcFileFolder();
        String hdf5PathRematch = (epcFolder!=null && epcFolder.length() > 0 ?
                epcFolder + '/' :
                ""
        ) + Path.of(valueInXml).getFileName().toString();
        String hdf5PathNoFolder = Path.of(valueInXml).getFileName().toString();

        return List.of(valueInXml, hdf5PathRematch, hdf5PathNoFolder);
    }

    public static List<String> getHdf5PathFromExternalPath(
            Object externalPathObj,
            String pathInRoot,
            Object rootObj,
            EPCFile epc
    ) {
        logger.debug("{} {}", externalPathObj, pathInRoot);
        if (externalPathObj instanceof String && pathInRoot != null) {
            // externalPathObj is maybe an attribute of an ExternalDataArrayPart, now search upper in the object
            String upperPath = pathInRoot.substring(0, pathInRoot.lastIndexOf("."));
            return getHdf5PathFromExternalPath(
                    ObjectController.getObjectAttributeValue(rootObj, upperPath),
                    upperPath,
                    rootObj,
                    epc
            );
        } else if (externalPathObj !=null && externalPathObj.getClass().getSimpleName().equals("ExternalDataArrayPart")) {
            String epcFolder = epc.getEpcFileFolder();
            List<String> h5Uri = searchAttributeMatchingName(externalPathObj, "uri").stream().map(String::valueOf).collect(Collectors.toList());
            if (!h5Uri.isEmpty()) {
                return getH5PathPossibilities(h5Uri.get(0), epc);
            }
        } else {
            List<Object> hdfProxyLst = searchAttributeMatchingName(externalPathObj, "HdfProxy");
            List<Object> extFileProxyLst = searchAttributeMatchingName(externalPathObj, "ExternalFileProxy");
            List<Object> extDataArrayPart = searchAttributeMatchingName(externalPathObj, "ExternalDataArrayPart");

            // resqml 2.0.1
            if (!hdfProxyLst.isEmpty()) {
                Object hdfProxy = hdfProxyLst;
                while (hdfProxy instanceof List) {
                    hdfProxy = ((List<?>) hdfProxy).get(0);
                }
                Object hdfProxyObj = epc.getObjectByIdentifier(EPCFile.getIdentifier(hdfProxy));
                if (hdfProxyObj != null) {
                    for (Relationship rel : epc.getAdditionalRels().get(EPCFile.getIdentifier(hdfProxyObj))) {
                        if (rel.getType().equals(EPCRelsRelationshipType.ExternalResource.getType())) {
                            return getH5PathPossibilities(rel.getTarget(), epc);
                        }
                    }
                }
            }

            // resqml 2.2dev3
            if (!extFileProxyLst.isEmpty()) {
                Object extFileProxy = extFileProxyLst;
                while (extFileProxy instanceof List) {
                    extFileProxy = ((List<?>) extFileProxy).get(0);
                }
                Object extPartRefObj = epc.getObjectByIdentifier(
                        EPCFile.getIdentifier(
                                ObjectController.getObjectAttributeValueRgx(extFileProxy, "epcExternalPartReference").get(0)
                        )
                );
                return getH5PathPossibilities((String) ObjectController.getObjectAttributeValue(extPartRefObj, "Filename"), epc);
            }

            // resqml 2.2
            if(!extDataArrayPart.isEmpty()){
                logger.debug(extDataArrayPart);
                List<String> result = new ArrayList<>();
                for(var edap: extDataArrayPart){
                    result.addAll(getHdf5PathFromExternalPath(
                            edap,
                            null,
                            rootObj,
                            epc
                    ));
                }
                return result;
            }

            // Nothing found here, try with epc name
            String epcPath = epc.getFilePath();
            return List.of(epcPath.substring(0, epcPath.length()-4) + ".h5");
        }

        return new ArrayList<>();
    }

    public EPCFile getEpcFile() {
        return epcFile;
    }

    @Override
    public Object getObject(String uuid, String objectVersion) {
        return this.epcFile.getObject(uuid, objectVersion);
    }

    @Override
    public Object getObjectByIdentifier(String identifier) {
        return this.epcFile.getObjectByIdentifier(identifier);
    }

    @Override
    public Object getObjectByUUID(String uuid) {
        return this.epcFile.getObjectByUUID(uuid);
    }

    @Override
    public List<?> readExternalArray(Object energyml_array, Object energymlObject, String pathInHDF) throws ObjectNotFoundNotError {
        List<String> h5filePaths;
        try {
            h5filePaths = getHdf5PathFromExternalPath(energyml_array, null, energymlObject, this.epcFile);
        }catch (Exception e){
            logger.error(e);
            throw e;
        }
        String pathInExternal = getHdfReference(energyml_array).get(0);
        logger.debug(h5filePaths);
        List<?> resultArray = null;
        assert h5filePaths != null;
        for(String hdf5Path: h5filePaths) {
            if(Files.exists(Path.of(hdf5Path))) {
                try {
                    resultArray = getDatasetValues(hdf5Path, pathInExternal);
                    break;  // if succeed, not try with other paths
                } catch (Exception ignore) {
                    logger.error(ignore);
                }
            }
        }

        if (resultArray == null)
            throw new ObjectNotFoundNotError(String.format("Failed to read h5 file. Paths tried : %s : %s", h5filePaths, pathInExternal));
        return resultArray;
    }
}
