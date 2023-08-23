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

import com.geosiris.energyml.pkg.EPCFile;
import com.geosiris.energyml.pkg.EPCPackageManager;
import com.geosiris.energyml.utils.EPCGenericManager;
import com.geosiris.energyml.utils.ObjectController;
import com.geosiris.energyml.utils.Utils;
import com.google.gson.Gson;
import energyml.common2_3.AxisOrder2D;
import energyml.resqml2_2.ContactElement;
import energyml.resqml2_2.TriangulatedSetRepresentation;
import org.junit.jupiter.api.Test;

import javax.xml.datatype.XMLGregorianCalendar;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObjectControllerTest {
    private static final SampleClass_A objTest = new SampleClass_A();

    @Test
    void test_subatt_class(){
        assert (ObjectController.getObjectAttributeValue(objTest, "attr0")) instanceof Integer;
        assert (ObjectController.getObjectAttributeValue(objTest, "attr1")) instanceof Boolean;
        assert (ObjectController.getObjectAttributeValue(objTest, "lst0")) instanceof List;
        assert (ObjectController.getObjectAttributeValue(objTest, "map0")) instanceof Map;
        assert (ObjectController.getObjectAttributeValue(objTest, "sub0")) instanceof SampleClass_B;
        assert (ObjectController.getObjectAttributeValue(objTest, "sub1")) instanceof Map;
    }

    @Test
    void test_access_path_depth_1(){
        assert ((Integer) ObjectController.getObjectAttributeValue(objTest, "attr0")) == 42;
        assert ((Boolean) ObjectController.getObjectAttributeValue(objTest, "attr1"));
        assert ((List<?>) ObjectController.getObjectAttributeValue(objTest, "lst0")).size() == 4;
        assert ((Map<?, ?>) ObjectController.getObjectAttributeValue(objTest, "map0")).size() == 3;
        assert ((SampleClass_B) ObjectController.getObjectAttributeValue(objTest, "sub0")) != null;
        assert ((Map<?, ?>) ObjectController.getObjectAttributeValue(objTest, "sub1")).size() == 2;
    }

    @Test
    void test_has_attribute(){
        assert ObjectController.hasAttribute(objTest, "attr0");
        assert ObjectController.hasAttribute(objTest, "attr1");
        assert ObjectController.hasAttribute(objTest, "lst0");
        assert ObjectController.hasAttribute(objTest, "map0");
        assert ObjectController.hasAttribute(objTest, "sub0");
        assert ObjectController.hasAttribute(objTest, "sub1");

        assert ObjectController.hasAttribute(objTest, "sub1.a.x");
        assert ObjectController.hasAttribute(objTest, "sub1.a.y");
        assert ObjectController.hasAttribute(objTest, "sub1.b.x");
        assert ObjectController.hasAttribute(objTest, "sub1.b.y");

        assert !ObjectController.hasAttribute(objTest, "sub1ze.b.y");
        assert !ObjectController.hasAttribute(objTest, "sub1.b.ze");
        assert !ObjectController.hasAttribute(objTest, "sub1.ze.y");
    }

    @Test
    void test_access_path_depth_2_list(){
        System.err.println(((String) ObjectController.getObjectAttributeValue(objTest, "lst0.0")).compareTo("Hello"));
        assert ((String) ObjectController.getObjectAttributeValue(objTest, "lst0.0")).compareTo("Hello") == 0;
        assert ((String) ObjectController.getObjectAttributeValue(objTest, "lst0.1")).compareTo(" ") == 0;
        assert ((String) ObjectController.getObjectAttributeValue(objTest, "lst0.2")).compareTo("world") == 0;
        assert ((String) ObjectController.getObjectAttributeValue(objTest, "lst0.3")).compareTo("!") == 0;
    }

    @Test
    void test_access_path_depth_2_map(){
        assert ((String) ObjectController.getObjectAttributeValue(objTest, "map0.a")).compareTo("a0") == 0;
        assert ((String) ObjectController.getObjectAttributeValue(objTest, "map0.b")).compareTo("b1") == 0;
        assert ((String) ObjectController.getObjectAttributeValue(objTest, "map0.c")).compareTo("c2") == 0;
    }

    @Test
    void test_access_path_depth_3(){
        assert ((Float) ObjectController.getObjectAttributeValue(objTest, "sub1.a.x")) == -1.f;
        assert ((Float) ObjectController.getObjectAttributeValue(objTest, "sub1.a.y")) == 1.f;
        assert ((Float) ObjectController.getObjectAttributeValue(objTest, "sub1.b.x")) == -2.f;
        assert ((Float) ObjectController.getObjectAttributeValue(objTest, "sub1.b.y")) == 2.f;
    }

    @Test
    void test_primitive_class_find(){
        assert ObjectController.getPrimitivClass(Long.class) == long.class;
        assert ObjectController.getPrimitivClass(Integer.class) == int.class;
        assert ObjectController.getPrimitivClass(Float.class) == float.class;
        assert ObjectController.getPrimitivClass(Double.class) == double.class;
        assert ObjectController.getPrimitivClass(Boolean.class) == boolean.class;
    }

    @Test
    void test_super_class_suffix(){
        assert ObjectController.hasSuperClassSuffix(HashMap.class, "map");
        assert ObjectController.hasSuperClassSuffix(ArrayList.class, "Collection");
        assert ObjectController.hasSuperClassSuffix(ContactElement.class, "DataObjectReference");
    }

    @Test
    void test_sub_template_class(){
        assert ObjectController.getClassTemplatedTypeofSubParameter(SampleClass_A.class, "lst0").get(0) == String.class;

        assert ObjectController.getClassTemplatedTypeofSubParameter(SampleClass_A.class, "map0").get(0) == String.class;
        assert ObjectController.getClassTemplatedTypeofSubParameter(SampleClass_A.class, "map0").get(1) == String.class;

        assert ObjectController.getClassTemplatedTypeofSubParameter(SampleClass_A.class, "sub1").get(0) == String.class;
        assert ObjectController.getClassTemplatedTypeofSubParameter(SampleClass_A.class, "sub1").get(1) == SampleClass_B.class;
    }

    @Test
    void test_get_super_classes(){
        List<Class<?>> superClasses = ObjectController.getSuperClasses(C_C.class);

        assert superClasses.size() == 3;
        assert superClasses.get(0) == C_B.class;
        assert superClasses.get(1) == C_A.class;
        assert superClasses.get(2) == Object.class;

    }

    @Test
    void test_inherits(){
        assert ObjectController.inherits(C_C.class, C_C.class, false);
        assert !ObjectController.inherits(C_C.class, C_B.class, false);
        assert !ObjectController.inherits(C_C.class, C_A.class, false);

        assert ObjectController.inherits(C_C.class, C_A.class, true);
        assert ObjectController.inherits(C_C.class, C_B.class, true);

        assert ObjectController.inherits(C_C.class, "C_C", true, false);
        assert !ObjectController.inherits(C_C.class, "C_A", true, false);
        assert !ObjectController.inherits(C_C.class, "C_B", true, false);

        assert ObjectController.inherits(C_C.class, "C_A", true, true);
        assert ObjectController.inherits(C_C.class, "C_B", true, true);

        assert !ObjectController.inherits(C_C.class, "c_a", false, false);
        assert !ObjectController.inherits(C_C.class, "c_b", false, false);
        assert ObjectController.inherits(C_C.class, "c_c", false, false);

        assert ObjectController.inherits(C_C.class, "c_a", false, true);
        assert ObjectController.inherits(C_C.class, "c_b", false, true);
    }

    @Test
    void test_stringToEnum(){
        assert ObjectController.stringToEnum(AxisOrder2D.EASTING_SOUTHING.value(), AxisOrder2D.class) == AxisOrder2D.EASTING_SOUTHING;
        assert ObjectController.stringToEnum(AxisOrder2D.EASTING_NORTHING.value(), AxisOrder2D.class) == AxisOrder2D.EASTING_NORTHING;
        assert ObjectController.stringToEnum(AxisOrder2D.NORTHING_WESTING.value(), AxisOrder2D.class) == AxisOrder2D.NORTHING_WESTING;
    }

    @Test
    void test_is_property_class(){
        assert ObjectController.isPropertyClass(String.class);
        assert ObjectController.isPropertyClass(Integer.class);
        assert ObjectController.isPropertyClass(Long.class);
        assert ObjectController.isPropertyClass(Float.class);

        assert !ObjectController.isPropertyClass(SampleClass_A.class);
        assert !ObjectController.isPropertyClass(SampleClass_B.class);
        assert !ObjectController.isPropertyClass(Map.class);
        assert !ObjectController.isPropertyClass(List.class);
    }

    @Test
    void test_findAllAttributesFromName(){
        List<?> attribs_cs = ObjectController.findAllAttributesFromName(new SampleClass_A(), "name", true, false);
        assert attribs_cs.size() == 3;

        for(String val: new String[]{"First", "Second", "DefaultName"}){
            Boolean found = false;
            for(Object val_f : attribs_cs){
                found = found || val.compareTo((String) val_f) == 0;
            }
            assert found;
        }

        List<?> attribs_ci = ObjectController.findAllAttributesFromName(new SampleClass_A(), "name", false, false);
        assert attribs_ci.size() == 4;

        for(String val: new String[]{"First", "Second", "DefaultName", "Parent"}){
            Boolean found = false;
            for(Object val_f : attribs_ci){
                found = found || val.compareTo((String) val_f) == 0;
            }
            assert found;
        }

    }


/*     _____                       __             __
      / ___/____ _____ ___  ____  / /__     _____/ /___ ______________  _____
      \__ \/ __ `/ __ `__ \/ __ \/ / _ \   / ___/ / __ `/ ___/ ___/ _ \/ ___/
     ___/ / /_/ / / / / / / /_/ / /  __/  / /__/ / /_/ (__  |__  )  __(__  )
    /____/\__,_/_/ /_/ /_/ .___/_/\___/   \___/_/\__,_/____/____/\___/____/
                        /_/
*/

    protected static class C_A{
        public C_A(){}
    }
    protected static class C_B extends C_A{
        public C_B(){super();}
    }
    protected static class C_C extends C_B{
        public C_C(){super();}
    }

    protected static class SampleClass_A{
        private int attr0;
        private Boolean attr1;
        private List<String> lst0;
        private Map<String, String> map0;

        private SampleClass_B sub0;
        private Map<String, SampleClass_B> sub1;
        private String NaMe;

        public SampleClass_A(){
            this.attr0 = 42;
            this.attr1 = true;
            this.lst0 = new ArrayList<>(List.of(new String[]{"Hello", " ", "world", "!"}));
            this.map0 = new HashMap<>();
            this.map0.put("a", "a0");
            this.map0.put("b", "b1");
            this.map0.put("c", "c2");
            this.sub0 = new SampleClass_B();
            this.sub1 = new HashMap<>();
            this.sub1.put("a", new SampleClass_B(-1.f, 1.f, "First"));
            this.sub1.put("b", new SampleClass_B(-2.f, 2.f, "Second"));
            this.NaMe = "Parent";
        }

        public int getAttr0() {
            return attr0;
        }

        public void setAttr0(int attr0) {
            this.attr0 = attr0;
        }

        public Boolean getAttr1() {
            return attr1;
        }

        public void setAttr1(Boolean attr1) {
            this.attr1 = attr1;
        }

        public List<String> getLst0() {
            return lst0;
        }

        public void setLst0(List<String> lst0) {
            this.lst0 = lst0;
        }

        public Map<String, String> getMap0() {
            return map0;
        }

        public void setMap0(Map<String, String> map0) {
            this.map0 = map0;
        }

        public SampleClass_B getSub0() {
            return sub0;
        }

        public void setSub0(SampleClass_B sub0) {
            this.sub0 = sub0;
        }

        public Map<String, SampleClass_B> getSub1() {
            return sub1;
        }

        public void setSub1(Map<String, SampleClass_B> sub1) {
            this.sub1 = sub1;
        }

        public String getNaMe() {return NaMe;}

        public void setNaMe(String naMe) {NaMe = naMe;}
    }

    protected static class SampleClass_B{
        private Float x;
        private Float y;
        private String name;
        public SampleClass_B(){
            this.x = 12.f;
            this.y = 24.f;
            this.name = "DefaultName";
        }
        public SampleClass_B(Float x, Float y, String name){
            this.x = x;
            this.y = y;
            this.name = name;
        }

        public Float getX() {
            return x;
        }

        public void setX(Float x) {
            this.x = x;
        }

        public Float getY() {
            return y;
        }

        public void setY(Float y) {
            this.y = y;
        }

        public String getName() {return name;}

        public void setName(String name) {this.name = name;}
    }

    public static void main(String[] argv) throws Exception {
        Gson gson = new Gson();
        List<Object> objList = new ArrayList<>();
        objList.add(gson.fromJson("{"
                + "    \"uuid\": \"54d7f659-7be4-428e-8a96-4aab9915a986\","
                + "    \"citation\":"
                + "    {"
                + "        \"title\": \"Coucou Title0\","
                + "        \"originator\": \"Test class\""
                + "    }"
                + "}", TriangulatedSetRepresentation.class
        ));
        objList.add(gson.fromJson("{"
                + "    \"uuid\": \"54d7f659-7be4-428e-8a96-4aab9915a986\","
                + "    \"citation\":"
                + "    {"
                + "        \"title\": \"Coucou Title1\","
                + "        \"originator\": \"Test class\""
                + "    }"
                + "}", TriangulatedSetRepresentation.class
        ));
        objList.add(gson.fromJson("{"
                + "    \"uuid\": \"54d7f659-7be4-428e-8a96-4aab9915a986\","
                + "    \"citation\":"
                + "    {"
                + "        \"title\": \"Coucou Title2\","
                + "        \"originator\": \"Test class\""
                + "    }"
                + "}", TriangulatedSetRepresentation.class
        ));
        for(Object o : objList){
            ObjectController.editObjectAttribute(o, ".Citation.LastUpdate", Utils.getCalendar("2023-" + (12-objList.indexOf(o)) + "-09T09:57:52.268+02:00"));
        }

        ObjectController.editObjectAttribute(objList.get(1), "Citation.LastUpdate", null);

        objList.stream().forEach(x -> System.out.println(ObjectController.getObjectAttributeValue(x, "Citation.LastUpdate")));
        System.out.println("==============");
        objList.sort((a, b) -> {
                XMLGregorianCalendar a_lastModif = (XMLGregorianCalendar) ObjectController.getObjectAttributeValue(a,"Citation.LastUpdate");
                XMLGregorianCalendar b_lastModif = (XMLGregorianCalendar) ObjectController.getObjectAttributeValue(b,"Citation.LastUpdate");
                if(a_lastModif == null){
                    return 1;
                } else if (b_lastModif == null) {
                    return -1;
                }else{
                    return b_lastModif.compare(a_lastModif);
                }
            });
        objList.stream().forEach(x -> System.out.println(ObjectController.getObjectAttributeValue(x, "Citation.LastUpdate")));

        Map<String, String> m = new HashMap<>();
        m.put(null, "coucou");

        for(String s : m.keySet()){
            System.out.println(s + " -- " + m.get(s));
        }


        System.out.println(Paths.get("namespace_resqml22/aa.xml").getParent().relativize(Paths.get("namespace_resqml22/coucou.xml")).toString());
        System.out.println(Paths.get("namespace_witsml22/aa.xml").getParent().relativize(Paths.get("namespace_resqml22/coucou.xml")).toString());
        System.out.println("namespace_witsml22/_rels/aa.xml.rels".substring(0, "namespace_witsml22/_rels/aa.xml.rels".length() - 4).replace("rels/", "").replace("rels\\", ""));
        System.out.println("namespace_witsml22/_rels/aa.xml.rels".substring(0, "namespace_witsml22/_rels/aa.xml.rels".length() - 4).replace("rels/", "").replace("rels\\", ""));

        EPCPackageManager pkgManager = new EPCPackageManager("energyml",null, null, null);
        EPCFile.read(new FileInputStream("D:/Geosiris/Cloud/Resqml_Tools/2020-2022-DATA/ALWYN_DEPTH/ALWYN-RESQML_v2.2.epc"), pkgManager).export(new FileOutputStream("C:/Users/Cryptaro/Downloads/cc.epc"));

    }
}
