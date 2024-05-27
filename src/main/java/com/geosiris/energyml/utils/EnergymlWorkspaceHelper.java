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

import com.geosiris.energyml.exception.NotImplementedException;
import com.geosiris.energyml.exception.ObjectNotFoundNotError;
import com.geosiris.energyml.pkg.EPCFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.geosiris.energyml.utils.ObjectController.*;


public class EnergymlWorkspaceHelper {
    public static Logger logger = LogManager.getLogger(EnergymlWorkspaceHelper.class);

    final static String[]_ARRAY_NAMES_ = new String[]{
            "BooleanArrayFromDiscretePropertyArray",
            "BooleanArrayFromIndexArray",
            "BooleanConstantArray",
            "BooleanExternalArray",
            "BooleanHdf5Array",
            "BooleanXmlArray",
            "CompoundExternalArray",
            "DasTimeArray",
            "DoubleConstantArray",
            "DoubleHdf5Array",
            "DoubleLatticeArray",
            "ExternalDataArray",
            "FloatingPointConstantArray",
            "FloatingPointExternalArray",
            "FloatingPointLatticeArray",
            "FloatingPointXmlArray",
            "IntegerArrayFromBooleanMaskArray",
            "IntegerConstantArray",
            "IntegerExternalArray",
            "IntegerHdf5Array",
            "IntegerLatticeArray",
            "IntegerRangeArray",
            "IntegerXmlArray",
            "JaggedArray",
            "ParametricLineArray",
            "ParametricLineFromRepresentationLatticeArray",
            "Point2DHdf5Array",
            "Point3DFromRepresentationLatticeArray",
            "Point3DHdf5Array",
            "Point3DLatticeArray",
            "Point3DParametricArray",
            "Point3DZvalueArray",
            "ResqmlJaggedArray",
            "StringConstantArray",
            "StringExternalArray",
            "StringHdf5Array",
            "StringXmlArray"
    };

    public static Object getCrsObj(
            Object contextObj,
            String pathInRoot,
            Object rootObj,
            EnergymlWorkspace workspace
    ) throws ObjectNotFoundNotError {
        if (workspace == null) {
            System.out.println("@get_crs_obj no Epc file given");
        } else {
            List<Object> crsList = searchAttributeMatchingName(contextObj, "\\.*Crs", Pattern.CASE_INSENSITIVE, "", false, true);
            if (!crsList.isEmpty()) {
                Object crs = workspace.getObjectByIdentifier(EPCFile.getIdentifier(crsList.get(0)));
                if (crs == null) {
                    crs = workspace.getObjectByUUID(EPCFile.getUuid(crsList.get(0)));
                }
                if (crs == null) {
                    logger.error("CRS " + crsList.get(0) + " not found (or not read correctly)");
                    throw new ObjectNotFoundNotError(EPCFile.getIdentifier(crsList.get(0)));
                }
                return crs;
            }

            if (!contextObj.equals(rootObj)) {
                String upperPath = pathInRoot.substring(0, pathInRoot.lastIndexOf("."));
                if (!upperPath.isEmpty()) {
                    return getCrsObj(
                            ObjectController.getObjectAttributeValue(rootObj, upperPath),
                            upperPath,
                            rootObj,
                            workspace
                    );
                }
            }
        }
        return null;
    }

    public static List<Double> pointAsArray(Object point) {
        return new ArrayList<>(Arrays.asList(
                Double.valueOf(String.valueOf(ObjectController.getObjectAttributeValue(point, "coordinate1"))),
                Double.valueOf(String.valueOf(ObjectController.getObjectAttributeValue(point, "coordinate2"))),
                Double.valueOf(String.valueOf(ObjectController.getObjectAttributeValue(point, "coordinate3")))
        ));
    }

    public static boolean isZReversed(Object crs) {
        boolean reverseZValues = false;
        if (crs != null) {
            List<Object> zIncreasingDownward = searchAttributeMatchingName(crs, "ZIncreasingDownward");
            if (!zIncreasingDownward.isEmpty()) {
                reverseZValues = (boolean) zIncreasingDownward.get(0);
            }
            if(!reverseZValues) {
                List<Object> crs_vertAxis = searchAttributeMatchingName(crs, "VerticalAxis.Direction");
                if (!crs_vertAxis.isEmpty()) {
                    reverseZValues = String.valueOf(crs_vertAxis.get(0)).equalsIgnoreCase("down");
                }
            }
            if(!reverseZValues) {
                List<Object> vertAxis = searchAttributeMatchingName(crs, "Direction");
                if (!vertAxis.isEmpty()) {
                    reverseZValues = String.valueOf(vertAxis.get(0)).equalsIgnoreCase("down");
                }
            }
        }

        return reverseZValues;
    }

