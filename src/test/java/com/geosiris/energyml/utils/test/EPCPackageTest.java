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

import com.geosiris.energyml.exception.EPCPackageInitializationException;
import com.geosiris.energyml.pkg.EPCPackage;
import org.junit.jupiter.api.Test;

public class EPCPackageTest {

    @Test
    void testPkgInformation() throws EPCPackageInitializationException {
        EPCPackage pkg22 = new EPCPackage("energyml.resqml2_2", "");

        assert pkg22.getVersion().compareTo("2_2") == 0;
        assert pkg22.getVersionNum().compareTo("2.2") == 0;
        assert pkg22.getPackagePath().compareTo("energyml.resqml2_2") == 0;
        assert pkg22.getPackageName().compareTo("resqml2_2") == 0;
        assert pkg22.getName().compareTo("resqml") == 0;

        EPCPackage pkg22dev3 = new EPCPackage("energyml.resqml_dev3x_2_2", "");

        assert pkg22dev3.getVersion().compareTo("_dev3x_2_2") == 0;
        assert pkg22dev3.getVersionNum().compareTo("2.2dev3") == 0;
        assert pkg22dev3.getPackagePath().compareTo("energyml.resqml_dev3x_2_2") == 0;
        assert pkg22dev3.getPackageName().compareTo("resqml_dev3x_2_2") == 0;
        assert pkg22dev3.getName().compareTo("resqml") == 0;
    }
}
