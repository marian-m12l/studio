/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.util.Properties;
import java.util.jar.JarFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StudioAgent {

    public static final String AGENT_PROPERTIES = "studio-agent.properties";
    public static final String METADATA_JAR = "/.studio/agent/studio-metadata.jar";

    private static final Logger LOGGER = LogManager.getLogger("studio-agent");

    private StudioAgent() {
        throw new IllegalArgumentException("Utility class");
    }

    public static void premain(String arguments, Instrumentation instrumentation) throws IOException {
        LOGGER.info("Started studio-agent (premain) version {}", getVersion());

        // Add metadata library to bootstrap classpath
        String metadataLibraryPath = System.getProperty("user.home") + METADATA_JAR;
        instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(metadataLibraryPath));

        // Add advices to hijack metadata and images resolution
        File temp = Files.createTempDirectory("studio-agent-bootstrap").toFile();
        new AgentBuilder.Default()
                // Allow JDK classes transformation
                .ignore(ElementMatchers.<TypeDescription>none())
                // Log transformations
                .with(new AgentBuilder.Listener.Adapter() {
                    @Override
                    public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule, boolean b, DynamicType dynamicType) {
                        LOGGER.info("Transformation registered on class: {}", typeDescription.getTypeName());
                    }
                    @Override
                    public void onError(String s, ClassLoader classLoader, JavaModule javaModule, boolean b, Throwable throwable) {
                        LOGGER.error("Transformation error: " + s, throwable);
                    }
                })
                // Allows resolution of advice classes
                .with(new AgentBuilder.InjectionStrategy.UsingInstrumentation(instrumentation, temp))
                // Transform HttpURLConnection#getInputStream to resolve unofficial metadata
                .type(ElementMatchers.<TypeDescription>named("java.net.HttpURLConnection"))
                .transform(
                        new AgentBuilder.Transformer.ForAdvice()
                                .include(UnofficialMetadataAdvice.class.getClassLoader())
                                .advice(ElementMatchers.<MethodDescription>nameEndsWith("getInputStream"), UnofficialMetadataAdvice.class.getName())
                )
                // Transform ImageDto#getImageUrl to resolve unofficial metadata images from local filesystem
                .type(ElementMatchers.<TypeDescription>named("com.lunii.luniistore.data.pack.model.ImageDto"))
                .transform(
                        new AgentBuilder.Transformer.ForAdvice()
                                .include(UnofficialImageAdvice.class.getClassLoader())
                                .advice(ElementMatchers.<MethodDescription>nameEndsWith("getImageUrl"), UnofficialImageAdvice.class.getName())
                )
                .installOn(instrumentation);
    }

    private static String getVersion() {
        final Properties properties = new Properties();
        try {
            properties.load(StudioAgent.class.getClassLoader().getResourceAsStream(AGENT_PROPERTIES));
            return properties.getProperty("version");
        } catch (IOException e) {
            LOGGER.error("Failed to read agent version.", e);
            return "unknown";
        }
    }

}
