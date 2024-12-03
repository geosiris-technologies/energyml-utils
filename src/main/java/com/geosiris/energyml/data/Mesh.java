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
package com.geosiris.energyml.data;

import com.geosiris.energyml.exception.NotImplementedException;
import com.geosiris.energyml.exception.ObjectNotFoundNotError;
import com.geosiris.energyml.utils.EPCGenericManager;
import com.geosiris.energyml.utils.EnergymlWorkspace;
import com.geosiris.energyml.utils.EnergymlWorkspaceHelper;
import com.geosiris.energyml.utils.ObjectController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.geosiris.energyml.pkg.EPCFile.getIdentifier;
import static com.geosiris.energyml.utils.EnergymlWorkspaceHelper.*;
import static com.geosiris.energyml.utils.ObjectController.searchAttributeMatchingNameWithPath;

public class Mesh {
    public static Logger logger = LogManager.getLogger(Mesh.class);


    public static Method getMeshReaderFunction(String meshTypeName) {
        for (Method m : Mesh.class.getMethods()) {
            if (m.getName().equals("read" + meshTypeName)) {
                return m;
            }
        }
        return null;
    }

    public static String meshNameMapping(String arrayTypeName) {
        arrayTypeName = arrayTypeName.replace("3D", "3d").replace("2D", "2d");
        arrayTypeName = arrayTypeName.replaceAll("^[Oo]bj([A-Z])", "$1");
        arrayTypeName = arrayTypeName.replaceAll("(Polyline|Point)Set", "$1");
        return arrayTypeName;
    }

    public static List<AbstractMesh> readMeshObject(Object energymlObject, EnergymlWorkspace workspace) throws InvocationTargetException, IllegalAccessException {
        if (energymlObject instanceof List) {
            return (List<AbstractMesh>) energymlObject;
        }
        String arrayTypeName = meshNameMapping(energymlObject.getClass().getSimpleName());

        Method readerFunc = getMeshReaderFunction(arrayTypeName);
        if (readerFunc != null) {
            logger.debug("{}", readerFunc.getName());
            readerFunc.setAccessible(true);
            return (List<AbstractMesh>) readerFunc.invoke(null, energymlObject, workspace);
        } else {
            logger.error("Type %s is not supported: function read_%s not found%n", arrayTypeName, arrayTypeName);
            throw new RuntimeException(String.format("Type %s is not supported%n\t%s: %n\tfunction read_%s not found", arrayTypeName, energymlObject, arrayTypeName));
        }
    }

