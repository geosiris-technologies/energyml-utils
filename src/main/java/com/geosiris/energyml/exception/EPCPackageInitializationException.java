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
package com.geosiris.energyml.exception;

public class EPCPackageInitializationException  extends Exception {

    private String packagePath;
    public EPCPackageInitializationException(String packagePath){
        super();
        this.packagePath = packagePath;
    }
    @Override
    public String toString() {
        return "Fail to init package at path " + packagePath;
    }
}
