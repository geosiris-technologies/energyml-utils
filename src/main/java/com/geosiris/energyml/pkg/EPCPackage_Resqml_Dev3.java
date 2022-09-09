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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

public class EPCPackage_Resqml_Dev3 extends EPCPackage {
    public static Logger logger = LogManager.getLogger(EPCPackage_Resqml_Dev3.class);
	private final static String staticPkgPath = "energyml.resqml_dev3x_";
	
	public static final Map<String, List<Class<?>>> pkgClasses = searchAllClassesForVersions(staticPkgPath);

	public static String getPackagePath_static(String version) {
		return staticPkgPath + reformatSchemaVersion(version).replace(".", "_");
	}

	public EPCPackage_Resqml_Dev3(String xsdMappingFilePath) {
		super("resqml (dev3)", staticPkgPath, xsdMappingFilePath);
		logger.info("EPCPackage_Resqml_Dev3 initialized "  + pkgClasses.keySet());
	}


	@Override
	public Map<String, List<Class<?>>> getAllClassesForVersion() {
		return pkgClasses;
	}

	@Override
	public List<String> getAllVersionsPackagePath() {
		return getAllVersionsPackagePath(pkgClasses);
	}
	
	@Override
	public String getNamespace() {
		return "resqml";
	}
	
	@Override
	public String getObjectContentType(Object obj) {
		return "application/x-resqml+xml;version=" + getSchemaVersion(obj) + ";type="
				+ getObjectTypeForFilePath(obj) + "";
	}

	@Override
	public List<Class<?>> getRootsElementsClasses() {
		return getRootsElementsClasses(pkgClasses);
	}


}
