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
public fun generateNewFile(fileName: String): File {
    val file = File("${getSaveFolder().absolutePath}/$fileName")
    file.createNewFile()
    return file
}