    public static List<PointSetMesh> readPointRepresentation(Object energymlObject, EnergymlWorkspace workspace) throws NotImplementedException, InvocationTargetException, IllegalAccessException {
        List<PointSetMesh> meshes = new ArrayList<>();

        long patchIdx = 0;
        Map<String, Object> pointsPathInObjMap = searchAttributeMatchingNameWithPath(energymlObject, "NodePatch.[\\d]+.Geometry.Points");
        for(Map.Entry<String, Object> e: pointsPathInObjMap.entrySet()) {
            String pointsPathInObj = e.getKey();
            Object pointsObj = e.getValue();

            List<List<Double>> points = new ArrayList<>();
            List<?> pl = readArray(pointsObj, energymlObject, pointsPathInObj, workspace);
            if(!pl.isEmpty()) {
                if (pl.get(0) instanceof Collection) {
                    points.addAll(((List<List<?>>) pl).stream()
                            .map(l -> l.stream().map(v -> ((Number) v).doubleValue()).collect(Collectors.toList())).collect(Collectors.toList()));
                } else { // pl given flat
                    for (int i = 0; i < pl.size() - 2; i+=3) {
                        points.add(new ArrayList<>(List.of(
                                ((Number) pl.get(i)).doubleValue(),
                                ((Number) pl.get(i + 1)).doubleValue(),
                                ((Number) pl.get(i + 2)).doubleValue()
                        )));
                    }
                }
            }else{
                logger.info("Size is 0 for {}", pointsPathInObj);
            }

            Object crs = null;
            try {
                crs = getCrsObj(pointsObj, pointsPathInObj, energymlObject, workspace);
            } catch (ObjectNotFoundNotError ignore) {}

            if(isZReversed(crs)){
                points.forEach(l->l.set(2, -l.get(2)));
            }

            meshes.add(new PointSetMesh(
                    energymlObject,
                    crs,
                    points,
                    String.format("NodePatch num %d", patchIdx)
            ));

            patchIdx++;
        }

        patchIdx = 0;
        pointsPathInObjMap = searchAttributeMatchingNameWithPath(energymlObject, "NodePatchGeometry.[\\d]+.Points");
        for(Map.Entry<String, Object> e: pointsPathInObjMap.entrySet()) {
            String pointsPathInObj = e.getKey();
            Object pointsObj = e.getValue();
            List<List<Double>> points = new ArrayList<>();
            List<?> pl = readArray(pointsObj, energymlObject, pointsPathInObj, workspace);
            if(!pl.isEmpty()) {
                if (pl.get(0) instanceof Collection) {
                    points.addAll(((List<List<?>>) pl).stream()
                            .map(l -> l.stream().map(v -> ((Number) v).doubleValue()).collect(Collectors.toList())).collect(Collectors.toList()));
                } else { // pl given flat
                    for (int i = 0; i < pl.size() - 2; i+=3) {
                        points.add(new ArrayList<>(List.of(
                                ((Number) pl.get(i)).doubleValue(),
                                ((Number) pl.get(i + 1)).doubleValue(),
                                ((Number) pl.get(i + 2)).doubleValue()
                        )));
                    }
                }
            }else{
                logger.info("Size is 0 for {}", pointsPathInObj);
            }

            Object crs = null;
            try {
                crs = getCrsObj(pointsObj, pointsPathInObj, energymlObject, workspace);
            } catch (ObjectNotFoundNotError ignore) {}

            if(isZReversed(crs)){
                points.forEach(l->l.set(2, -l.get(2)));
            }

            meshes.add(new PointSetMesh(
                    energymlObject,
                    crs,
                    points,
                    String.format("NodePatchGeometry num %d", patchIdx)
            ));

            patchIdx++;
        }

        return meshes;
    }

