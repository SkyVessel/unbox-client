plugins {
    id("fuzs.multiloader.multiloader-convention-plugins-common")
}

dependencies {
    compileOnly(files("../../puzzleslib/Common/build/libs/puzzleslib-v26.1.16-mc26.1.x+common.jar"))
}

multiloader {
    mixins {
        mixin("AbstractContainerMenuMixin")
        clientMixin(
            "AbstractContainerScreenMixin",
            "GuiGraphicsExtractorMixin",
            "ItemStackMixin",
            "MultiPlayerGameModeMixin"
        )
    }
}

neoForge { accessTransformers.from(file("../../puzzleslib/Common/build/generated/resources/META-INF/accesstransformer.cfg")) }