    public static List<Double> prodNTab(Double val, List<Double> tab) {
        return tab.stream().map(x -> x * val).collect(Collectors.toList());
    }

    public static List<Double> sumLists(List<Double> l1, List<Double> l2) {
        int minLen = Math.min(l1.size(), l2.size());
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < minLen; i++) {
            result.add(l1.get(i) + l2.get(i));
        }
        result.addAll(l1.subList(minLen, l1.size()));
        result.addAll(l2.subList(minLen, l2.size()));
        return result;
    }

    public static String arrayNameMapping(String arrayTypeName) {
        arrayTypeName = arrayTypeName.replace("3D", "3d").replace("2D", "2d");
        if (arrayTypeName.endsWith("ConstantArray")) {
            return "ConstantArray";
        } else if (arrayTypeName.contains("External") || arrayTypeName.contains("Hdf5")) {
            return "ExternalArray";
        } else if (arrayTypeName.endsWith("XmlArray")) {
            return "XmlArray";
        } else if (arrayTypeName.contains("Jagged")) {
            return "JaggedArray";
        } else if (arrayTypeName.contains("Lattice")) {
            if (arrayTypeName.contains("Integer") || arrayTypeName.contains("Double")) {
                return "int_double_lattice_array";
            }
        }
        return arrayTypeName;
    }

    public static List<String> getSupportedArray() {
        List<String> supportedArray = new ArrayList<>();
        for (String arrayName : _ARRAY_NAMES_) {
            if (getArrayReaderFunction(arrayNameMapping(arrayName)) != null) {
                supportedArray.add(arrayName);
            }
        }
        return supportedArray;
    }

    public static List<String> getNotSupportedArray() {
        List<String> notSupportedArray = new ArrayList<>();
        for (String arrayName : _ARRAY_NAMES_) {
            if (getArrayReaderFunction(arrayNameMapping(arrayName)) == null) {
                notSupportedArray.add(arrayName);
            }
        }
        return notSupportedArray;
    }

    public static List<?> readExternalArray(Object energymlArray, Object rootObj, String pathInRoot, EnergymlWorkspace workspace) throws ObjectNotFoundNotError {
        return workspace.readExternalArray(energymlArray, rootObj, pathInRoot);
    }

    public static Method getArrayReaderFunction(String arrayTypeName) {
        try {
            return EnergymlWorkspaceHelper.class.getMethod("read" + arrayTypeName, Object.class, Object.class, String.class, EnergymlWorkspace.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static List<?> readArray(Object energymlArray) throws InvocationTargetException, IllegalAccessException, NotImplementedException {
        return readArray(energymlArray,null);
    }

    public static List<?> readArray(Object energymlArray, Object rootObj) throws InvocationTargetException, IllegalAccessException, NotImplementedException {
        return readArray(energymlArray, rootObj, "");
    }

    public static List<?> readArray(Object energymlArray, Object rootObj, String pathInRoot) throws InvocationTargetException, IllegalAccessException, NotImplementedException {
        return readArray(energymlArray, rootObj, pathInRoot, null);
    }

    public static List<?> readArray(Object energymlArray, Object rootObj, String pathInRoot, EnergymlWorkspace workspace) throws InvocationTargetException, IllegalAccessException, NotImplementedException {
        if (energymlArray instanceof List) {
            return (List<Object>) energymlArray;
        }
        String arrayTypeName = arrayNameMapping(energymlArray.getClass().getSimpleName());

        Method readerFunc = getArrayReaderFunction(arrayTypeName);
        if (readerFunc != null) {
            logger.debug("invoke {}", readerFunc.getName());
            return (List<?>) readerFunc.invoke(null, energymlArray, rootObj, pathInRoot, workspace);
        } else {
            logger.error("Type {} is not supported: function read not found", arrayTypeName, arrayTypeName);
            throw new NotImplementedException("Type " + arrayTypeName + " is not supported\n\t" + energymlArray + ": \n\tfunction read_" + arrayTypeName + " not found");
        }
    }

    public static List<Object> readConstantArray(Object energymlArray, Object rootObj, String pathInRoot, EnergymlWorkspace workspace) {
        Object value = ObjectController.getObjectAttributeValue(energymlArray, "value");
        Integer count = ((Number)ObjectController.getObjectAttributeValue(energymlArray, "count")).intValue();

        List<Object> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(value);
        }
        return result;
    }

    public static List<?> readXmlArray(Object energymlArray, Object rootObj, String pathInRoot, EnergymlWorkspace workspace) {
        return (List<?>) ObjectController.getObjectAttributeValue(energymlArray, "values");
    }

    public static List<Object> readJaggedArray(Object energymlArray, Object rootObj, String pathInRoot, EnergymlWorkspace workspace) throws InvocationTargetException, IllegalAccessException, NotImplementedException {
        List<?> elements = readArray(ObjectController.getObjectAttributeValue(energymlArray, "elements"), rootObj, pathInRoot + ".elements", workspace);
        List<?> cumulativeLength = readArray(readArray(ObjectController.getObjectAttributeValue(energymlArray, "cumulative_length")), rootObj, pathInRoot + ".cumulative_length", workspace);

        List<Object> result = new ArrayList<>();
        int previous = 0;
        for (Object cl : cumulativeLength) {
            result.add(elements.subList(previous, (int) cl));
            previous = (int) cl;
        }
        return result;
    }

    public static List<?> readPoint3dZValueArray(Object energymlArray, Object rootObj, String pathInRoot, EnergymlWorkspace workspace) throws NotImplementedException, InvocationTargetException, IllegalAccessException {
        try {
            Object supportingGeometry = ObjectController.getObjectAttributeValue(energymlArray, "SupportingGeometry");
            /*Object crs = null;
            try {
                crs = getCrsObj(energymlArray, pathInRoot, rootObj, workspace);
            } catch (ObjectNotFoundNotError e) {
                logger.error("No CRS found, not able to check zIncreasingDownward");
            }
            boolean zIncreasingDownward = isZReversed(crs);*/
            List<?> supGeomArray = readArray(
                    supportingGeometry,
                    rootObj,
                    pathInRoot + ".SupportingGeometry",
                    workspace
            );
            Object zvalues = ObjectController.getObjectAttributeValue(energymlArray, "ZValues");
            List<?> zvaluesArray = readArray(
                    zvalues,
                    rootObj,
                    pathInRoot + ".ZValues",
                    workspace
            );

            List<Double> zvalueFlat = null;
            if(zvaluesArray.get(0) instanceof Collection){
                zvalueFlat = ((List<List<?>>)zvaluesArray).stream().flatMap(List::stream).map(z-> z instanceof String ? Double.parseDouble((String) z) : ((Number)z).doubleValue()).collect(Collectors.toList());
            }else{
                zvalueFlat = zvaluesArray.stream().map(z-> z instanceof String ? Double.parseDouble((String) z) : ((Number)z).doubleValue()).collect(Collectors.toList());
            }
//            logger.info("supGeomArray : {}", (((List<?>)supGeomArray.get(0)).get(0)));
//            logger.info("zvaluesArrayNotFlat");

            if(!zvaluesArray.isEmpty()) {
                if(supGeomArray.get(0) instanceof Collection && !(((List<?>)supGeomArray.get(0)).get(0) instanceof Collection)){
                    // supGeom is List<List<Double>>, a list of points
//                    final int colSize = ((List<List<?>>)supGeomArray).get(0).size();
//                    for (int li = 0; li < supGeomArray.size(); li++) {
//                        for (int ci = 0; ci < colSize; ci++) {
//                            int idx = li * colSize + ci;
////                        if(zIncreasingDownward)
////                            idx = li * zvaluesArrayNotFlat.size() + ci;
////                            List<Double> p = (List<Double>) supGeomArray.get(idx);
//                            ((List<List<Double>>)supGeomArray).get(idx).set(2, zvaluesArrayNotFlat.get(li).get(ci));
//                        }
//                    }
                    for(int pi=0; pi<supGeomArray.size(); pi++){
                        ((List<List<Double>>)supGeomArray).get(pi).set(2, zvalueFlat.get(pi));
                    }
                }else {
                    final int colSize = ((List<List<?>>)supGeomArray).get(0).size();
                    for (int li = 0; li < supGeomArray.size(); li++) {
                        for (int ci = 0; ci < colSize; ci++) {
                            int idx = li * colSize + ci;
                            ((List<List<List<Double>>>)supGeomArray).get(li).get(ci).set(2, zvalueFlat.get(idx));
                        }
                    }
                }
            }
            return supGeomArray;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static List<?> readPoint3dFromRepresentationLatticeArray(
            Object energymlArray,
            Object rootObj,
            String pathInRoot,
            EnergymlWorkspace workspace
    ) throws InvocationTargetException, IllegalAccessException, NotImplementedException {
        try {
            String supportingRepIdentifier = EPCFile.getIdentifier(ObjectController.getObjectAttributeValue(energymlArray, "supportingRepresentation"));
            Object supportingRep = workspace.getObjectByIdentifier(supportingRepIdentifier);

            if (supportingRep.getClass().getSimpleName().toLowerCase().contains("grid2d")) {
                Map<String, Object> patchs = ObjectController.searchAttributeMatchingNameWithPath(supportingRep, "Grid2dPatch");
                String patchPath = patchs.keySet().stream().findFirst().get();
                Object patch = patchs.get(patchPath);
                return readGrid2dPatch(patch, supportingRep, patchPath, workspace);
            } else {
                throw new RuntimeException("Not supported type " + energymlArray.getClass() + " for object " + rootObj.getClass());
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static List<?> readGrid2dPatch(
            Object patch,
            Object grid2d,
            String pathInRoot,
            EnergymlWorkspace workspace
    ) throws InvocationTargetException, IllegalAccessException, NotImplementedException {
        Map<String, Object> pointsPathAndPointsObj = ObjectController.searchAttributeMatchingNameWithPath(patch, "Geometry.Points");
        String pointsPath = pointsPathAndPointsObj.keySet().stream().findFirst().get();
        Object pointsObj = pointsPathAndPointsObj.get(pointsPath);

        return readArray(pointsObj, grid2d, pathInRoot + pointsPath, workspace);
    }

    public static List<List<List<Double>>> readPoint3dLatticeArray(
            Object energymlArray,
            Object rootObj,
            String pathInRoot,
            EnergymlWorkspace workspace
    ) throws NotImplementedException, InvocationTargetException, IllegalAccessException {
        List<List<List<Double>>> result = new ArrayList<>();
        try {
            List<Double> origin = pointAsArray(ObjectController.getObjectAttributeValue(energymlArray, "origin"));
            List<Object> offset = (List<Object>) ObjectController.getObjectAttributeValue(energymlArray, "offset");
            if (offset.size() == 2) {
                Object slowest = offset.get(0);
                Object fastest = offset.get(1);

                List<Integer> crsSaCount = ((List<Number>) searchAttributeInUpperMatchingName(energymlArray, "SlowestAxisCount", rootObj, pathInRoot)).stream()
                        .map(n -> n.intValue())
                        .collect(Collectors.toList());
                List<Integer> crsFaCount = ((List<Number>) searchAttributeInUpperMatchingName(energymlArray, "FastestAxisCount", rootObj, pathInRoot)).stream()
                        .map(n -> n.intValue())
                        .collect(Collectors.toList());

                Object crs = null;
                try {
                    crs = getCrsObj(energymlArray, pathInRoot, rootObj, workspace);
                } catch (ObjectNotFoundNotError e) {
                    logger.error("No CRS found, not able to check zIncreasingDownward");
                }

                boolean zIncreasingDownward = isZReversed(crs);

                List<Double> slowestVec = pointAsArray(ObjectController.getObjectAttributeValue(slowest, "offset"));
//                List<Double> slowestSpacing = (List<Double>) readArray(ObjectController.getObjectAttributeValue(slowest, "spacing"));
                List<Double> slowestSpacing = (List<Double>) readArray(ObjectController.getObjectAttributeValue(zIncreasingDownward ? fastest: slowest, "spacing"));
                List<List<Double>> slowestTable = new ArrayList<>();
                for (Object spacing : slowestSpacing) {
                    slowestTable.add(prodNTab(Double.valueOf(String.valueOf(spacing)), slowestVec));
                }

                List<Double> fastestVec = pointAsArray(ObjectController.getObjectAttributeValue(fastest, "offset"));
//                List<Double> fastestSpacing = (List<Double>) readArray(ObjectController.getObjectAttributeValue(fastest, "spacing"));
                List<Double> fastestSpacing = (List<Double>) readArray(ObjectController.getObjectAttributeValue(zIncreasingDownward ? slowest: fastest, "spacing"));
                List<List<Double>> fastestTable = new ArrayList<>();
                for (Object spacing : fastestSpacing) {
                    fastestTable.add(prodNTab(Double.valueOf(String.valueOf(spacing)), fastestVec));
                }

                int slowestSize = slowestTable.size();
                int fastestSize = fastestTable.size();

                if (!crsSaCount.isEmpty() && !crsFaCount.isEmpty()) {
                    if ((crsSaCount.get(0) == fastestSize && crsFaCount.get(0) == slowestSize)
                            || (crsSaCount.get(0) == fastestSize - 1 && crsFaCount.get(0) == slowestSize - 1)) {
                        logger.debug("@readPoint3dLatticeArray reversing order");
                        List<List<Double>> tmpTable = slowestTable;
                        slowestTable = fastestTable;
                        fastestTable = tmpTable;

                        int tmpSize = slowestSize;
                        slowestSize = fastestSize;
                        fastestSize = tmpSize;
                    } else {
                        slowestSize = crsSaCount.get(0);
                        fastestSize = crsFaCount.get(0);
                    }
                }
                logger.debug("slowestSize {} fastestSize {}", slowestSize , fastestSize);
                logger.debug("fastestTable {} slowestTable {}", fastestTable , slowestTable);

                if(zIncreasingDownward){
//                    slowestTable = slowestTable.stream().map(l -> l.stream().map(i -> -i).collect(Collectors.toList())).collect(Collectors.toList());
//                    fastestTable = fastestTable.stream().map(l -> l.stream().map(i -> -i).collect(Collectors.toList())).collect(Collectors.toList());
//                    Collections.reverse(slowestTable);
//                    Collections.reverse(fastestTable);
//                    slowestTable.forEach(l -> Collections.swap(l, 0, 1));
//                    fastestTable.forEach(l -> Collections.swap(l, 0, 1));
                   /* List<List<Double>> tmp = slowestTable;
                    slowestTable = fastestTable;
                    fastestTable = tmp;
                    fastestSize = fastestTable.size();
                    slowestSize = slowestTable.size();*/
                }
                for (int i = 0; i < Math.min(slowestSize, slowestTable.size() + 1); i++) {
                    List<List<Double>> fastList = new ArrayList<>();
                    for (int j = 0; j < Math.min(fastestSize, fastestTable.size() + 1); j++) {
                        List<Double> previousValue = origin;
                        if (j > 0) {
                            if (i > 0) { // (i,j) on prends (i-1, j-1) comme previous
                                previousValue = result.get(i - 1).get(j - 1);
                            } else { // (0, j)
                                previousValue = fastList.get(j - 1);
                            }
                        } else {
                            if (i > 0) {
                                previousValue = result.get(i - 1).get(0);
                            }
                            // if (0,0) previous is origin
                        }
                        List<Double> current = previousValue;
                        if (j > 0) {
                            current = sumLists(current, fastestTable.get(j - 1));
                        }
                        if (i > 0) {
                            current = sumLists(current, slowestTable.get(i - 1));
                        }
                        fastList.add(current);
                    }
                    result.add(fastList);
                }
            } else {
                throw new RuntimeException(energymlArray.getClass() + " read with an offset of length " + offset.size() + " is not supported");
            }
        }catch (Exception e){
            e.printStackTrace();
        }

        return result;
    }

    public static List<String> getHdfReference(Object obj) {
        /**
         See :func:`get_hdf_reference_with_path`. Only the value is returned, not the dot path into the object
         :param obj:
         :return:
         **/
        return new ArrayList<>(getHdfReferenceWithPath(obj).values());
    }


    public static Map<String, String> getHdfReferenceWithPath(Object obj) {
        /**
         See :func:`search_attribute_matching_name_with_path`. Search an attribute with type matching regex
         "(PathInHdfFile|PathInExternalFile)".

         :param obj:
         :return: {Dot_Path_In_Obj: value, ...}
         **/
        return searchAttributeMatchingNameWithPath(
                obj,
                "(PathInHdfFile|PathInExternalFile)"
        ).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> (String) e.getValue()));
    }
}
