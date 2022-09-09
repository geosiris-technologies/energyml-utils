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

public enum EPCRelsRelationshipType {

    /**
     * The object in Target is the destination of the relationship.
     **/
    DestinationObject("destinationObject"),
    /**
     * The current object is the source in the relationship with the target object.
     **/
    SourceObject("sourceObject"),
    /**
     * The target object is a proxy object for an external data object (HDF5 file).
     **/
    MlToExternalPartProxy("mlToExternalPartProxy"),
    /**
     * The current object is used as a proxy object by the target object.
     **/
    ExternalPartProxyToMl("externalPartProxyToMl"),
    /**
     * The target is a resource outside of the EPC package. Note that TargetMode should be “External” for this relationship.
     **/
    ExternalResource("externalResource"),
    /**
     * The object in Target is a media representation for the current object. As a guideline, media files should be stored in a "media" folder in the root of the package.
     **/
    DestinationMedia("destinationMedia"),
    /**
     * The current object is a media representation for the object in Target.
     **/
    SourceMedia("sourceMedia"),
    /**
     * The target is part of a larger data object that has been chunked into several smaller files
     **/
    ChunkedPart("chunkedPart"),

    /**
     * /!\ not in the norm
     */
    ExtendedCoreProperties("extended-core-properties");

    public final String label;

    private EPCRelsRelationshipType(String label) {
        this.label = label;
    }

    public String getType(){
        switch (this){
		case ExtendedCoreProperties:
			return "http://schemas.f2i-consulting.com/package/2014/relationships/" + label;
		case ChunkedPart:
		case DestinationMedia:
		case DestinationObject:
		case ExternalPartProxyToMl:
		case ExternalResource:
		case MlToExternalPartProxy:
		case SourceMedia:
		case SourceObject:
		default:
			return "http://schemas.energistics.org/package/2012/relationships/" + label;
		}
    }
}
