package tech.bananajuice.convertpixelart

object RustCore {
    init {
        System.loadLibrary("android_lib")
    }

    external fun convertFile(inputPath: String, outputPath: String, timelapse: Boolean): Int
}
