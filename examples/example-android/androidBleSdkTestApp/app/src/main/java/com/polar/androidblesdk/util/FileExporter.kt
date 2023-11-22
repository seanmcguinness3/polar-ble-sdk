package com.polar.androidblesdk.util
import android.os.Environment
import android.util.Log
import com.opencsv.CSVWriter
import java.io.File

class FileExporter{


}
fun getSaveFolder(subFolder: String = ""):File{
    val documentFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    val resultsFolder = File(documentFolder, "BLESensorData/" + subFolder)
    resultsFolder.mkdir()
    return resultsFolder
}
public fun generateNewFile(): File {
    val fileName = "ass.txt"
    //val file = File(fileName)
    val file = File("${getSaveFolder().absolutePath}/test_$fileName")
    file.createNewFile()
    return file
}
