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

import com.geosiris.energyml.utils.ExportVersion;
import com.geosiris.energyml.utils.Utils;
import energyml.common2_3.Citation;
import energyml.resqml2_2.TriangulatedSetRepresentation;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.regex.Matcher;

public class UtilsTest {
    private final static TriangulatedSetRepresentation TR_TEST = createTestData_trSet();

    @Test
    void test_pattern_schema(){
        Matcher m0 = Utils.PATTERN_SCHEMA.matcher("energyml.common2_3.Activity");

        if(m0.find()) {
            assert m0.group("pkgId").compareTo("common2_3") == 0;
            assert m0.group("pkgName").compareTo("common") == 0;
            assert m0.group("pkgVersion").compareTo("2_3") == 0;
            assert m0.group("devVersion") == null;
            assert m0.group("className").compareTo("Activity") == 0;
        }else{
            assert false;
        }

        Matcher m1 = Utils.PATTERN_SCHEMA.matcher("energyml.resqml_dev3x_2_2.TriangulatedSetRepresentation");

        if(m1.find()) {
            assert m1.group("pkgId").compareTo("resqml_dev3x_2_2") == 0;
            assert m1.group("pkgName").compareTo("resqml") == 0;
            assert m1.group("pkgVersion").compareTo("2_2") == 0;
            assert m1.group("devVersion").compareTo("_dev3x_") == 0;
            assert m1.group("className").compareTo("TriangulatedSetRepresentation") == 0;
        }else{
            assert false;
        }
    }

    @Test
    void test_schema_version(){
        assert Utils.getSchemaVersionFromClassName("energyml.common2_3.Activity").compareTo("2.3") == 0;
        assert Utils.getSchemaVersionFromClassName("energyml.resqml_dev3x_2_2.TriangulatedSetRepresentation").compareTo("2.2dev3") == 0;
        assert Utils.getSchemaVersionFromClassName("energyml.resqml2_2.TriangulatedSetRepresentation").compareTo("2.2") == 0;
    }

    @Test
    void test_energyml_pk_name(){
        assert Utils.getEnergymlPackageName(TR_TEST).compareTo("resqml22") == 0;
    }

    @Test
    void test_get_qualified_type(){
        assert Utils.getQualifiedType(TR_TEST).compareTo("resqml22.TriangulatedSetRepresentation") == 0;
    }

    @Test
    void test_gen_path_in_epc(){
        final String fName = TR_TEST.getClass().getSimpleName() + "_" + TR_TEST.getUuid();
        assert Utils.genPathInEPC(TR_TEST, ExportVersion.CLASSIC).compareTo(fName + ".xml") == 0;
        assert Utils.genPathInEPC(TR_TEST, ExportVersion.EXPANDED).compareTo("namespace_resqml22/" + fName + ".xml") == 0;
    }


    public static void main(String[] argv){

        System.err.println(Utils.genPathInEPC(TR_TEST, ExportVersion.EXPANDED));
    }

    public static TriangulatedSetRepresentation createTestData_trSet(){
        TriangulatedSetRepresentation tr = new TriangulatedSetRepresentation();
        tr.setUuid(UUID.randomUUID()+"");

        Citation cit = new Citation();
        cit.setCreation(Utils.getCalendarForNow());
        cit.setTitle("Tr Title");
        cit.setOriginator("Maven test");
        tr.setCitation(cit);

        return tr;
    }
}
