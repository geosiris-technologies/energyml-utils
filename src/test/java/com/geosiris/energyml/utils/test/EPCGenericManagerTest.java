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

import com.geosiris.energyml.utils.EPCGenericManager;
import com.geosiris.energyml.utils.ExportVersion;
import com.geosiris.energyml.utils.ObjectController;
import com.geosiris.energyml.utils.Utils;
import energyml.common2_3.Citation;
import energyml.resqml2_2.TriangulatedSetRepresentation;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;

public class EPCGenericManagerTest {
    private final static String cName_tsr201 = "energyml.resqml2_0_1.ObjTriangulatedSetRepresentation";
    private final static String cName_tsr22 = "energyml.resqml2_2.TriangulatedSetRepresentation";
    private final static String cName_tsr22dev3 = "energyml.resqml_dev3x_2_2.TriangulatedSetRepresentation";
    private final static String cName_act23 = "energyml.common2_3.Activity";
    private final static String cName_notExist0 = "energyml.unkownpkg2_3.Activity";

    private final static TriangulatedSetRepresentation TR_TEST = createTestData_trSet();


    @Test
    void getSchemaVersionFromClassName_test(){
        assert EPCGenericManager.getSchemaVersionFromClassName(cName_tsr201, true).compareTo("2.0") == 0;
        assert EPCGenericManager.getSchemaVersionFromClassName(cName_tsr201, true, 3).compareTo("2.0.1") == 0;
        assert EPCGenericManager.getSchemaVersionFromClassName(cName_tsr22, true).compareTo("2.2") == 0;
        assert EPCGenericManager.getSchemaVersionFromClassName(cName_tsr22dev3, true).compareTo("2.2dev3") == 0;
        assert EPCGenericManager.getSchemaVersionFromClassName(cName_tsr22dev3, false).compareTo("2.2") == 0;
        assert EPCGenericManager.getSchemaVersionFromClassName(cName_act23, true).compareTo("2.3") == 0;
        assert EPCGenericManager.getSchemaVersionFromClassName(cName_notExist0, true) == null;
    }

