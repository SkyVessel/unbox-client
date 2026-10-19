plugins {
    id("fuzs.multiloader.multiloader-convention-plugins-neoforge")
}

dependencies {
    compileOnly(files("../../puzzleslib/NeoForge/build/libs/puzzleslib-v26.1.16-mc26.1.x+neoforge.jar"))
}

neoForge { accessTransformers.from(file("../../puzzleslib/Common/build/generated/resources/META-INF/accesstransformer.cfg")) }
