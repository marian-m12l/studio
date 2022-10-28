package io.quarkiverse.usb4java.deployment;

import static io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem.allProvidersFromClassPath;

import java.io.IOException;
import java.util.stream.Stream; 
import org.jboss.logging.Logger;
import io.quarkiverse.usb4java.runtime.Usb4javaRecorder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.JniRuntimeAccessBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.deployment.pkg.NativeConfig;

class Usb4javaProcessor {

    private static final String FEATURE = "usb4java";
    private static final String PKG = "org.usb4java.";

    private static final Logger LOG = Logger.getLogger(Usb4javaProcessor.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void nativeJni(NativeConfig nativeConfig,
            BuildProducer<NativeImageResourceBuildItem> nativeResources,
            BuildProducer<JniRuntimeAccessBuildItem> jniAccess,
            BuildProducer<RuntimeInitializedClassBuildItem> runtimeClasses) throws IOException {
        // native binary path
        String src;
        if (nativeConfig.isContainerBuild()) {
            src = Usb4javaLibraryUtil.extractLibrary("linux-x86-64", "libusb4java.so");
        } else {
            src = Usb4javaLibraryUtil.extractLibrary();
        }
        LOG.info("Libusb: " + src);
        nativeResources.produce(new NativeImageResourceBuildItem(src));

        // We don't want to load usb4java during build
        String[] classes = {
                "BosDescriptor",
                "BosDevCapabilityDescriptor",
                "BufferUtils",
                "ConfigDescriptor",
                "ContainerIdDescriptor",
                "Context",
                "ControlSetup",
                "DescriptorUtils",
                "Device",
                "DeviceDescriptor",
                "DeviceHandle",
                "DeviceList",
                "DeviceListIterator",
                "EndpointDescriptor",
                "HotplugCallback",
                "HotplugCallbackHandle",
                "Interface",
                "InterfaceDescriptor",
                "IsoPacketDescriptor",
                "LibUsb",
                "LibUsbException",
                "Loader",
                "LoaderException",
                "Pollfd",
                "PollfdListener",
                "Pollfds",
                "PollfdsIterator",
                "SsEndpointCompanionDescriptor",
                "SsUsbDeviceCapabilityDescriptor",
                "Transfer",
                "TransferCallback",
                "Usb20ExtensionDescriptor",
                "Version"
        };

        for (String s : classes) {
            String fullName = PKG + s;
            jniAccess.produce(new JniRuntimeAccessBuildItem(true, true, true, fullName));
            runtimeClasses.produce(new RuntimeInitializedClassBuildItem(fullName));
        }
    }

    @BuildStep
    void audioServiceLoader(BuildProducer<ServiceProviderBuildItem> serviceProviders) {
        // need local META-INF/services/ because native can't load java.desktop module
        // services implementation list copied from module-info.java
        serviceProviders.produce(allProvidersFromClassPath("javax.sound.sampled.spi.AudioFileReader"));
        serviceProviders.produce(allProvidersFromClassPath("javax.sound.sampled.spi.AudioFileWriter"));
        serviceProviders.produce(allProvidersFromClassPath("javax.sound.sampled.spi.FormatConversionProvider"));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void loadUsb4java(Usb4javaRecorder recorder) {
        // Explicitly load usb4java at application startup.
        recorder.loadUsb4javaLibrary();
    }
}
