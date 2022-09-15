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
import energyml.content_types.Types;
import jakarta.xml.bind.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class OPCContentType {
    public static JAXBContext JAXB_CONTEXT = ContextBuilder.createContext("energyml.content_types");

    public OPCContentType() { }

    public static Object unmarshal(String input) throws JAXBException {
        InputStream inputStr = new ByteArrayInputStream(input.getBytes());
        return unmarshal(inputStr);
    }

    public static Object unmarshal(InputStream input) throws JAXBException {

        Unmarshaller unmarshal = JAXB_CONTEXT.createUnmarshaller();

        Object o = unmarshal.unmarshal(input);
        if (o instanceof JAXBElement)
            return (Types) ((JAXBElement<?>)o).getValue();
        else return o;
    }

    public static String marshal(Types rels) throws JAXBException {
        Marshaller marshaller = JAXB_CONTEXT.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        marshaller.marshal(rels, bos);

        return bos.toString();
    }

    public static Types parseContentType(String input) throws JAXBException {
        return (Types) unmarshal(input);
    }

    public static Types parseContentType(InputStream input) throws JAXBException {
        return (Types) unmarshal(input);
    }

    public static String genContentTypePath() {
        return "[Content_Types].xml";
    }

}
