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

import com.geosiris.energyml.exception.EPCPackageInitializationException;
import com.geosiris.energyml.utils.ContextBuilder;
import com.geosiris.energyml.utils.EPCGenericManager;
import jakarta.xml.bind.*;
import jakarta.xml.bind.util.ValidationEventCollector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

public class EPCPackage {
    public static Logger logger = LogManager.getLogger(EPCPackage.class);

    protected JAXBContext jaxbContext;
    protected Schema xsdSchema;

    protected List<Class<?>> pkgClasses;

    protected final String domain;
    /**
     * Version of the package : for resqml2_2 is '2.2', for resqml_dev3x_2_2 is
     * '2.2dev3'
     */
    protected final String domainVersion;
    protected final String versionNum;
    protected final String devVersionNum;
    protected final String packageName;
    protected final String packagePath;

    public EPCPackage(String pkgPath, String xsdMappingFilePath) throws EPCPackageInitializationException {
        this(pkgPath, xsdMappingFilePath, Thread.currentThread().getContextClassLoader());
    }

    public EPCPackage(String pkgPath, String xsdMappingFilePath, final ClassLoader classLoader)
            throws EPCPackageInitializationException {
        String devVersionNum1;
        this.packagePath = pkgPath;

        Matcher pkgMatch = EPCGenericManager.PATTERN_ENERGYML_CLASS_NAME.matcher(pkgPath);
        if (pkgMatch.find()) {
            this.domain = pkgMatch.group("domain");
            this.domainVersion = (pkgMatch.group("versionNum")
                    + (pkgMatch.group("dev") != null ? pkgMatch.group("dev") : "")).replace("_", ".");
            this.packageName = pkgMatch.group("packageName");
            this.versionNum = pkgMatch.group("versionNum").replace("_", ".");
            try {
                devVersionNum1 = pkgMatch.group("devNum").replace("_", ".");
            } catch (Exception ignore) {
                devVersionNum1 = null;
            }
        } else {
            throw new EPCPackageInitializationException(pkgPath);
        }

        this.devVersionNum = devVersionNum1;
        this.jaxbContext = ContextBuilder.createContext(packagePath, classLoader);
        this.pkgClasses = new ArrayList<>(ContextBuilder.getClasses(this.packagePath, classLoader));

        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            xsdSchema = sf.newSchema(new File(Objects.requireNonNull(xsdMappingFilePath)));
        } catch (Exception e) {
            logger.error("ERR: no xsd schema for package '" + packagePath + "' at path " + xsdMappingFilePath);
        }
    }

    public boolean isDevVersion() {
        return devVersionNum != null;
    }

    public boolean isReleaseVersion() {
        return !isDevVersion();
    }

    public List<Class<?>> getRootsElementsClasses() {
        // return
        // pkgClasses.stream().filter(EPCGenericManager::isRootClass).collect(Collectors.toList());
        // OLD Way, but it includes types with no "createMY_TYPE_NAME(MY_TYPE value) ->
        // JaxbElement" functions
        Object objFactory = getObjectFactory();
        return Arrays.stream(objFactory.getClass().getMethods())
                .filter(m -> m.getReturnType() == JAXBElement.class && m.getParameterCount() == 1)
                .map(m -> m.getParameters()[0].getType())
                .collect(Collectors.toList());
    }

    public Object createInstance(String className) {
        Object result = null;
        String simpleClassName = className;
        if (simpleClassName.contains(".")) {
            simpleClassName = simpleClassName.substring(simpleClassName.lastIndexOf(".") + 1);
        }
        try {
            Object objFactory = getObjectFactory();
            if (objFactory == null) {
                throw new Exception("No " + packagePath + " factory found");
            }
            Method mCreate = objFactory.getClass().getMethod("create" + simpleClassName);

            result = mCreate.invoke(objFactory);
        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.debug(e.getMessage(), e);
        }

        if (result == null) {
            // Si la methode createXXX n'existe pas on cree l'instance sans la factory
            try {
                result = Class.forName(className).getConstructor().newInstance();
            } catch (Exception e) {
                logger.error(e.getMessage());
                logger.debug(e.getMessage(), e);
            }
        }
        return result;
    }

    public Boolean isClassNameMatchesPackage(String className) {
        return className.startsWith(this.packagePath + ".");
    }

    public Object getObjectFactory() {
        try {
            Class<?> objClass = Class.forName(this.packagePath + ".ObjectFactory");
            return objClass.getConstructor().newInstance();
        } catch (Exception e) {
            logger.error("Error in getResqmlFactory with schemaVersion '" + domainVersion + "'");
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    public JAXBElement<?> parseXmlContent(String xmlContent, boolean alternateDevVersionExists) {
        long ticBegin = System.currentTimeMillis();

        logger.debug(">Trying to parse from package '" + this.packagePath);

        JAXBElement<?> result = parseXmlFromContext(xmlContent, true, alternateDevVersionExists, xsdSchema);
        if (result != null) {
            logger.debug("Success reading with '" + this.packagePath + "' object class : "
                    + result.getValue().getClass().getName());
        } else {
            logger.debug("\terror reading with package " + this.packagePath); // + " --> " + xmlContent);
        }

        long ticEnd = System.currentTimeMillis();
        logger.debug("\t@parseXmlContent : Parsing file took " + (ticEnd - ticBegin) / 1000.0 + "s");
        return result;
    }

    private JAXBElement<?> parseXmlFromContext(String xmlContent,
            boolean tryWithoutNamespaceIfFail,
            boolean testRemarshalling, // test to marshall/unmarshall after first unmarshall
            // to verify if everything has been read correctly
            Schema schema) {
        ValidationEventCollector vec = null;

        try {
            long ticCreateUnmarshaller_b = System.currentTimeMillis();
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            if (schema != null)
                unmarshaller.setSchema(schema);

            long ticCreateUnmarshaller_e = System.currentTimeMillis();
            logger.debug("\t@parseXmlFromContext (" + this.packagePath + ": Creating unmarshaller "
                    + (ticCreateUnmarshaller_e - ticCreateUnmarshaller_b) / 1000.0 + "s");

            vec = new ValidationEventCollector();
            unmarshaller.setEventHandler(vec);

            ByteArrayInputStream bais = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
            long ticUnmarshall_b = System.currentTimeMillis();
            JAXBElement<?> result = (JAXBElement<?>) unmarshaller.unmarshal(bais);
            long ticUnmarshall_e = System.currentTimeMillis();
            logger.debug("\t@parseXmlFromContext : Unmarshalling took " + (ticUnmarshall_e - ticUnmarshall_b) / 1000.0
                    + "s");

            logger.debug("result  " + result);
            if (vec.getEvents().length > 0) {
                for (ValidationEvent ev : vec.getEvents()) {
                    logger.debug("event : " + ev);
                }
                logger.debug("events found");
                return null;
            } else if (result != null) {
                if (testRemarshalling) {
                    logger.debug("no events found");
                    long ticMarshall_b = System.currentTimeMillis();
                    String xmlNewContent = marshal(result.getValue());
                    long ticMarshall_e = System.currentTimeMillis();
                    logger.debug("\t@parseXmlFromContext : Marshalling took " + (ticMarshall_e - ticMarshall_b) / 1000.0
                            + "s");

                    long ticCountingChevron_b = System.currentTimeMillis();
                    int countOld = 0;
                    int countNew = 0;
                    for (int i = 0; i < xmlNewContent.length(); i++)
                        if (xmlNewContent.charAt(i) == '<')
                            if (xmlNewContent.charAt(i + 1) != '?') // On ne compte pas le <?xml du dÃ©part qui n'est
                                                                    // pas
                                // toujours la
                                countNew++;

                    for (int i = 0; i < xmlContent.length(); i++)
                        if (xmlContent.charAt(i) == '<')
                            if (xmlContent.charAt(i + 1) != '?') // On ne compte pas le <?xml du dÃ©part qui n'est pas
                                // toujours la
                                countOld++;

                    long ticCountingChevron_e = System.currentTimeMillis();

                    logger.debug("\t@parseXmlFromContext : Counting chevron cost "
                            + (ticCountingChevron_e - ticCountingChevron_b) / 1000.0 + "s");

                    if (countOld > countNew) {
                        logger.error("Error reading object class : " + result.getValue().getClass().getName()
                                + " old count of '<' : " + countOld + " new is : " + countNew);
                        if (tryWithoutNamespaceIfFail) {

                            logger.debug("Trying without namespace");
                            String contentNoNamespace = xmlContent.replaceAll("<(/?)[a-zA-Z0-9_]+:([\\w\\d_]+)",
                                    "<$1$2");
                            return parseXmlFromContext(contentNoNamespace, false, testRemarshalling, schema);
                        } else {
                            return null;
                        }
                    }
                }
                return result;
            }
        } catch (Exception e) {
            // Optimized fix: Only process if obj_ prefix is detected
            String firstchars = xmlContent.length() > 100 ? xmlContent.substring(0, 100) : xmlContent;

            if (firstchars.contains(":obj_") || firstchars.contains("<obj_")) {
                // Only apply regex replacement if obj_ prefix is found
                logger.debug("First lines : " + firstchars);
                String processedXmlContent = xmlContent.replaceAll("(<\\s*/?[^:]+:)?obj_([A-Za-z])", "$1$2");
                logger.debug("After replace : " + processedXmlContent);
                assert !processedXmlContent.contains(":obj_") && !processedXmlContent.contains("<obj_");
                logger.debug("Applied obj_ prefix removal transformation");
                try {
                    JAXBElement<?> result = parseXmlFromContext(processedXmlContent, tryWithoutNamespaceIfFail,
                            testRemarshalling, schema);
                    if (result != null) {
                        logger.debug("Successfully parsed after obj_ prefix removal");
                        return result;
                    }
                } catch (Exception ignore) {
                }
                logger.debug("Failed to parse after obj_ prefix removal");
            } else {
                if (schema != null) {
                    logger.debug(e.getCause() + " " + e.getMessage());
                    // try to parse without schema
                    return parseXmlFromContext(xmlContent, tryWithoutNamespaceIfFail, testRemarshalling, null);
                }
                logger.debug(e.getMessage(), e);
                logger.debug("File not read : ");
                logger.debug(xmlContent.substring(0, 500) + " [.....]");
                if (vec != null) {
                    logger.debug("\tRead event [" + vec.getEvents().length + "]");
                    for (ValidationEvent ev : vec.getEvents()) {
                        logger.debug("event : " + ev);
                    }
                }
            }
        }
        return null;
    }

    public void marshal(Object obj, OutputStream os) {
        if (jaxbContext != null) {
            try {
                Marshaller marshaller = jaxbContext.createMarshaller();
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
                marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
                marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);

                Object factory = getObjectFactory();
                try {
                    JAXBElement<?> elt = wrap(obj, factory);
                    marshaller.marshal(elt, os);
                } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException e) {
                    logger.error(e.getMessage(), e);
                }
            } catch (JAXBException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    public static JAXBElement<?> wrap(Object energymlObject, Object factory) throws NoSuchMethodException,
            SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        final Class<?> class22 = energymlObject.getClass();
        final String typename = class22.getSimpleName();

        String[] listOfPotentialRemovablePrefix = { "Obj" };
        Class<?> classFactory22 = factory.getClass();

        ArrayList<String> methodPotentialName = new ArrayList<>();
        methodPotentialName.add("create" + typename);
        methodPotentialName.add(typename.substring(0, 1).toLowerCase() + typename.substring(1));
        for (String prefixToRemove : listOfPotentialRemovablePrefix) {
            if (typename.startsWith(prefixToRemove)) {
                String noPrefix = typename.substring(prefixToRemove.length());
                methodPotentialName.add("create" + noPrefix);
                methodPotentialName.add(noPrefix.substring(0, 1).toLowerCase() + noPrefix.substring(1));
            }
        }

        for (String m_name : methodPotentialName) {
            try {
                Method m = classFactory22.getMethod(m_name, class22);
                return (JAXBElement<?>) m.invoke(factory, energymlObject);
            } catch (NoSuchMethodException e) {
                // pass
            } catch (Exception e2) {
                logger.error(e2.getMessage(), e2);
            }
        }
        logger.error("No method found to create a type " + typename);
        logger.error("Tested names " + methodPotentialName.stream().reduce((s1, s2) -> s1 + ", " + s2));
        return null;
    }

    public boolean validate(Object obj) throws JAXBException {
        if (obj != null) {
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            jaxbUnmarshaller.setSchema(xsdSchema);

            String xmlObj = marshal(obj);
            logger.debug(xmlObj);
            ByteArrayInputStream bais = new ByteArrayInputStream(xmlObj.getBytes(StandardCharsets.UTF_8));

            jaxbUnmarshaller.unmarshal(bais);
            return true;
        }
        return false;
    }

    public String marshal(Object obj) {
        if (obj != null) {
            try {
                OutputStream os = new ByteArrayOutputStream();
                marshal(obj, os);
                return os.toString();
            } catch (Exception e) {
                logger.error("Error marshalling object " + obj);
                logger.debug(e.getMessage(), e);
            }
        }
        return null;
    }

    /*
     * ______ __ __ _______ __ __
     * / ____/__ / /_/ /____ __________ _/_/ ___/___ / /_/ /____ __________
     * / / __/ _ \/ __/ __/ _ \/ ___/ ___/_/_/ \__ \/ _ \/ __/ __/ _ \/ ___/ ___/
     * / /_/ / __/ /_/ /_/ __/ / (__ )/_/ ___/ / __/ /_/ /_/ __/ / (__ )
     * \____/\___/\__/\__/\___/_/ /____/_/ /____/\___/\__/\__/\___/_/ /____/
     */

    public String getDomain() {
        return domain;
    }

    public String getDomainVersion() {
        return domainVersion;
    }

    public String getVersionNum() {
        return versionNum;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getPackagePath() {
        return packagePath;
    }

    public List<Class<?>> getPkgClasses() {
        return pkgClasses;
    }

    public String getDevVersionNum() {
        return devVersionNum;
    }

    public boolean matchNamespace(String namespace) {
        return namespace.toLowerCase().contains(domain.toLowerCase());
    }
}
