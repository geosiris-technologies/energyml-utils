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

import java.io.OutputStream;
import java.util.List;

public class GridedPointSetMesh extends AbstractMesh{

    public GridedPointSetMesh(Object energymlObject, Object crsObject, List<List<List<Double>>> point_list, String identifier) {
        this.energymlObject = energymlObject;
        this.crsObject = crsObject;
        this.pointList = point_list;
        this.identifier = identifier;
    }

    @Override
    public Long getNbPoints() {
        return this.pointList != null ? this.pointList.size() : 0L;
    }

    @Override
    public Long getNbEdge() {
        return 0L;
    }

    @Override
    public Long getNbFaces() {
        return 0L;
    }

    @Override
    public List<List<Long>> getEdgeIndices() {
        return List.of();
    }

    @Override
    public void exportOff(OutputStream out) throws NotImplementedException {
        throw new NotImplementedException();
    }

    @Override
    public void exportObj(OutputStream out) throws NotImplementedException {
        throw new NotImplementedException();
    }
}
