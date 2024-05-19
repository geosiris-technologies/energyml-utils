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
import com.geosiris.energyml.pkg.EPCFile;
import com.geosiris.energyml.utils.EnergymlWorkspace;
import com.geosiris.energyml.utils.ObjectController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.geosiris.energyml.utils.EnergymlWorkspaceHelper.*;
import static com.geosiris.energyml.utils.ObjectController.searchAttributeMatchingName;
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
            List<List<Double>> points = (List<List<Double>>) readArray(pointsObj, energymlObject, pointsPathInObj, workspace);

            Object crs = null;
            try {
                crs = getCrsObj(pointsObj, pointsPathInObj, energymlObject, workspace);
            } catch (ObjectNotFoundNotError ignore) {}
            if (points != null) {
                meshes.add(new PointSetMesh(
                        energymlObject,
                        crs,
                        points,
                        String.format("NodePatch num %d", patchIdx)
                ));
            }

            patchIdx++;
        }

        patchIdx = 0;
        pointsPathInObjMap = searchAttributeMatchingNameWithPath(energymlObject, "NodePatchGeometry.[\\d]+.Points");
        for(Map.Entry<String, Object> e: pointsPathInObjMap.entrySet()) {
            String pointsPathInObj = e.getKey();
            Object pointsObj = e.getValue();
            List<List<Double>> points = (List<List<Double>>) readArray(pointsObj, energymlObject, pointsPathInObj, workspace);

            Object crs = null;
            try {
                crs = getCrsObj(pointsObj, pointsPathInObj, energymlObject, workspace);
            } catch (ObjectNotFoundNotError ignore) {}
            if (points != null) {
                meshes.add(new PointSetMesh(
                        energymlObject,
                        crs,
                        points,
                        String.format("NodePatchGeometry num %d", patchIdx)
                ));
            }

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
                List<List<Double>> points = (List<List<Double>>) readArray(pointsObj, energymlObject, patchPathInObj + pointsPath, workspace);

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
                            .map(v -> Long.valueOf(String.valueOf(v ))).collect(Collectors.toList());
                    long idx = 0;
                    int polyIdx = 0;
                    pointIndices = new ArrayList<>();
                    for (Long nbNode : nodeCountsList) {
                        pointIndices.add(IntStream.range((int) idx, (int) idx + nbNode.intValue())
                                .boxed().map(Long::valueOf).collect(Collectors.toList()));
//                                .boxed().map(i->Long.valueOf(String.valueOf(i))).collect(Collectors.toList()));
                        if (closePoly != null && closePoly.size() > polyIdx && closePoly.get(polyIdx) != null) {
                            pointIndices.get(pointIndices.size() - 1).add(idx);
                        }
                        idx += nbNode;
                        polyIdx++;
                    }
                } catch (IndexOutOfBoundsException ignore) {
                }

                if (pointIndices == null || pointIndices.isEmpty()) {
                    pointIndices = new ArrayList<List<Long>>(Collections.singleton(new ArrayList<Long>(IntStream.range(0, points.size()).mapToObj(Long::valueOf).collect(Collectors.toList()))));
                }

                if (!points.isEmpty()) {
                    meshes.add(new PolylineSetMesh(
                            energymlObject,
                            crs,
                            points,
                            String.format("%s_patch%d", EPCFile.getIdentifier(energymlObject), patchIdx),
                            pointIndices
                    ));
                }

                patchIdx++;
            }
        }catch (Exception e){
            e.printStackTrace();
            throw e;
        }
        return meshes;
    }

    public static List<SurfaceMesh> readGrid2dRepresentation(Object energymlObject, EnergymlWorkspace workspace) throws NotImplementedException, InvocationTargetException, IllegalAccessException {
        return readGrid2dRepresentationHolable(energymlObject, workspace, false);
    }

    public static List<SurfaceMesh> readGrid2dRepresentationHolable(Object energymlObject, EnergymlWorkspace workspace, boolean keepHoles) throws NotImplementedException, InvocationTargetException, IllegalAccessException {
        List<SurfaceMesh> meshes = new ArrayList<>();
        try {
            logger.debug("keepHoles {}", keepHoles);
            Object crs = null;

            long patchIdx = 0;
            var patchPathInObjMap = searchAttributeMatchingNameWithPath(energymlObject, "Grid2dPatch");
            for (Map.Entry<String, Object> e : patchPathInObjMap.entrySet()) {
                String patchPath = e.getKey();
                Object patch = e.getValue();

                var reverseZValues = false;
                try {
                    crs = getCrsObj(patch, patchPath, energymlObject, workspace);
                    reverseZValues = isZReversed(crs);
                } catch (ObjectNotFoundNotError ignore) {
                }

                List<List<Double>> points = readGrid2dPatch(patch, energymlObject, patchPath, workspace);
//                logger.info("points : {}", points);
                var faCount = Long.valueOf(String.valueOf(searchAttributeMatchingName(patch, "FastestAxisCount").get(0)));
                var saCount = Long.valueOf(String.valueOf(searchAttributeMatchingName(patch, "SlowestAxisCount").get(0)));

                List<List<Double>> pointsNoNaN = new ArrayList<>();
                var indiceToFinalIndice = new HashMap<Long, Long>();
                if (keepHoles) {
                    for (var i = 0; i < points.size(); i++) {
                        var p = points.get(i);
                        if (Double.isNaN(p.get(2))) {
                            points.get(i).set(2, 0.);
//                            points.set(i, List.of(p.get(0), p.get(1), 0.));
                        } else if (reverseZValues) {
                            points.get(i).set(2, -(Double) p.get(2));
//                            points.set(i, List.of(p.get(0), p.get(1), -(Double) p.get(2)));
                        }
                    }
                } else {
                    for (var i = 0; i < points.size(); i++) {
                        List<Double> p = points.get(i);
                        if (p.size() > 2 && !Double.isNaN(p.get(2))) {
                            if (reverseZValues) {
                                points.get(i).set(2, -(Double) p.get(2));
//                                points.set(i, List.of(p.get(0), p.get(1), -(Double) p.get(2)));
                            }
                            indiceToFinalIndice.put((long) i, (long) pointsNoNaN.size());
                            pointsNoNaN.add(p);
                        }
                    }
                }

                List<List<Long>> indices = new ArrayList<>();
                while (saCount * faCount > points.size()) {
                    saCount--;
                    faCount--;
                }

                while (saCount * faCount < points.size()) {
                    saCount++;
                    faCount++;
                }

                for (int sa = 0; sa < saCount - 1; sa++) {
                    for (int fa = 0; fa < faCount - 1; fa++) {
                        var line = sa * faCount;
                        if (keepHoles) {
                            indices.add(new ArrayList<>(Arrays.asList(
                                    line + fa,
                                    line + fa + 1,
                                    line + faCount + fa + 1,
                                    line + faCount + fa
                            )
                            ));
                        } else if (
                                indiceToFinalIndice.containsKey(line + fa)
                                        && indiceToFinalIndice.containsKey(line + fa + 1)
                                        && indiceToFinalIndice.containsKey(line + faCount + fa + 1)
                                        && indiceToFinalIndice.containsKey(line + faCount + fa)
                        ) {
                            indices.add(new ArrayList<>(Arrays.asList(
                                    indiceToFinalIndice.get(line + fa),
                                    indiceToFinalIndice.get(line + fa + 1),
                                    indiceToFinalIndice.get(line + faCount + fa + 1),
                                    indiceToFinalIndice.get(line + faCount + fa)
                            )));
                        }
                    }
                }
//                logger.info("points : {}", points);

//                logger.info("{} saCount: {} faCount: {}", indices, saCount, faCount);
//                logger.info("saCount: {} faCount: {}", saCount, faCount);
//                logger.info("indiceToFinalIndice : {} ", indiceToFinalIndice);

                meshes.add(new SurfaceMesh(
                        energymlObject,
                        crs,
                        keepHoles ? points : pointsNoNaN,
                        String.format("%s_patch%d", EPCFile.getIdentifier(energymlObject), patchIdx),
                        indices
                ));

                patchIdx++;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return meshes;
    }

    public static List<SurfaceMesh> readTriangulatedSetRepresentation (Object energymlObject, EnergymlWorkspace
            workspace) throws NotImplementedException, InvocationTargetException, IllegalAccessException {
        List<SurfaceMesh> meshes = new ArrayList<>();
        try {
            Object crs = null;


            long pointOffset = 0;
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
                    pointList.addAll((List<List<Double>>) readArray(pointPath.getValue(), energymlObject, patchPath + pointPath.getKey(), workspace));
                }
                if(isZReversed(crs)){
                    pointList.forEach(l->l.set(2, -l.get(2)));
                }

                List<List<Long>> trianglesList_obj = new ArrayList<>();
                for (var trianglesPath : searchAttributeMatchingNameWithPath(patch, "Triangles").entrySet()) {
                    trianglesList_obj.addAll(((List<List<?>>) readArray(trianglesPath.getValue(), energymlObject, patchPath + trianglesPath.getKey(), workspace)).stream()
                            .map(l -> l.stream().map(v -> Long.valueOf(String.valueOf(v ))).collect(Collectors.toList())).collect(Collectors.toList()));
                }
//                logger.info("Triangles {} {}", patchPath, trianglesList_obj);
                final long finalPointOffset = pointOffset;
                List<List<Long>> trianglesList = trianglesList_obj.stream()
                        .map(l-> l.stream().map(ti -> ti - finalPointOffset)
                                .collect(Collectors.toList()))
                        .collect(Collectors.toList());

                meshes.add(new SurfaceMesh(
                        energymlObject,
                        crs,
                        pointList,
                        String.format("%s_patch%d", EPCFile.getIdentifier(energymlObject), patchIdx),
//                        new ArrayList<>(trianglesList.stream().collect(Collectors.groupingBy(s -> counter.getAndIncrement() / 3)).values())
                        trianglesList
                ));

                pointOffset += pointList.size();
                patchIdx++;
            }
        }catch (Exception e){
            e.printStackTrace();
        }

        return meshes;
    }
}