    public static List<PolylineSetMesh> readPolylineRepresentation(Object energymlObject, EnergymlWorkspace workspace) throws NotImplementedException, InvocationTargetException, IllegalAccessException {
        List<PolylineSetMesh> meshes = new ArrayList<>();
        try {
            long patchIdx = 0;
            var patchPathInObjMap = searchAttributeMatchingNameWithPath(energymlObject, "[Node|Line]Patch");
            for (Map.Entry<String, Object> e : patchPathInObjMap.entrySet()) {
                String patchPathInObj = e.getKey();
                Object patch = e.getValue();
                Map.Entry<String, Object> entry = searchAttributeMatchingNameWithPath(patch, "Geometry.Points").entrySet().iterator().next();
                String pointsPath = entry.getKey();
                Object pointsObj = entry.getValue();

                List<List<Double>> points = new ArrayList<>();
                List<?> pl = EnergymlWorkspaceHelper.readArray(pointsObj, energymlObject, patchPathInObj + pointsPath, workspace);
                if(!pl.isEmpty()) {
                    if (pl.get(0) instanceof Collection) {
                        points.addAll(((List<List<?>>) pl).stream()
                                .map(l -> l.stream().map(v -> ((Number) v).doubleValue()).collect(Collectors.toList())).collect(Collectors.toList()));
                    } else { // pl given flat
                        for (int i = 0; i < pl.size() - 2; i+=3) {
                            points.add(new ArrayList<>(List.of(
                                    ((Number) pl.get(i)).doubleValue(),
                                    ((Number) pl.get(i + 1)).doubleValue(),
                                    ((Number) pl.get(i + 2)).doubleValue()
                            )));
                        }
                    }
                }else{
                    logger.info("Size is 0 for {}", patch);
                }

                Object crs = null;
                try {
                    crs = getCrsObj(pointsObj, patchPathInObj + pointsPath, energymlObject, workspace);
                } catch (ObjectNotFoundNotError ignore) {
                }

                Map.Entry<String, Object> closedPolyEntry = searchAttributeMatchingNameWithPath(patch, "ClosedPolylines").entrySet().iterator().next();
                String closePolyPath = closedPolyEntry.getKey();
                Object closePolyObj = closedPolyEntry.getValue();
                var closePoly = readArray(closePolyObj, energymlObject, patchPathInObj + closePolyPath, workspace);

                List<List<Long>> pointIndices = null;
                try {
                    Map.Entry<String, Object> nodeCountPerPolyPathInObjEntry = searchAttributeMatchingNameWithPath(patch, "NodeCountPerPolyline").entrySet().iterator().next();
                    String nodeCountPerPolyPathInObj = nodeCountPerPolyPathInObjEntry.getKey();
                    Object nodeCountPerPoly = nodeCountPerPolyPathInObjEntry.getValue();
                    List<Long> nodeCountsList = readArray(nodeCountPerPoly, energymlObject, patchPathInObj + nodeCountPerPolyPathInObj, workspace).stream()
                            .map(v -> ((Number)v).longValue()).collect(Collectors.toList());
                    long idx = 0;
                    int polyIdx = 0;
                    pointIndices = new ArrayList<>();
                    for (Long nbNode : nodeCountsList) {
                        pointIndices.add(IntStream.range((int) idx, (int) idx + nbNode.intValue())
                                .boxed().map(Long::valueOf).collect(Collectors.toList()));
                        if (closePoly != null && closePoly.size() > polyIdx && closePoly.get(polyIdx) != null) {
                            pointIndices.get(pointIndices.size() - 1).add(idx);
                        }
                        idx += nbNode;
                        polyIdx++;
                    }
                } catch (IndexOutOfBoundsException ignore) {
                }

                if(isZReversed(crs)){
                    points.forEach(l->l.set(2, -l.get(2)));
                }

                if (pointIndices == null || pointIndices.isEmpty()) {
                    pointIndices = new ArrayList<>(Collections.singleton(new ArrayList<>(IntStream.range(0, points.size()).mapToObj(Long::valueOf).collect(Collectors.toList()))));
                }

                if (!points.isEmpty()) {
                    meshes.add(new PolylineSetMesh(
                            energymlObject,
                            crs,
                            points,
                            String.format("%s_patch%d", getIdentifier(energymlObject), patchIdx),
                            pointIndices
                    ));
                }

                patchIdx++;
            }
        }catch (Exception e){
            logger.error(e);
            throw e;
        }
        return meshes;
    }

    public static List<SurfaceMesh> readGrid2dRepresentation(Object energymlObject, EnergymlWorkspace workspace) {
        return readGrid2dRepresentationHolable(energymlObject, workspace, false);
    }

