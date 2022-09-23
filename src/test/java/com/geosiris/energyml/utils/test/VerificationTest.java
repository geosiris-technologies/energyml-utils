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

import com.geosiris.energyml.utils.Verifications;
import energyml.resqml2_2.TriangulatedSetRepresentation;
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

    public static void main(String[] argv){
        for(Method m : List.class.getMethods()){
            System.out.println(m.getName() + " " + m.getReturnType() + " è " + m.getGenericReturnType());
        }
    }
}
