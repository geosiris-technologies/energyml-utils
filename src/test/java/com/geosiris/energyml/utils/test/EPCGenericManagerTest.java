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
import com.geosiris.energyml.utils.EPCGenericManager;
import com.geosiris.energyml.utils.ExportVersion;
import com.geosiris.energyml.utils.Utils;
import energyml.common2_3.Citation;
import energyml.resqml2_2.TriangulatedSetRepresentation;
import jdk.jshell.execution.Util;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;

public class EPCGenericManagerTest {
    private final String cName_tsr201 = "energyml.resqml2_0_1.TriangulatedSetRepresentation";
    private final String cName_tsr22 = "energyml.resqml2_2.TriangulatedSetRepresentation";
    private final String cName_tsr22dev3 = "energyml.resqml_dev3x_2_2.Obj_TriangulatedSetRepresentation";
    private final String cName_act23 = "energyml.common2_3.Activity";
    private final String cName_notExist0 = "energyml.unkownpkg2_3.Activity";

    private final static TriangulatedSetRepresentation TR_TEST = createTestData_trSet();


    @Test
    void getSchemaVersionFromClassName_test(){
        assert EPCGenericManager.getSchemaVersionFromClassName(cName_tsr201).compareTo("2.0.1") == 0;
        assert EPCGenericManager.getSchemaVersionFromClassName(cName_tsr22).compareTo("2.2") == 0;
        assert EPCGenericManager.getSchemaVersionFromClassName(cName_tsr22dev3).compareTo("2.2dev3") == 0;
        assert EPCGenericManager.getSchemaVersionFromClassName(cName_act23).compareTo("2.3") == 0;
        assert EPCGenericManager.getSchemaVersionFromClassName(cName_notExist0) == null;
    }

    @Test
    void patternClassName_test(){
        Matcher m_tsr201 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(cName_tsr201);
        m_tsr201.find();
        assert m_tsr201.group("name").compareTo("resqml") == 0;

        Matcher m_tsr22 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(cName_tsr22);
        m_tsr22.find();
        assert m_tsr22.group("name").compareTo("resqml") == 0;

        Matcher m_tsr22dev3 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(cName_tsr22dev3);
        m_tsr22dev3.find();
        assert m_tsr22dev3.group("name").compareTo("resqml") == 0;

        Matcher m_act23 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(cName_act23);
        m_act23.find();
        assert m_act23.group("name").compareTo("common") == 0;

        assert !EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(cName_notExist0).find();
    }

    @Test
    void patternClassPkgName_test(){
        Matcher m_tsr201 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(cName_tsr201);
        m_tsr201.find();
        assert m_tsr201.group("packageName").compareTo("resqml2_0_1") == 0;

        Matcher m_tsr22 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(cName_tsr22);
        m_tsr22.find();
        assert m_tsr22.group("packageName").compareTo("resqml2_2") == 0;

        Matcher m_tsr22dev3 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(cName_tsr22dev3);
        m_tsr22dev3.find();
        assert m_tsr22dev3.group("packageName").compareTo("resqml_dev3x_2_2") == 0;

        Matcher m_act23 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(cName_act23);
        m_act23.find();
        assert m_act23.group("packageName").compareTo("common2_3") == 0;
    }

    @Test
    void patternClassDev_test(){
        Matcher m_tsr201 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(cName_tsr201);
        m_tsr201.find();
        assert m_tsr201.group("dev") == null;

        Matcher m_tsr22 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(cName_tsr22);
        m_tsr22.find();
        assert m_tsr22.group("dev") == null;

        Matcher m_tsr22dev3 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(cName_tsr22dev3);
        m_tsr22dev3.find();
        assert m_tsr22dev3.group("dev").compareTo("dev3") == 0;

        Matcher m_act23 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(cName_act23);
        m_act23.find();
        assert m_act23.group("dev") == null;
    }