    public static List<SurfaceMesh> readGrid2dRepresentationHolable(Object energymlObject, EnergymlWorkspace workspace, boolean keepHoles) {
        List<SurfaceMesh> meshes = new ArrayList<>();
        try {
            logger.debug("keepHoles {}", keepHoles);
            Object crs = null;

            long patchIdx = 0;
            var patchPathInObjMap = searchAttributeMatchingNameWithPath(energymlObject, "Grid2dPatch");
            if(EPCGenericManager.getObjectQualifiedType(energymlObject).contains("resqml22.")){
                patchPathInObjMap.put("", energymlObject);  // Resqml 22
            }
            for (Map.Entry<String, Object> e : patchPathInObjMap.entrySet()) {
                String patchPath = e.getKey();
                Object patch = e.getValue();

                boolean reverseZValues = false;
                try {
                    crs = getCrsObj(patch, patchPath, energymlObject, workspace);
                    reverseZValues = isZReversed(crs);
                } catch (ObjectNotFoundNotError ignore) {
                }

                List<List<List<Double>>> points = (List<List<List<Double>>>) readGrid2dPatch(patch, energymlObject, patchPath, workspace);

                boolean finalReverseZValues = reverseZValues;
                points.forEach(l -> l.forEach(p -> {
                    if(Double.isNaN(p.get(2))) {
                        if (keepHoles)
                            p.set(2, 0.);
                    }else if(finalReverseZValues){
                        p.set(2, -p.get(2));
                    }
                }));

                List<List<Double>> pointsNoNaN = new ArrayList<>();
                var indiceToFinalIndice = new HashMap<Long, Long>();

                List<List<Long>> indices = new ArrayList<>();
                if (!keepHoles) {
                    for (var i = 0; i < points.size(); i++) {
                        long lineSize = points.get(i).size();
                        for (var j = 0; j < lineSize; j++) {
                            List<Double> p = points.get(i).get(j);
                            if(!Double.isNaN(p.get(2))) {
                                indiceToFinalIndice.put((long) i * lineSize + j, (long) pointsNoNaN.size());
                                pointsNoNaN.add(p);
                            }
                        }
                    }
                }

                for (long i = 0; i < points.size() - 1; i++) {
                    long lineSize = points.get((int) i).size();
                    for (long j = 0; j < lineSize - 1; j++) {
                        List<Long> faceIdx = new ArrayList<>(List.of(
                                i * lineSize + j,
                                (i+1) * lineSize + j,
                                (i+1) * lineSize + j + 1,
                                i * lineSize + j + 1
                        ));
                        if(!keepHoles){
                            faceIdx = faceIdx.stream().map(indiceToFinalIndice::get).collect(Collectors.toList());
                        }
                        if(!faceIdx.contains(null))
                            indices.add(faceIdx);
                    }
                }

                meshes.add(new SurfaceMesh(
                        energymlObject,
                        crs,
                        keepHoles ? points.stream().flatMap(List::stream).collect(Collectors.toList()) : pointsNoNaN,
                        String.format("%s_patch%d", getIdentifier(energymlObject), patchIdx),
                        indices
                ));

                patchIdx++;
            }
        }catch (Exception e){
            logger.error(e);
        }
        return meshes;
    }

    public static List<GridedPointSetMesh> readGrid2dRepresentationPoints(Object energymlObject, EnergymlWorkspace workspace){
        List<GridedPointSetMesh> meshes = new ArrayList<>();
        try {
            Object crs = null;

            long patchIdx = 0;
            var patchPathInObjMap = searchAttributeMatchingNameWithPath(energymlObject, "Grid2dPatch");
            if(EPCGenericManager.getObjectQualifiedType(energymlObject).contains("resqml22.")){
                patchPathInObjMap.put("", energymlObject);  // Resqml 22
            }
            for (Map.Entry<String, Object> e : patchPathInObjMap.entrySet()) {
                String patchPath = e.getKey();
                Object patch = e.getValue();

                boolean reverseZValues = false;
                try {
                    crs = getCrsObj(patch, patchPath, energymlObject, workspace);
                    reverseZValues = isZReversed(crs);
                } catch (ObjectNotFoundNotError ignore) {
                }

                List<List<List<Double>>> points = (List<List<List<Double>>>) readGrid2dPatch(patch, energymlObject, patchPath, workspace);

                boolean finalReverseZValues = reverseZValues;
                points.forEach(l -> l.forEach(p -> {
                    if(!Double.isNaN(p.get(2)) && finalReverseZValues){
                        p.set(2, -p.get(2));
                    }
                }));

                meshes.add(new GridedPointSetMesh(
                        energymlObject,
                        crs,
                        points,
                        String.format("%s_patch%d", getIdentifier(energymlObject), patchIdx)
                ));

                patchIdx++;
            }
        }catch (Exception e){
            logger.error(e);
        }
        return meshes;
    }

