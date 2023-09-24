package io.quarkus.awt.deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import io.quarkus.awt.runtime.graal.AwtFeature;
import io.quarkus.awt.runtime.graal.DarwinAwtFeature;
import io.quarkus.awt.runtime.graal.WindowsAwtFeature;
import io.quarkus.deployment.Feature;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.NativeImageFeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.JniRuntimeAccessBuildItem;
import io.quarkus.deployment.builditem.nativeimage.JniRuntimeAccessFieldBuildItem;
import io.quarkus.deployment.builditem.nativeimage.JniRuntimeAccessMethodBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeMinimalJavaVersionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedPackageBuildItem;
import io.quarkus.deployment.pkg.builditem.NativeImageRunnerBuildItem;
import io.quarkus.deployment.pkg.steps.GraalVM;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;

class AwtProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(Feature.AWT);
    }

    // k: win & awt
    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void nativeImageFeatures(BuildProducer<NativeImageFeatureBuildItem> nativeImageFeatures) {
        // k: nativeImageFeatures.produce(new NativeImageFeatureBuildItem(AwtFeature.class));
        nativeImageFeatures.produce(new NativeImageFeatureBuildItem(DarwinAwtFeature.class));
        nativeImageFeatures.produce(new NativeImageFeatureBuildItem(WindowsAwtFeature.class));
    }

    // k: comment to allow Win
    //    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    //    UnsupportedOSBuildItem osSupportCheck() {
    //        return new UnsupportedOSBuildItem(WINDOWS,
    //                "Windows AWT integration is not ready in native-image and would result in " +
    //                        "java.lang.UnsatisfiedLinkError: no awt in java.library.path.");
    //    }

    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    NativeMinimalJavaVersionBuildItem nativeMinimalJavaVersionBuildItem() {
        return new NativeMinimalJavaVersionBuildItem(11, 13,
                "AWT: Some MLib related operations, such as filter in awt.image.ConvolveOp will not work. " +
                        "See https://bugs.openjdk.java.net/browse/JDK-8254024");
    }

    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void resources(
            BuildProducer<NativeImageResourcePatternsBuildItem> resourcePatternsBuildItemBuildProducer) {
        resourcePatternsBuildItemBuildProducer
                .produce(NativeImageResourcePatternsBuildItem.builder() //
                        .includePattern(".*/iio-plugin.*properties$") // Texts for e.g. exceptions strings
                        .includePattern(".*/.*pf$") // Default colour profiles
                        .build());
    }

    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    ReflectiveClassBuildItem setupReflectionClasses() {
        return ReflectiveClassBuildItem.builder( //
                "com.sun.imageio.plugins.common.I18N",
                "sun.awt.X11.XToolkit",
                "sun.awt.X11FontManager",
                "sun.awt.X11GraphicsEnvironment",
                // k: macos
                "sun.lwawt.macosx.LWCToolkit",
                "com.apple.eawt.Application" //
        ).build();
    }

    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    ReflectiveClassBuildItem setupReflectionClassesWithMethods() {
        return ReflectiveClassBuildItem.builder( //
                "javax.imageio.plugins.tiff.BaselineTIFFTagSet",
                "javax.imageio.plugins.tiff.ExifGPSTagSet",
                "javax.imageio.plugins.tiff.ExifInteroperabilityTagSet",
                "javax.imageio.plugins.tiff.ExifParentTIFFTagSet",
                "javax.imageio.plugins.tiff.ExifTIFFTagSet",
                "javax.imageio.plugins.tiff.FaxTIFFTagSet",
                "javax.imageio.plugins.tiff.GeoTIFFTagSet",
                "javax.imageio.plugins.tiff.TIFFTagSet",
                "sun.java2d.loops.OpaqueCopyAnyToArgb",
                "sun.java2d.loops.OpaqueCopyArgbToAny",
                "sun.java2d.loops.SetDrawLineANY",
                "sun.java2d.loops.SetDrawPathANY",
                "sun.java2d.loops.SetDrawPolygonsANY",
                "sun.java2d.loops.SetDrawRectANY",
                "sun.java2d.loops.SetFillPathANY",
                "sun.java2d.loops.SetFillRectANY",
                "sun.java2d.loops.SetFillSpansANY" //
        ).methods().build();
    }

    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void setupAWTInit(BuildProducer<JniRuntimeAccessBuildItem> jc,
            BuildProducer<JniRuntimeAccessMethodBuildItem> jm,
            BuildProducer<JniRuntimeAccessFieldBuildItem> jf,
            NativeImageRunnerBuildItem nativeImageRunnerBuildItem) {
        final GraalVM.Version v = nativeImageRunnerBuildItem.getBuildRunner().getGraalVMVersion();
        // Dynamically loading shared objects instead
        // of baking in static libs: https://github.com/oracle/graal/issues/4921
        if (v.compareTo(GraalVM.Version.VERSION_23_0_0) >= 0) {
            jm.produce(new JniRuntimeAccessMethodBuildItem("java.lang.System", "load", "java.lang.String"));
            jm.produce(new JniRuntimeAccessMethodBuildItem("java.lang.System", "setProperty", "java.lang.String", "java.lang.String"));
            jm.produce(new JniRuntimeAccessMethodBuildItem("sun.awt.SunToolkit", "awtLock"));
            jm.produce(new JniRuntimeAccessMethodBuildItem("sun.awt.SunToolkit", "awtLockNotify"));
            jm.produce(new JniRuntimeAccessMethodBuildItem("sun.awt.SunToolkit", "awtLockNotifyAll"));
            jm.produce(new JniRuntimeAccessMethodBuildItem("sun.awt.SunToolkit", "awtLockWait", "long"));
            jm.produce(new JniRuntimeAccessMethodBuildItem("sun.awt.SunToolkit", "awtUnlock"));
            jf.produce(new JniRuntimeAccessFieldBuildItem("sun.awt.SunToolkit", "AWT_LOCK"));
            jf.produce(new JniRuntimeAccessFieldBuildItem("sun.awt.SunToolkit", "AWT_LOCK_COND"));
            jm.produce(new JniRuntimeAccessMethodBuildItem("sun.awt.X11.XErrorHandlerUtil", "init", "long"));
            jc.produce(new JniRuntimeAccessBuildItem(false, false, true, "sun.awt.X11.XToolkit"));
            jm.produce(new JniRuntimeAccessMethodBuildItem("java.lang.Thread", "yield"));
        }
    }

    /* 
    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    JniRuntimeAccessBuildItem setupJava2DClasses() {
        return new JniRuntimeAccessBuildItem(true, true, true,
                "com.sun.imageio.plugins.jpeg.JPEGImageReader",
                "com.sun.imageio.plugins.jpeg.JPEGImageWriter",
                "java.awt.AlphaComposite",
                "java.awt.Color",
                "java.awt.color.CMMException",
                "java.awt.color.ColorSpace",
                "java.awt.color.ICC_Profile",
                "java.awt.color.ICC_ProfileGray",
                "java.awt.color.ICC_ProfileRGB",
                "java.awt.geom.AffineTransform",
                "java.awt.geom.GeneralPath",
                "java.awt.geom.Path2D",
                "java.awt.geom.Path2D$Float",
                "java.awt.geom.Point2D$Float",
                "java.awt.geom.Rectangle2D$Float",
                "java.awt.image.AffineTransformOp",
                "java.awt.image.BandedSampleModel",
                "java.awt.image.BufferedImage",
                "java.awt.image.ColorModel",
                "java.awt.image.ComponentColorModel",
                "java.awt.image.ComponentSampleModel",
                "java.awt.image.ConvolveOp",
                "java.awt.image.DirectColorModel",
                "java.awt.image.IndexColorModel",
                "java.awt.image.Kernel",
                "java.awt.image.MultiPixelPackedSampleModel",
                "java.awt.image.PackedColorModel",
                "java.awt.image.PixelInterleavedSampleModel",
                "java.awt.image.Raster",
                "java.awt.image.SampleModel",
                "java.awt.image.SinglePixelPackedSampleModel",
                "java.awt.Transparency",
                "javax.imageio.IIOException",
                "javax.imageio.plugins.jpeg.JPEGHuffmanTable",
                "javax.imageio.plugins.jpeg.JPEGQTable",
                "sun.awt.image.BufImgSurfaceData",
                "sun.awt.image.BufImgSurfaceData$ICMColorData",
                "sun.awt.image.ByteBandedRaster",
                "sun.awt.image.ByteComponentRaster",
                "sun.awt.image.ByteInterleavedRaster",
                "sun.awt.image.BytePackedRaster",
                "sun.awt.image.DataBufferNative",
                "sun.awt.image.GifImageDecoder",
                "sun.awt.image.ImageRepresentation",
                "sun.awt.image.ImagingLib",
                "sun.awt.image.IntegerComponentRaster",
                "sun.awt.image.IntegerInterleavedRaster",
                "sun.awt.image.ShortBandedRaster",
                "sun.awt.image.ShortComponentRaster",
                "sun.awt.image.ShortInterleavedRaster",
                "sun.awt.image.SunWritableRaster",
                "sun.awt.image.WritableRasterNative",
                "sun.awt.SunHints",
                "sun.font.CharToGlyphMapper",
                "sun.font.Font2D",
                "sun.font.FontConfigManager",
                "sun.font.FontManagerNativeLibrary",
                "sun.font.FontStrike",
                // Added for JDK 19+ due to: https://github.com/openjdk/jdk20/commit/9bc023220 calling FontUtilities
                "sun.font.FontUtilities",
                "sun.font.FreetypeFontScaler",
                "sun.font.GlyphLayout",
                "sun.font.GlyphLayout$EngineRecord",
                "sun.font.GlyphLayout$GVData",
                "sun.font.GlyphLayout$LayoutEngine",
                "sun.font.GlyphLayout$LayoutEngineFactory",
                "sun.font.GlyphLayout$LayoutEngineKey",
                "sun.font.GlyphLayout$SDCache",
                "sun.font.GlyphLayout$SDCache$SDKey",
                "sun.font.GlyphList",
                "sun.font.PhysicalStrike",
                "sun.font.StrikeMetrics",
                "sun.font.TrueTypeFont",
                "sun.font.Type1Font",
                "sun.java2d.cmm.lcms.LCMSImageLayout",
                "sun.java2d.cmm.lcms.LCMSProfile",
                "sun.java2d.cmm.lcms.LCMSTransform",
                "sun.java2d.DefaultDisposerRecord",
                "sun.java2d.Disposer",
                "sun.java2d.InvalidPipeException",
                "sun.java2d.loops.Blit",
                "sun.java2d.loops.BlitBg",
                "sun.java2d.loops.CompositeType",
                "sun.java2d.loops.DrawGlyphList",
                "sun.java2d.loops.DrawGlyphListAA",
                "sun.java2d.loops.DrawGlyphListLCD",
                "sun.java2d.loops.DrawLine",
                "sun.java2d.loops.DrawParallelogram",
                "sun.java2d.loops.DrawPath",
                "sun.java2d.loops.DrawPolygons",
                "sun.java2d.loops.DrawRect",
                "sun.java2d.loops.FillParallelogram",
                "sun.java2d.loops.FillPath",
                "sun.java2d.loops.FillRect",
                "sun.java2d.loops.FillSpans",
                "sun.java2d.loops.GraphicsPrimitive",
                "sun.java2d.loops.GraphicsPrimitiveMgr",
                "sun.java2d.loops.MaskBlit",
                "sun.java2d.loops.MaskFill",
                "sun.java2d.loops.ScaledBlit",
                "sun.java2d.loops.SurfaceType",
                "sun.java2d.loops.TransformHelper",
                "sun.java2d.loops.XORComposite",
                "sun.java2d.NullSurfaceData",
                "sun.java2d.pipe.BufferedMaskBlit",
                "sun.java2d.pipe.GlyphListPipe",
                "sun.java2d.pipe.Region",
                "sun.java2d.pipe.RegionIterator",
                "sun.java2d.pipe.ShapeSpanIterator",
                "sun.java2d.pipe.SpanClipRenderer",
                "sun.java2d.pipe.ValidatePipe",
                "sun.java2d.SunGraphics2D",
                "sun.java2d.SurfaceData",
                // Java 20 :test
                "javax.imageio.ImageIO",
                "javax.imageio.spi.ImageReaderSpi",
                "javax.imageio.spi.ImageWriterSpi",
                "javax.imageio.stream.FileImageInputStream",
                "javax.imageio.stream.FileImageOutputStream",
                "com.sun.imageio.plugins.jpeg.JPEGImageWriterSpi",
                "com.sun.imageio.plugins.jpeg.JPEGImageReaderSpi",
                "com.sun.imageio.spi.RAFImageInputStreamSpi",
                "com.sun.imageio.spi.FileImageOutputStreamSpi"
                //
                );
    }
    */

    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    JniRuntimeAccessBuildItem setupJava2DClasses(NativeImageRunnerBuildItem nativeImageRunnerBuildItem) {
        final GraalVM.Version v = nativeImageRunnerBuildItem.getBuildRunner().getGraalVMVersion();
        final List<String> classes = new ArrayList<>();
        classes.add("com.sun.imageio.plugins.jpeg.JPEGImageReader");
        classes.add("com.sun.imageio.plugins.jpeg.JPEGImageWriter");
        classes.add("java.awt.AlphaComposite");
        classes.add("java.awt.Color");
        classes.add("java.awt.color.CMMException");
        classes.add("java.awt.color.ColorSpace");
        classes.add("java.awt.color.ICC_ColorSpace");
        classes.add("java.awt.color.ICC_Profile");
        classes.add("java.awt.color.ICC_ProfileGray");
        classes.add("java.awt.color.ICC_ProfileRGB");
        classes.add("java.awt.Composite");
        classes.add("java.awt.geom.AffineTransform");
        classes.add("java.awt.geom.GeneralPath");
        classes.add("java.awt.geom.Path2D");
        classes.add("java.awt.geom.Path2D$Float");
        classes.add("java.awt.geom.Point2D$Float");
        classes.add("java.awt.geom.Rectangle2D$Float");
        classes.add("java.awt.image.AffineTransformOp");
        classes.add("java.awt.image.BandedSampleModel");
        classes.add("java.awt.image.BufferedImage");
        classes.add("java.awt.image.ColorModel");
        classes.add("java.awt.image.ComponentColorModel");
        classes.add("java.awt.image.ComponentSampleModel");
        classes.add("java.awt.image.ConvolveOp");
        classes.add("java.awt.image.DirectColorModel");
        classes.add("java.awt.image.IndexColorModel");
        classes.add("java.awt.image.Kernel");
        classes.add("java.awt.image.MultiPixelPackedSampleModel");
        classes.add("java.awt.image.PackedColorModel");
        classes.add("java.awt.image.PixelInterleavedSampleModel");
        classes.add("java.awt.image.Raster");
        classes.add("java.awt.image.SampleModel");
        classes.add("java.awt.image.SinglePixelPackedSampleModel");
        classes.add("java.awt.Rectangle");
        classes.add("java.awt.Transparency");
        classes.add("javax.imageio.IIOException");
        classes.add("javax.imageio.plugins.jpeg.JPEGHuffmanTable");
        classes.add("javax.imageio.plugins.jpeg.JPEGQTable");
        classes.add("sun.awt.image.BufImgSurfaceData");
        classes.add("sun.awt.image.BufImgSurfaceData$ICMColorData");
        classes.add("sun.awt.image.ByteBandedRaster");
        classes.add("sun.awt.image.ByteComponentRaster");
        classes.add("sun.awt.image.ByteInterleavedRaster");
        classes.add("sun.awt.image.BytePackedRaster");
        classes.add("sun.awt.image.DataBufferNative");
        classes.add("sun.awt.image.GifImageDecoder");
        classes.add("sun.awt.image.ImageRepresentation");
        classes.add("sun.awt.image.ImagingLib");
        classes.add("sun.awt.image.IntegerComponentRaster");
        classes.add("sun.awt.image.IntegerInterleavedRaster");
        classes.add("sun.awt.image.ShortBandedRaster");
        classes.add("sun.awt.image.ShortComponentRaster");
        classes.add("sun.awt.image.ShortInterleavedRaster");
        classes.add("sun.awt.image.SunWritableRaster");
        classes.add("sun.awt.image.WritableRasterNative");
        classes.add("sun.awt.SunHints");
        classes.add("sun.font.CharToGlyphMapper");
        classes.add("sun.font.Font2D");
        classes.add("sun.font.FontConfigManager");
        classes.add("sun.font.FontConfigManager$FcCompFont");
        classes.add("sun.font.FontConfigManager$FontConfigFont");
        classes.add("sun.font.FontConfigManager$FontConfigInfo");
        classes.add("sun.font.FontManagerNativeLibrary");
        classes.add("sun.font.FontStrike");
        classes.add("sun.font.FreetypeFontScaler");
        classes.add("sun.font.GlyphLayout");
        classes.add("sun.font.GlyphLayout$EngineRecord");
        classes.add("sun.font.GlyphLayout$GVData");
        classes.add("sun.font.GlyphLayout$LayoutEngine");
        classes.add("sun.font.GlyphLayout$LayoutEngineFactory");
        classes.add("sun.font.GlyphLayout$LayoutEngineKey");
        classes.add("sun.font.GlyphLayout$SDCache");
        classes.add("sun.font.GlyphLayout$SDCache$SDKey");
        classes.add("sun.font.GlyphList");
        classes.add("sun.font.PhysicalStrike");
        classes.add("sun.font.StrikeMetrics");
        classes.add("sun.font.TrueTypeFont");
        classes.add("sun.font.Type1Font");
        classes.add("sun.java2d.cmm.lcms.LCMS");
        classes.add("sun.java2d.cmm.lcms.LCMSImageLayout");
        classes.add("sun.java2d.cmm.lcms.LCMSProfile");
        classes.add("sun.java2d.cmm.lcms.LCMSTransform");
        classes.add("sun.java2d.DefaultDisposerRecord");
        classes.add("sun.java2d.Disposer");
        classes.add("sun.java2d.InvalidPipeException");
        classes.add("sun.java2d.loops.Blit");
        classes.add("sun.java2d.loops.BlitBg");
        classes.add("sun.java2d.loops.CompositeType");
        classes.add("sun.java2d.loops.DrawGlyphList");
        classes.add("sun.java2d.loops.DrawGlyphListAA");
        classes.add("sun.java2d.loops.DrawGlyphListLCD");
        classes.add("sun.java2d.loops.DrawLine");
        classes.add("sun.java2d.loops.DrawParallelogram");
        classes.add("sun.java2d.loops.DrawPath");
        classes.add("sun.java2d.loops.DrawPolygons");
        classes.add("sun.java2d.loops.DrawRect");
        classes.add("sun.java2d.loops.FillParallelogram");
        classes.add("sun.java2d.loops.FillPath");
        classes.add("sun.java2d.loops.FillRect");
        classes.add("sun.java2d.loops.FillSpans");
        classes.add("sun.java2d.loops.GraphicsPrimitive");
        classes.add("sun.java2d.loops.GraphicsPrimitiveMgr");
        classes.add("sun.java2d.loops.MaskBlit");
        classes.add("sun.java2d.loops.MaskFill");
        classes.add("sun.java2d.loops.ScaledBlit");
        classes.add("sun.java2d.loops.SurfaceType");
        classes.add("sun.java2d.loops.TransformHelper");
        classes.add("sun.java2d.loops.XORComposite");
        classes.add("sun.java2d.NullSurfaceData");
        classes.add("sun.java2d.pipe.BufferedMaskBlit");
        classes.add("sun.java2d.pipe.GlyphListPipe");
        classes.add("sun.java2d.pipe.Region");
        classes.add("sun.java2d.pipe.RegionIterator");
        classes.add("sun.java2d.pipe.ShapeSpanIterator");
        classes.add("sun.java2d.pipe.SpanClipRenderer");
        classes.add("sun.java2d.pipe.SpanIterator");
        classes.add("sun.java2d.pipe.ValidatePipe");
        classes.add("sun.java2d.SunGraphics2D");
        classes.add("sun.java2d.SurfaceData");

        // A new way of dynamically loading shared objects instead
        // of baking in static libs: https://github.com/oracle/graal/issues/4921
        if (v.compareTo(GraalVM.Version.VERSION_23_0_0) >= 0) {
            classes.add("sun.awt.X11FontManager");
            if (v.javaFeatureVersion != 19) {
                classes.add("java.awt.GraphicsEnvironment");
                classes.add("sun.awt.X11GraphicsConfig");
                classes.add("sun.awt.X11GraphicsDevice");
                classes.add("sun.java2d.SunGraphicsEnvironment");
                classes.add("sun.java2d.xr.XRSurfaceData");
            }
        }

        // Added for JDK 19+ due to: https://github.com/openjdk/jdk20/commit/9bc023220 calling FontUtilities
        if (v.jdkVersionGreaterOrEqualTo(19, 0)) {
            classes.add("sun.font.FontUtilities");
        }

        return new JniRuntimeAccessBuildItem(true, true, true, classes.toArray(new String[0]));
    }

    /*
     * Moved over here due to: https://github.com/quarkusio/quarkus/pull/32069
     * A better detection and DarwinAwtFeature handling might be in order.
     */
    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void runtimeInitializedClasses(BuildProducer<RuntimeInitializedPackageBuildItem> runtimeInitilizedPackages) {
        /*
         * Note that this initialization is not enough if user wants to deserialize actual images
         * (e.g. from XML). AWT Extension must be loaded for decoding JDK supported image formats.
         */
        Stream.of(
                "com.sun.imageio",
                "java.awt",
                "javax.imageio",
                "sun.awt",
                "sun.font",
                "sun.java2d")
                .map(RuntimeInitializedPackageBuildItem::new)
                .forEach(runtimeInitilizedPackages::produce);
    }

}
