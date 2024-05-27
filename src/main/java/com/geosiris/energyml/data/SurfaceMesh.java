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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SurfaceMesh extends AbstractMesh{
    private List<List<Long>> facesIndices;

    public SurfaceMesh(Object energymlObject, Object crsObject, List<List<Double>> point_list, String identifier, List<List<Long>> faces_indices) {
        this.energymlObject = energymlObject;
        this.crsObject = crsObject;
        this.pointList = point_list;
        this.identifier = identifier;
        this.facesIndices = faces_indices;
    }

    @Override
    public Long getNbPoints() {
        return this.pointList != null ? this.pointList.size() : 0L;
    }

    @Override
    public Long getNbEdge() {
        if(facesIndices == null || facesIndices.size() == 0){
            return 0L;
        }else {
            return facesIndices.stream()
                    .map(l -> ((long) l.size()) - 1L)
                    .reduce(Long::sum).get();
        }
    }

    @Override
    public Long getNbFaces() {
        return (long) facesIndices.size();
    }

    @Override
    public List<List<Long>> getEdgeIndices() {
        return facesIndices;
    }

    @Override
    public void exportOff(OutputStream out) throws NotImplementedException {
        throw new NotImplementedException();
    }

    @Override
    public void exportObj(OutputStream out) throws NotImplementedException {
        throw new NotImplementedException();
    }

    /**
     *
     * @param pointPart:
     * @param facePart:
     * @param points:
     * @param indices:
     * @param pointOffset:
     * @param colors: currently not supported
     * @param eltLetter: "l" for line and "f" for faces
     */
    public static void exportObjElt(
            OutputStream pointPart,
            OutputStream facePart,
            List<List<Double>> points,
            List<List<Long>> indices,
            Long pointOffset,
            List<List<Integer>> colors,
            String eltLetter
    ) throws IOException {
        Long offsetObj = 1L;  // OBJ point indices starts at 1 not 0
        for(var p: points) {
            if (!p.isEmpty())
                pointPart.write(String.format("v %s\n",
                        p.stream().map(String::valueOf).collect(Collectors.joining(" "))).getBytes(StandardCharsets.UTF_8)
                );
        }
        int cpt = 0;
        for(var face: indices) {
            if(!indices.isEmpty()) {
                facePart.write(
                        String.format("%s %s",
                                        eltLetter,
//                                        face.size(),
                                        face.stream().map(x -> String.valueOf(x + pointOffset + offsetObj)).collect(Collectors.joining(" ")))
                                .getBytes(
                                        StandardCharsets.UTF_8)
                );
                if (colors != null && colors.size() > cpt && colors.get(cpt) != null && !colors.get(cpt).isEmpty())
                {
                    pointPart.write(String.format("%s",
                        colors.get(cpt).stream().map(String::valueOf).collect(Collectors.joining(" "))).getBytes(StandardCharsets.UTF_8)
                    );
                }
                pointPart.write("\n".getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /**
     *
     * @param pointPart:
     * @param facePart:
     * @param points:
     * @param indices:
     * @param pointOffset:
     * @param colors: currently not supported
     */
    public static void exportOffElt(
            OutputStream pointPart,
            OutputStream facePart,
            List<List<Double>> points,
            List<List<Long>> indices,
            Long pointOffset,
            List<List<Integer>> colors
            ) throws IOException {
        Long offsetObj = 1L;  // OBJ point indices starts at 1 not 0
        for(var p: points) {
            if (!p.isEmpty())
                pointPart.write(String.format("%s\n",
                        p.stream().map(String::valueOf).collect(Collectors.joining(" "))).getBytes(StandardCharsets.UTF_8)
                );
        }
        // cpt = 0
        for(var face: indices) {
            if(!indices.isEmpty())
                facePart.write(
                        String.format("%s\n",
                                        face.stream().map(x -> String.valueOf(x + pointOffset + offsetObj)).collect(Collectors.joining(" ")))
                                .getBytes(
                                        StandardCharsets.UTF_8)
                );
        }
    }

    public static void exportObj(
            List<AbstractMesh> meshList,
            OutputStream out,
            String objName
            ) throws IOException {
        out.write("# Generated by energyml-utils a Geosiris python module\n\n".getBytes(StandardCharsets.UTF_8));
        if(objName != null){
            out.write(String.format("o %s\n\n", objName).getBytes(StandardCharsets.UTF_8));
        }

        Long pointOffset = 0L;
        for(var m: meshList){
            out.write(String.format("g %s\n\n", m.identifier).getBytes(StandardCharsets.UTF_8));
            exportObjElt(
                    out,
                    out,
                    (List<List<Double>>) m.pointList,
                    m.getEdgeIndices(),
                    pointOffset,
                    new ArrayList<>(),
                    m instanceof PolylineSetMesh ? "l" : "f"
            );
            pointOffset += m.pointList.size();
            out.write("\n".getBytes(StandardCharsets.UTF_8));
        }
    }
}