    public static List<SurfaceMesh> readTriangulatedSetRepresentation(Object energymlObject, EnergymlWorkspace
            workspace) {
        List<SurfaceMesh> meshes = new ArrayList<>();
        try {
            Object crs = null;

            // long pointOffset = 0;
            long patchIdx = 0;
            var patchPathInObjMap = searchAttributeMatchingNameWithPath(energymlObject, "TrianglePatch.\\d+", false);

            for (Map.Entry<String, Object> e : patchPathInObjMap.entrySet().stream().sorted(
                    (ea, eb) -> {
                        String indexa = String.valueOf(ObjectController.getObjectAttributeValue(ea.getValue(), "PatchIndex"));
                        String indexb = String.valueOf(ObjectController.getObjectAttributeValue(eb.getValue(), "PatchIndex"));
                        if(indexa==null){
                            return -1;
                        } else if (indexb==null) {
                            return 1;
                        }else{
                            try{
                                return Integer.compare(Integer.parseInt(indexa), Integer.parseInt(indexb));
                            }catch (Exception ignore){}
                        }
                        return -1;
                    }
            ).collect(Collectors.toList())) {
                String patchPath = e.getKey();
                Object patch = e.getValue();
                try {
                    crs = getCrsObj(patch, patchPath, energymlObject, workspace);
                } catch (ObjectNotFoundNotError ignore) {
                }

                List<List<Double>> pointList = new ArrayList<>();
                for (var pointPath : searchAttributeMatchingNameWithPath(patch, "Geometry.Points").entrySet()) {
                    List<?> pl = readArray(pointPath.getValue(), energymlObject, patchPath + pointPath.getKey(), workspace);
                    if(!pl.isEmpty()) {
                        if (pl.get(0) instanceof Collection) {
                            pointList.addAll(((List<List<?>>) pl).stream()
                                    .map(l -> l.stream().map(v -> ((Number) v).doubleValue()).collect(Collectors.toList())).collect(Collectors.toList()));
                        } else { // pl given flat
                            for (int i = 0; i < pl.size() - 2; i+=3) {
                                pointList.add(new ArrayList<>(List.of(
                                        ((Number) pl.get(i)).doubleValue(),
                                        ((Number) pl.get(i + 1)).doubleValue(),
                                        ((Number) pl.get(i + 2)).doubleValue()
                                )));
                            }
                        }
                    }else{
                        logger.info("Size is 0 for {}", patch);
                    }
                }
                if(isZReversed(crs)){
                    pointList.forEach(l->l.set(2, -l.get(2)));
                }

                List<List<Long>> trianglesList_obj = new ArrayList<>();
                for (var trianglesPath : searchAttributeMatchingNameWithPath(patch, "Triangles").entrySet()) {
                    List<?> indices = readArray(trianglesPath.getValue(), energymlObject, patchPath + trianglesPath.getKey(), workspace);
                    if(indices.get(0) instanceof Collection){
                        trianglesList_obj.addAll(((List<List<?>>)indices).stream()
                                .map(l -> l.stream().map(v -> ((Number)v).longValue()).collect(Collectors.toList())).collect(Collectors.toList()));
                    }else{ // indices given flat
                        for(int i=0; i<indices.size()-2; i+=3){
                            trianglesList_obj.add(new ArrayList<>(List.of(
                                    ((Number)indices.get(i)).longValue(),
                                    ((Number)indices.get(i+1)).longValue(),
                                    ((Number)indices.get(i+2)).longValue()
                            )));
                        }
                    }
                }
//                logger.info("Triangles {} {}", patchPath, trianglesList_obj);
//                final long finalPointOffset = pointOffset;
//                List<List<Long>> trianglesList = trianglesList_obj.stream()
//                        .map(l-> l.stream().map(ti -> ti - finalPointOffset)
//                                .collect(Collectors.toList()))
//                        .collect(Collectors.toList());

                meshes.add(new SurfaceMesh(
                        energymlObject,
                        crs,
                        pointList,
                        String.format("%s_patch%d", getIdentifier(energymlObject), patchIdx),
//                        new ArrayList<>(trianglesList.stream().collect(Collectors.groupingBy(s -> counter.getAndIncrement() / 3)).values())
//                        trianglesList
                        trianglesList_obj
                ));

                // pointOffset += pointList.size();
                patchIdx++;
            }
        }catch (Exception e){
            logger.error(e);
        }

        return meshes;
    }

}