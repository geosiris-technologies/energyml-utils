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
package com.geosiris.energyml.utils;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ContextBuilder {
	public static Logger logger = LogManager.getLogger(ContextBuilder.class);

	public static JAXBContext getContext(String pkg) {
		try {
			return JAXBContext.newInstance(pkg);
		} catch (JAXBException e) {
			logger.debug(e.getMessage(), e);
		}
		logger.error("[ENERGYML] #Err# Context : " + pkg + " not created !");
		return null;
	}
	public static Map<String, List<Class<?>>> getClassesForVersion(final String packageNamePrefix) {
		Map<String, List<Class<?>>> result = new HashMap<>();
		List<String> versions = getPackagesVersions(packageNamePrefix);
		if(versions.size()<=0)
			versions.add("");
		for(String version : versions){
			String pkg = packageNamePrefix + version.replace(".", "_");
			result.put(pkg, new ArrayList<>(getClasses(pkg)));
		}
		return result;
	}

	public static Set<Class<?>> getClasses(final String pkg) {
		Set<Class<?>> result = new HashSet<>();
		ClassLoader sysLoader = Thread.currentThread().getContextClassLoader();
		String pkgPath = pkg.replace(".", "/");
		try {
			Enumeration<URL> resources = sysLoader.getResources(pkgPath);
			while (resources.hasMoreElements()) {
				URL zipUrl = resources.nextElement();
				result.addAll(findInZip(zipUrl, pkgPath + "/[\\w]+\\.class", false, true).stream().map(c -> {
					try {
						return Class.forName(c);
					} catch (ClassNotFoundException ignore){}
					return null;
				}).collect(Collectors.toSet()));
			}
		}catch (IOException e){
			logger.error(e.getMessage(), e);
		}
		return result;
	}

	public static Set<String> findInZip(URL zipUrl, String regex, boolean onlyDir, boolean onlyFiles){
		Set<String> result = new HashSet<>();
		String finalUrl = zipUrl.getPath().replaceAll(".jar!/.*", ".jar")
				.replaceAll("^[(file|jar):]+/", "");

		ZipInputStream zip = null;
		try {
			zip = new ZipInputStream(new FileInputStream(finalUrl));
		}catch (IOException e){
			try {
				finalUrl = zipUrl.getPath().replaceAll(".jar!/.*", ".jar")
						.replaceAll("^[(file|jar):]+", ""); // without removing the first "/" after the ":"
				zip = new ZipInputStream(new FileInputStream(finalUrl));
			}catch (IOException e2){
				logger.error(e.getMessage(), e);
				logger.error(e2.getMessage(), e2);
			}
		}
		if(zip != null){
			try {
				while (true) {
					ZipEntry e = zip.getNextEntry();
					if (e == null) {
						break;
					}
					if (((onlyDir || ! onlyFiles) && e.isDirectory())
							|| ((onlyFiles || !onlyDir ) && !e.isDirectory())) {
						String name = e.getName();
						if (name.matches(regex)) {
							result.add(name.replace("/", ".").replaceAll("\\.$", "").replaceFirst("\\.class$", ""));
						}
					}
				}
			}catch (Exception e){
				logger.error(e.getMessage(), e);
			}
		}
		return result;
	}

	public static String getPackagePath(String packagePath, String version) {
		return packagePath + version.replace(".", "_");
	}

	public static JAXBContext createContext(String packagePath) {
		JAXBContext context = null;

		logger.debug("trying create jaxb context : " + packagePath);
		try {
			context = JAXBContext.newInstance(packagePath);
			logger.debug("jaxb context created for " + packagePath);
		} catch (Exception e) {
			logger.error("No context found for " + packagePath);
			logger.debug(e.getMessage(), e);
		}
		return context;
	}

	public static Map<String, JAXBContext> createAllContext_Filter(String packagePath, List<String> excludePkgList) {
		try {
			final List<String> pkgVersion = getPackagesVersions(packagePath);
			logger.info("Found pkg version for : " + packagePath + " : " + pkgVersion);
			if (pkgVersion.size() <= 0) {
				try {
					JAXBContext context = createContext(packagePath);
					if (context != null) {
						Map<String, JAXBContext> mapResult = new HashMap<>();
						mapResult.put(packagePath, context);
						return mapResult;
					}
				}catch (Exception e){
					logger.debug(e.getMessage(), e);
				}
			} else {
				return pkgVersion.stream().map((p_version) -> {

					String pkgPath = packagePath + p_version;
					logger.debug("\tCreating context for " + pkgPath);
					boolean allowed = true;
					for (String pkgToExclude : excludePkgList) {
						if (pkgToExclude.compareTo(pkgPath) == 0) {
							allowed = false;
							logger.debug("\tExcluding pkg " + pkgPath);
							break;
						}
					}

					if (allowed) {
						try {
							JAXBContext context = createContext(pkgPath);
							if (context != null) {
								Map<String, JAXBContext> mapResult = new HashMap<>();
								mapResult.put(pkgPath, context);
								return mapResult;
							}
						}catch (Exception e){
							logger.error(e.getMessage(), e);
						}
					}

					return null;
				}).reduce(new HashMap<>(), (a, b) -> {
					if(b != null)
						a.putAll(b);
					return a;
				});
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return new HashMap<>();
	}

	public static Map<String, JAXBContext> createAllContext(String packagePath) {
		return createAllContext_Filter(packagePath, new ArrayList<>());
	}


	public static List<String> getPackagesVersions(final String packageName){
		List<String> foundVersions = new ArrayList<>();
		Pattern pattern = Pattern.compile(packageName + Utils.PATTERN_PKG_VERSION);

		// Si on a rien trouve dans les classes du projet on cherche dans les jars du buildPath
		for (String pkg : listPackages(packageName.replace('.', '/'))) {
			Matcher match = pattern.matcher(pkg);
			while (match.find()) {
				foundVersions.add(match.group("version"));
			}
		}
		return foundVersions;
	}

	public static Set<String> listPackages(String prefix) {
		Set<String> result = new HashSet<>();

		String newPrefix = prefix;
		if(newPrefix.contains(".")){
			newPrefix = newPrefix.substring(0, newPrefix.lastIndexOf("."));
		}else if(newPrefix.contains("/")){
			newPrefix = newPrefix.substring(0, newPrefix.lastIndexOf("/"));
		}
		ClassLoader sysloader = Thread.currentThread().getContextClassLoader();
		try{
			Enumeration<URL> resources = sysloader.getResources(newPrefix);
			while(resources.hasMoreElements()){
				URL url = resources.nextElement();
				result.addAll(findInZip(url, prefix.replace(".", "/") + ".*", true, false));
			}
		}catch (IOException e){
			logger.error(e);
		}
		return result;
	}
}
