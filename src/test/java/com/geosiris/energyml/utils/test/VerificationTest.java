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
package com.geosiris.energyml.utils.test;

import com.geosiris.energyml.pkg.EPCPackageManager;
import com.geosiris.energyml.utils.Utils;
import com.geosiris.energyml.utils.Verifications;

import energyml.common2_3.Activity;
import energyml.common2_3.Citation;
import energyml.common2_3.DataObjectReference;
import energyml.common2_3.DoubleQuantityParameter;
import energyml.common2_3.IntegerConstantArray;
import energyml.resqml2_2.PointGeometry;
import energyml.resqml2_2.SurfaceRole;
import energyml.resqml2_2.TrianglePatch;
import energyml.resqml2_2.TriangulatedSetRepresentation;
import jakarta.xml.bind.JAXBException;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

public class VerificationTest {

    @Test
    void test_attributeIsMendatory(){
        assert Verifications.attributeIsMendatory(TriangulatedSetRepresentation.class, "Citation.title");
        assert ! Verifications.attributeIsMendatory(TriangulatedSetRepresentation.class, "Citation.editor");
        assert Verifications.attributeIsMendatory(TriangulatedSetRepresentation.class, "uuid");
        assert Verifications.attributeIsMendatory(TriangulatedSetRepresentation.class, "trianglePatch");
    }

    public static void main(String[] argv) throws JAXBException{
        for(Method m : List.class.getMethods()){
            System.out.println(m.getName() + " " + m.getReturnType() + " è " + m.getGenericReturnType());
        }

        TriangulatedSetRepresentation tr = new TriangulatedSetRepresentation();
        tr.setUuid("00000000-0000-0000-0000-000000000001");
        tr.setSchemaVersion("2.2");
        tr.setSurfaceRole(SurfaceRole.MAP);

        TrianglePatch tp = new TrianglePatch();
        IntegerConstantArray ica = new IntegerConstantArray();
        ica.setCount(1);
        ica.setValue(0);
        tp.setTriangles(ica);
        PointGeometry pg = new PointGeometry();
        pg.setPoints(null);
        tp.setGeometry(null);
        tr.getTrianglePatch().add(tp);
        
        DataObjectReference dor = new DataObjectReference();
        dor.setUuid("00000000-0000-0000-0000-000000000002");
        dor.setTitle("Test DOR");
        dor.setQualifiedType("resqml20.obj_RockFluidUnitInterpretation");
        tr.setRepresentedObject(dor);
        Citation cit = new Citation();
        cit.setTitle("Test title");
        cit.setCreation(Utils.getCalendarForNow());
        cit.setLastUpdate(Utils.getCalendarForNow());
        cit.setFormat("cc");
        cit.setOriginator("coucou");


        tr.setCitation(cit);

        Activity act = new Activity();
        act.setUuid("00000000-0000-0000-0000-000000000003");
        act.setCitation(cit);
        act.setSchemaVersion("2.3");
        act.setActivityDescriptor(dor);
        DoubleQuantityParameter dqp = new DoubleQuantityParameter();
        dqp.setTitle("cc");
        dqp.setIsUncertain(true);
        dqp.setUom("m");
        act.getParameter().add(dqp);
        
        System.out.println("-------------------------------------------------");
        EPCPackageManager pm = new EPCPackageManager("energyml",
																		"D:/Geosiris/Github/webstudio/webstudio/docker/data/comments",
																		"D:/Geosiris/Github/webstudio/webstudio/docker/data/resqmlAccessibleDORMapping.json",
																		"D:/Geosiris/Github/webstudio/webstudio/docker/data/xsd/xsd_mapping.json");
        System.out.println(pm.validate(act));
    }
}
