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

import java.util.List;
import java.util.Map;

public class EPCPackage_Witsml extends EPCPackage {
	private final static String staticPkgPath = "energyml.witsml";
	
	public static final Map<String, List<Class<?>>> pkgClasses = searchAllClassesForVersions(staticPkgPath);

	public static String getPackagePath_static(String version) {
		return staticPkgPath + reformatSchemaVersion(version).replace(".", "_");
	}

	public EPCPackage_Witsml(String xsdMappingFilePath) {
		super("witsml", staticPkgPath, xsdMappingFilePath);
		logger.info("EPCPackage_Witsml initialized " + pkgClasses.keySet());
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
	public String getObjectContentType(Object obj) {
		return "application/x-witsml+xml;version=" + getSchemaVersion(obj) + ";type="
				+ getObjectTypeForFilePath(obj) + "";
	}

	@Override
	public List<Class<?>> getRootsElementsClasses() {
		return getRootsElementsClasses(pkgClasses);
	}
}
