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

import java.util.List;

public abstract class AbstractMesh implements MeshExporter{
    protected Object energymlObject;
    protected Object crsObject;
    protected List<List<Double>> point_list;
    protected String identifier;

    public abstract Long getNbPoints();
    public abstract Long getNbEdge();
    public abstract Long getNbFaces();

    public String getIdentifier() {
        return identifier;
    }

    public abstract List<List<Long>> getEdgeIndices();
}
