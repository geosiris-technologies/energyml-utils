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


import com.geosiris.energyml.utils.ContextBuilder;
import com.geosiris.energyml.utils.ExportVersion;
import com.geosiris.energyml.utils.ObjectController;
import com.geosiris.energyml.utils.Utils;
import energyml.relationships.Relationship;
import energyml.relationships.Relationships;
import jakarta.xml.bind.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

public class OPCRelsPackage {
    public static JAXBContext JAXB_CONTEXT = ContextBuilder.createContext("energyml.relationships");

    public OPCRelsPackage(){}

    public static Object unmarshal(String input) throws JAXBException {
        InputStream inputStr = new ByteArrayInputStream(input.getBytes());
        return unmarshal(inputStr);
    }

    public static Object unmarshal(InputStream input) throws JAXBException {

        Unmarshaller unmarshal = JAXB_CONTEXT.createUnmarshaller();

        Object o = unmarshal.unmarshal(input);
        if (o instanceof JAXBElement)
            return ((JAXBElement<?>)o).getValue();
        else return o;
    }

    private static String marshal(Relationship rel) throws JAXBException {
        Marshaller marshaller = JAXB_CONTEXT.createMarshaller();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        marshaller.marshal(rel, bos);
        return bos.toString();
    }

    public static String marshal(Relationships rels) throws JAXBException {
        Marshaller marshaller = JAXB_CONTEXT.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        marshaller.marshal(rels, bos);

        return bos.toString();
    }

    public static Relationships parseRels(String input) throws JAXBException {
        return (Relationships) unmarshal(input);
    }

    public static Relationships parseRels(InputStream input) throws JAXBException {
        return (Relationships) unmarshal(input);
    }

    public static List<Relationship> searchH5Relation(Relationships rels) {
        return rels.getRelationship().stream().filter((rel) -> rel.getId().toLowerCase().contains("hdf5file")).collect(Collectors.toList());
    }

    public static String genRelsFolderPath(ExportVersion exportVersion) {
        return "_rels";
    }

    public static String getRelsExtension() {
        return "rels";
    }

    public static String getRelsContentType() {
        return "application/vnd.openxmlformats-package.relationships+xml";
    }

    public static String genRelsPathInEPC(Object obj, ExportVersion version) {
        StringBuilder sb = new StringBuilder();

        String dirPath = Utils.genPathInEPC(obj, version);
        if (dirPath.contains("/") || dirPath.contains("\\")) {
            if (dirPath.contains("/")) {
                dirPath = dirPath.substring(0, dirPath.indexOf("/") + 1);
            }
            if (dirPath.contains("\\")) {
                dirPath = dirPath.substring(0, dirPath.indexOf("\\") + 1);
            }
        } else {
            dirPath = "";
        }
        sb.append(dirPath);
        sb.append(genRelsFolderPath(version)).append("/");
        if (obj != null) {
            sb.append(Utils.getObjectTypeForFilePath(obj));
            sb.append("_");
            sb.append(ObjectController.getObjectAttributeValue(obj, "uuid"));
            sb.append(".xml");
        }
        sb.append(".rels");
        return sb.toString();
    }
}
