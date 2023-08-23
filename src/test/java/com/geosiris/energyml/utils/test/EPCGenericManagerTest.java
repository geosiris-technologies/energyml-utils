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
import com.geosiris.energyml.utils.ObjectController;
import com.geosiris.energyml.utils.Utils;
import energyml.common2_2.DataObjectReference;
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

    public static void main(String[] argv){
//        System.out.println(EPCGenericManager.getObjectQualifiedType(cName_tsr22dev3, true));
//        Matcher m_tsr201 = EPCGenericManager.PATTERN_QUALIFIED_TYPE.matcher(EPCGenericManager.getObjectQualifiedType(cName_tsr201, true));
//        m_tsr201.find();
//        System.out.println(m_tsr201.group("domain"));
//        System.out.println(m_tsr201.group("domainVersion"));
//        System.out.println(m_tsr201.group("type"));
//        EPCPackageManager manager = new EPCPackageManager("energyml", "", "", "");
//        String tr0 = Utils.readFileOrRessource("C:/Users/Cryptaro/Downloads/TriangulatedSetRepresentation_616f910c-b3f2-4910-978b-146c21762d12.xml");
//        System.out.println(tr0);
//        manager.getClasses().stream().filter(cl -> cl != null && !Modifier.isAbstract(cl.getModifiers())).collect(Collectors.toList()).forEach(x -> System.out.println(x));


        String graphical = "<?xml version=\"1.0\" encoding=\"utf-8\"?><GraphicalInformationSet xmlns=\"http://www.energistics.org/energyml/data/commonv2\" xmlns:ns2=\"http://www.energistics.org/energyml/data/resqmlv2\" uuid=\"5e3206c9-b3fa-4506-9ac0-1c76fdf72fbc\" schemaVersion=\"2.3\"><Citation><Title>GRAPHICAL INFORMATION SET 1</Title><Originator>Geosiris user</Originator><Creation>2023-05-24T09:15:46.016Z</Creation><Format>Geosiris WebStudio</Format><LastUpdate>2023-05-25T20:27:34.990Z</LastUpdate></Citation><GraphicalInformation xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"ns2:ColorInformation\"><TargetObject><Uuid>d1966850-6b1e-4dd7-abda-3c5b7b975712</Uuid><QualifiedType>resqml22.PolylineSetRepresentation</QualifiedType><Title>depthVolve_F3_2011</Title></TargetObject><TargetObject><Uuid>8e329639-4097-4467-9e0e-a04e645c9035</Uuid><QualifiedType>resqml22.PolylineSetRepresentation</QualifiedType><Title>depthVolve_F20_2013_update</Title></TargetObject><TargetObject><Uuid>38bf3283-9514-43ab-81e3-17080dc5826f</Uuid><QualifiedType>resqml22.PolylineSetRepresentation</QualifiedType><Title>depthVolve_F20_2011_New2</Title></TargetObject><TargetObject><Uuid>7908100a-68c1-4c07-a2f9-1c6f76af3a43</Uuid><QualifiedType>resqml22.PolylineSetRepresentation</QualifiedType><Title>depthVolve_F52_2011</Title></TargetObject><TargetObject><Uuid>052ec11a-e9f9-4faa-8512-ba0ef589952e</Uuid><QualifiedType>resqml22.PolylineSetRepresentation</QualifiedType><Title>depthVolve_F28_2011</Title></TargetObject><TargetObject><Uuid>3229af74-8dcd-4df5-8148-9b4f54d0c381</Uuid><QualifiedType>resqml22.PolylineSetRepresentation</QualifiedType><Title>depthVolve_F11_2011</Title></TargetObject><TargetObject><Uuid>4e23ee3e-54a7-427a-83f9-1473de6c56a4</Uuid><QualifiedType>resqml22.PolylineSetRepresentation</QualifiedType><Title>depthVolve_F27_2011</Title></TargetObject><TargetObject><Uuid>303bed39-6290-4ea7-b9f1-917438833fd5</Uuid><QualifiedType>resqml22.PolylineSetRepresentation</QualifiedType><Title>depthVolve_F8_2013_update</Title></TargetObject><TargetObject><Uuid>203ebec9-df29-4a6b-b621-ba1b8378c7fa</Uuid><QualifiedType>resqml22.PolylineSetRepresentation</QualifiedType><Title>depthVolve_F10_2011</Title></TargetObject><TargetObject><Uuid>27cf5a6d-6869-45e8-a061-555236f1546c</Uuid><QualifiedType>resqml22.PolylineSetRepresentation</QualifiedType><Title>depthVolve_F17_2011</Title></TargetObject><TargetObject><Uuid>162b0fd8-6dad-43ce-af6c-4b6370af767a</Uuid><QualifiedType>resqml22.PolylineSetRepresentation</QualifiedType><Title>depthVolve_F7_2011</Title></TargetObject><ns2:UseLogarithmicMapping>false</ns2:UseLogarithmicMapping><ns2:UseReverseMapping>false</ns2:UseReverseMapping><ns2:ValueVectorIndex>2</ns2:ValueVectorIndex><ns2:ColorMap><Uuid>44920872-7f80-4fd9-992e-cd941f45c2e4</Uuid><QualifiedType>resqml22.DiscreteColorMap</QualifiedType><Title>DISCRETE COLOR MAP 0</Title></ns2:ColorMap></GraphicalInformation><GraphicalInformation xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"ns2:ColorInformation\"><TargetObject><Uuid>349ecd25-5db0-40c1-b179-b5316fbc754f</Uuid><QualifiedType>resqml22.TriangulatedSetRepresentation</QualifiedType><Title>depthVolve_F27_2011_MBA</Title></TargetObject><TargetObject><Uuid>c2f53ce1-1b2d-4819-b397-f174bc8c23e0</Uuid><QualifiedType>resqml22.TriangulatedSetRepresentation</QualifiedType><Title>depthVolve_F11_2011_MBA</Title></TargetObject><TargetObject><Uuid>5db39032-4998-4b75-9156-ea104a8649d2</Uuid><QualifiedType>resqml22.TriangulatedSetRepresentation</QualifiedType><Title>depthVolve_F17_2011_MBA</Title></TargetObject><TargetObject><Uuid>fa4e80d0-d30f-48d3-aa70-41d06b837c2b</Uuid><QualifiedType>resqml22.TriangulatedSetRepresentation</QualifiedType><Title>depthVolve_F3_2011_MBA</Title></TargetObject><TargetObject><Uuid>bc1fbf7f-1495-4f1f-9c30-8be4b19a5475</Uuid><QualifiedType>resqml22.TriangulatedSetRepresentation</QualifiedType><Title>depthVolve_F7_2011_MBA</Title></TargetObject><TargetObject><Uuid>79f43d6a-77e1-46b7-b161-127b395a64be</Uuid><QualifiedType>resqml22.TriangulatedSetRepresentation</QualifiedType><Title>depthVolve_F28_2011_MBA</Title></TargetObject><TargetObject><Uuid>cdd9c2cd-6673-44b0-8d6d-812319daacaa</Uuid><QualifiedType>resqml22.TriangulatedSetRepresentation</QualifiedType><Title>depthVolve_F20_2013_update_MBA</Title></TargetObject><TargetObject><Uuid>13f6b289-3b0a-4ec3-b060-347c1efd6e5e</Uuid><QualifiedType>resqml22.TriangulatedSetRepresentation</QualifiedType><Title>depthVolve_F10_2011_MBA</Title></TargetObject><TargetObject><Uuid>5755aa3a-87b3-4a5d-bdf1-b8a6c1ebdf36</Uuid><QualifiedType>resqml22.TriangulatedSetRepresentation</QualifiedType><Title>depthVolve_F52_2011_MBA</Title></TargetObject><ns2:UseLogarithmicMapping>false</ns2:UseLogarithmicMapping><ns2:UseReverseMapping>false</ns2:UseReverseMapping><ns2:ValueVectorIndex>5</ns2:ValueVectorIndex><ns2:ColorMap><Uuid>44920872-7f80-4fd9-992e-cd941f45c2e4</Uuid><QualifiedType>resqml22.DiscreteColorMap</QualifiedType><Title>DISCRETE COLOR MAP 0</Title></ns2:ColorMap></GraphicalInformation></GraphicalInformationSet>";

        EPCPackageManager manager = new EPCPackageManager("energyml");
        System.out.println(manager.unmarshal(graphical).getValue());
    }
}