    @Test
    void test_pattern_schema(){
        Matcher m0 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher("energyml.common2_3.Activity");

        if(m0.find()) {
            assert m0.group("packageName").compareTo("common2_3") == 0;
            assert m0.group("name").compareTo("common") == 0;
            assert m0.group("versionNum").compareTo("2_3") == 0;
            assert m0.group("version").compareTo("2_3") == 0;
            assert m0.group("dev") == null;
            assert m0.group("className").compareTo("Activity") == 0;
        }else{
            assert false;
        }

        Matcher m1 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher("energyml.resqml_dev3x_2_2.TriangulatedSetRepresentation");

        if(m1.find()) {
            assert m1.group("packageName").compareTo("resqml_dev3x_2_2") == 0;
            assert m1.group("name").compareTo("resqml") == 0;
            assert m1.group("versionNum").compareTo("2_2") == 0;
            assert m1.group("version").compareTo("_dev3x_2_2") == 0;
            assert m1.group("dev").compareTo("dev3") == 0;
            assert m1.group("className").compareTo("TriangulatedSetRepresentation") == 0;
        }else{
            assert false;
        }
    }

    @Test
    void test_reformatSchemaVersion(){
        assert EPCGenericManager.reformatSchemaVersion("Resqml 2.2").compareTo("2.2") == 0;
        assert EPCGenericManager.reformatSchemaVersion("Resqml2.2").compareTo("2.2") == 0;
        assert EPCGenericManager.reformatSchemaVersion("Resqml v2.2").compareTo("2.2") == 0;
        assert EPCGenericManager.reformatSchemaVersion("Resqmlv2.2").compareTo("2.2") == 0;
        assert EPCGenericManager.reformatSchemaVersion("Resqml2.2dev3").compareTo("2.2dev3") == 0;
        assert EPCGenericManager.reformatSchemaVersion("  Resqml  2.2    dev3  ").compareTo("2.2dev3") == 0;
        assert EPCGenericManager.reformatSchemaVersion("v2.2").compareTo("2.2") == 0;
        assert EPCGenericManager.reformatSchemaVersion("2.2").compareTo("2.2") == 0;
        assert EPCGenericManager.reformatSchemaVersion("Witsml2.2").compareTo("2.2") == 0;
        assert EPCGenericManager.reformatSchemaVersion("NOtAResqmlType2.2").compareTo("2.2") == 0;
    }

    @Test
    void test_schema_version(){
        assert Objects.requireNonNull(EPCGenericManager.getSchemaVersionFromClassName("energyml.common2_3.Activity")).compareTo("2.3") == 0;
        assert Objects.requireNonNull(EPCGenericManager.getSchemaVersionFromClassName("energyml.resqml_dev3x_2_2.TriangulatedSetRepresentation")).compareTo("2.2dev3") == 0;
        assert Objects.requireNonNull(EPCGenericManager.getSchemaVersionFromClassName("energyml.resqml2_2.TriangulatedSetRepresentation")).compareTo("2.2") == 0;
    }

    @Test
    void test_energyml_pk_name(){
        assert EPCGenericManager.getPackageIdentifier_withVersionForETP(TR_TEST, 2, 2).compareTo("resqml22") == 0;
        assert EPCGenericManager.getPackageIdentifier_withVersionForETP(TR_TEST, 1, 1).compareTo("resqml2") == 0;
        assert EPCGenericManager.getPackageIdentifierFromClassName_withVersionForETP(cName_tsr22dev3, 2, 2).compareTo("resqml22") == 0;
        assert EPCGenericManager.getPackageIdentifierFromClassName_withVersionForETP(cName_tsr22dev3, 2, 3).compareTo("resqml22") == 0;
    }

    @Test
    void test_get_qualified_type(){
        assert EPCGenericManager.getObjectQualifiedType(TR_TEST).compareTo("resqml22.TriangulatedSetRepresentation") == 0;
    }

    @Test
    void test_gen_path_in_epc(){
        final String fName = TR_TEST.getClass().getSimpleName() + "_" + TR_TEST.getUuid();
        assert EPCGenericManager.genPathInEPC(TR_TEST, ExportVersion.CLASSIC).compareTo(fName + ".xml") == 0;
        assert EPCGenericManager.genPathInEPC(TR_TEST, ExportVersion.EXPANDED).compareTo("namespace_resqml22/" + fName + ".xml") == 0;
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

    public static void main(String[] argv){
        EPCPackageManager manager = new EPCPackageManager("energyml", "", "", "");
        String tr0 = Utils.readFileOrRessource("C:/Users/Cryptaro/Downloads/TriangulatedSetRepresentation_616f910c-b3f2-4910-978b-146c21762d12.xml");
        System.out.println(tr0);
    }
}