    @Test
    void patternClassName_test(){
        Matcher m_tsr201 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(cName_tsr201);
        m_tsr201.find();
        assert m_tsr201.group("domain").compareTo("resqml") == 0;

        Matcher m_tsr22 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(cName_tsr22);
        m_tsr22.find();
        assert m_tsr22.group("domain").compareTo("resqml") == 0;

        Matcher m_tsr22dev3 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(cName_tsr22dev3);
        m_tsr22dev3.find();
        assert m_tsr22dev3.group("domain").compareTo("resqml") == 0;

        Matcher m_act23 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(cName_act23);
        m_act23.find();
        assert m_act23.group("domain").compareTo("common") == 0;

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
    void patternContentType_test(){
        Matcher m_tsr201 = EPCGenericManager.PATTERN_CONTENT_TYPE.matcher(EPCGenericManager.getObjectContentType_fromClassName(cName_tsr201, true));
        m_tsr201.find();
        assert m_tsr201.group("domain").compareTo("resqml") == 0;
        assert m_tsr201.group("domainVersion").compareTo("2.0") == 0;
        assert m_tsr201.group("type").compareTo("obj_TriangulatedSetRepresentation") == 0;

        Matcher m_tsr201_bis_3digits = EPCGenericManager.PATTERN_CONTENT_TYPE.matcher(EPCGenericManager.getObjectContentType_fromClassName(cName_tsr201, true,3));
        m_tsr201_bis_3digits.find();
        assert m_tsr201_bis_3digits.group("domain").compareTo("resqml") == 0;
        assert m_tsr201_bis_3digits.group("domainVersion").compareTo("2.0.1") == 0;
        assert m_tsr201_bis_3digits.group("type").compareTo("obj_TriangulatedSetRepresentation") == 0;

        Matcher m_tsr22 = EPCGenericManager.PATTERN_CONTENT_TYPE.matcher(EPCGenericManager.getObjectContentType_fromClassName(cName_tsr22, true));
        m_tsr22.find();
        assert m_tsr22.group("domain").compareTo("resqml") == 0;
        assert m_tsr22.group("domainVersion").compareTo("2.2") == 0;
        assert m_tsr22.group("type").compareTo("TriangulatedSetRepresentation") == 0;

        Matcher m_tsr22dev3 = EPCGenericManager.PATTERN_CONTENT_TYPE.matcher(EPCGenericManager.getObjectContentType_fromClassName(cName_tsr22dev3, true));
        m_tsr22dev3.find();
        assert m_tsr22dev3.group("domain").compareTo("resqml") == 0;
        assert m_tsr22dev3.group("domainVersion").compareTo("2.2dev3") == 0;
        assert m_tsr22dev3.group("type").compareTo("TriangulatedSetRepresentation") == 0;

        Matcher m_tsr22dev3_noDev = EPCGenericManager.PATTERN_CONTENT_TYPE.matcher(EPCGenericManager.getObjectContentType_fromClassName(cName_tsr22dev3));
        m_tsr22dev3_noDev.find();
        assert m_tsr22dev3_noDev.group("domain").compareTo("resqml") == 0;
        assert m_tsr22dev3_noDev.group("domainVersion").compareTo("2.2") == 0;
        assert m_tsr22dev3_noDev.group("type").compareTo("TriangulatedSetRepresentation") == 0;

        Matcher m_tsr22dev3_BIS = EPCGenericManager.PATTERN_CONTENT_TYPE.matcher(EPCGenericManager.getObjectContentType_fromClassName(cName_tsr22dev3, false));
        m_tsr22dev3_BIS.find();
        assert m_tsr22dev3_BIS.group("domain").compareTo("resqml") == 0;
        assert m_tsr22dev3_BIS.group("domainVersion").compareTo("2.2") == 0;
        assert m_tsr22dev3_BIS.group("type").compareTo("TriangulatedSetRepresentation") == 0;

        Matcher m_act23 = EPCGenericManager.PATTERN_CONTENT_TYPE.matcher(EPCGenericManager.getObjectContentType_fromClassName(cName_act23, true));
        m_act23.find();
        assert m_act23.group("domain").compareTo("eml") == 0;
        assert m_act23.group("domainVersion").compareTo("2.3") == 0;
        assert m_act23.group("type").compareTo("Activity") == 0;
    }

    @Test
    void test_has_attribute_content_type(){
        assert ObjectController.hasAttribute(new energyml.common2_2.DataObjectReference(), "contentType");
        assert !ObjectController.hasAttribute(new energyml.common2_3.DataObjectReference(), "contentType");
    }

    @Test
    void test_has_attribute_qualified_type(){
        assert !ObjectController.hasAttribute(new energyml.common2_2.DataObjectReference(), "qualifiedType");
        assert ObjectController.hasAttribute(new energyml.common2_3.DataObjectReference(), "qualifiedType");
    }

    @Test
    void patternQualified_test(){
        Matcher m_tsr201 = EPCGenericManager.PATTERN_QUALIFIED_TYPE.matcher(EPCGenericManager.getObjectQualifiedType(cName_tsr201, true));
        m_tsr201.find();
        assert m_tsr201.group("domain").compareTo("resqml") == 0;
        assert m_tsr201.group("domainVersion").compareTo("20") == 0;
        assert m_tsr201.group("type").compareTo("obj_TriangulatedSetRepresentation") == 0;

        Matcher m_tsr22 = EPCGenericManager.PATTERN_QUALIFIED_TYPE.matcher(EPCGenericManager.getObjectQualifiedType(cName_tsr22, true));
        m_tsr22.find();
        assert m_tsr22.group("domain").compareTo("resqml") == 0;
        assert m_tsr22.group("domainVersion").compareTo("22") == 0;
        assert m_tsr22.group("type").compareTo("TriangulatedSetRepresentation") == 0;

        Matcher m_tsr22dev3 = EPCGenericManager.PATTERN_QUALIFIED_TYPE.matcher(EPCGenericManager.getObjectQualifiedType(cName_tsr22dev3, true));
        m_tsr22dev3.find();
        assert m_tsr22dev3.group("domain").compareTo("resqml") == 0;
        assert m_tsr22dev3.group("domainVersion").compareTo("22dev3") == 0;
        assert m_tsr22dev3.group("type").compareTo("TriangulatedSetRepresentation") == 0;

        Matcher m_tsr22dev3_BIS = EPCGenericManager.PATTERN_QUALIFIED_TYPE.matcher(EPCGenericManager.getObjectQualifiedType(cName_tsr22dev3, false));
        m_tsr22dev3_BIS.find();
        assert m_tsr22dev3_BIS.group("domain").compareTo("resqml") == 0;
        assert m_tsr22dev3_BIS.group("domainVersion").compareTo("22") == 0;
        assert m_tsr22dev3_BIS.group("type").compareTo("TriangulatedSetRepresentation") == 0;

        Matcher m_act23 = EPCGenericManager.PATTERN_QUALIFIED_TYPE.matcher(EPCGenericManager.getObjectQualifiedType(cName_act23, true));
        m_act23.find();
        assert m_act23.group("domain").compareTo("eml") == 0;
        assert m_act23.group("domainVersion").compareTo("23") == 0;
        assert m_act23.group("type").compareTo("Activity") == 0;
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
            assert m0.group("domain").compareTo("common") == 0;
            assert m0.group("versionNum").compareTo("2_3") == 0;
            assert m0.group("version").compareTo("2_3") == 0;
            assert m0.group("dev") == null;
            assert m0.group("type").compareTo("Activity") == 0;
        }else{
            assert false;
        }

        Matcher m1 = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher("energyml.resqml_dev3x_2_2.TriangulatedSetRepresentation");

        if(m1.find()) {
            assert m1.group("packageName").compareTo("resqml_dev3x_2_2") == 0;
            assert m1.group("domain").compareTo("resqml") == 0;
            assert m1.group("versionNum").compareTo("2_2") == 0;
            assert m1.group("version").compareTo("_dev3x_2_2") == 0;
            assert m1.group("dev").compareTo("dev3") == 0;
            assert m1.group("type").compareTo("TriangulatedSetRepresentation") == 0;
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
        assert Objects.requireNonNull(EPCGenericManager.getSchemaVersionFromClassName("energyml.common2_3.Activity", true)).compareTo("2.3") == 0;
        assert Objects.requireNonNull(EPCGenericManager.getSchemaVersionFromClassName("energyml.resqml_dev3x_2_2.TriangulatedSetRepresentation", true)).compareTo("2.2dev3") == 0;
        assert Objects.requireNonNull(EPCGenericManager.getSchemaVersionFromClassName("energyml.resqml_dev3x_2_2.TriangulatedSetRepresentation", false)).compareTo("2.2") == 0;
        assert Objects.requireNonNull(EPCGenericManager.getSchemaVersionFromClassName("energyml.resqml2_2.TriangulatedSetRepresentation", true)).compareTo("2.2") == 0;
    }

    @Test
    void test_energyml_pk_name(){
        assert EPCGenericManager.getPackageDomain_withVersionForETP(TR_TEST, 2, 2, true).compareTo("resqml22") == 0;
        assert EPCGenericManager.getPackageDomain_withVersionForETP(TR_TEST, 1, 1, true).compareTo("resqml2") == 0;
        assert EPCGenericManager.getPackageDomain_withVersionForETP_fromClassName(cName_tsr22dev3, 1, 1, true).compareTo("resqml2dev3") == 0;
        assert EPCGenericManager.getPackageDomain_withVersionForETP_fromClassName(cName_tsr22dev3, 2, 2, true).compareTo("resqml22dev3") == 0;
        assert EPCGenericManager.getPackageDomain_withVersionForETP_fromClassName(cName_tsr22dev3, 2, 2, false).compareTo("resqml22") == 0;
        assert EPCGenericManager.getPackageDomain_withVersionForETP_fromClassName(cName_tsr22dev3, 2, 3, true).compareTo("resqml22dev3") == 0;
        assert EPCGenericManager.getPackageDomain_withVersionForETP_fromClassName(cName_tsr22dev3, 2, 3, false).compareTo("resqml22") == 0;
        assert EPCGenericManager.getPackageDomain_withVersionForETP_fromClassName(cName_tsr22dev3, 3, 3, false).compareTo("resqml220") == 0;
    }

    @Test
    void test_getObjectTypeForFilePath_fromClassName(){
        assert EPCGenericManager.getObjectTypeForFilePath(TR_TEST).compareTo("TriangulatedSetRepresentation") == 0;
        assert EPCGenericManager.getObjectTypeForFilePath_fromClassName(cName_tsr201).compareTo("obj_TriangulatedSetRepresentation") == 0;
    }

    @Test
    void test_get_qualified_type(){
        assert EPCGenericManager.getObjectQualifiedType(TR_TEST, true).compareTo("resqml22.TriangulatedSetRepresentation") == 0;
        assert EPCGenericManager.getObjectQualifiedType(TR_TEST, false).compareTo("resqml22.TriangulatedSetRepresentation") == 0;
    }

    @Test
    void test_gen_path_in_epc(){
        final String fName = TR_TEST.getClass().getSimpleName() + "_" + TR_TEST.getUuid();
        assert EPCGenericManager.genPathInEPC(TR_TEST, ExportVersion.CLASSIC).compareTo(fName + ".xml") == 0;
        assert EPCGenericManager.genPathInEPC(TR_TEST, ExportVersion.EXPANDED).compareTo("namespace_resqml22/" + fName + ".xml") == 0;
    }

    @Test
    void test_reshape_version(){
        assert EPCGenericManager.reshapeVersion("v2.0.1", 0).compareTo("v2.0.1") == 0;
        assert EPCGenericManager.reshapeVersion("v2.0.1", 1).compareTo("2") == 0;
        assert EPCGenericManager.reshapeVersion("v2.0.1", 2).compareTo("2.0") == 0;
        assert EPCGenericManager.reshapeVersion("v2.0.1", 3).compareTo("2.0.1") == 0;
        assert EPCGenericManager.reshapeVersion("v2.0.1", 4).compareTo("v2.0.1") == 0;
        assert EPCGenericManager.reshapeVersion("v2.0.1.5.2.6.5.4", 4).compareTo("v2.0.1.5.2.6.5.4") == 0;
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

    public static energyml.resqml_dev3x_2_2.TriangulatedSetRepresentation createTestData_trSet_22dev3(){
        energyml.resqml_dev3x_2_2.TriangulatedSetRepresentation tr = new energyml.resqml_dev3x_2_2.TriangulatedSetRepresentation();
        tr.setUuid(UUID.randomUUID()+"");

        energyml.common2_2.Citation cit = new energyml.common2_2.Citation();
        cit.setCreation(Utils.getCalendarForNow());
        cit.setTitle("Tr Title");
        cit.setOriginator("Maven test");
        tr.setCitation(cit);

        return tr;
    }
}
