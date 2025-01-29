package io.bambosan.mbloader

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Collections
import java.util.Objects
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val listener = findViewById<TextView>(R.id.listener)
        startLauncher(Handler(Looper.getMainLooper()), listener)
    }

    @SuppressLint("SetTextI18n")
    private fun startLauncher(handler: Handler, listener: TextView) {
        Executors.newSingleThreadExecutor().execute {
            try {
                val cacheDexDir = File(codeCacheDir, "dex")
                handleCacheCleaning(cacheDexDir, handler, listener)
                val mcInfo = packageManager.getApplicationInfo(
                    "com.mojang.minecraftpe",
                    PackageManager.GET_META_DATA
                )
                val pathList = getPathList(classLoader)
                processDexFiles(mcInfo, cacheDexDir, pathList!!, handler, listener, "launcher.dex")
                processNativeLibraries(mcInfo, pathList, handler, listener)
                launchMinecraft(mcInfo)
            } catch (e: Exception) {
                val fallbackActivity = Intent(
                    this,
                    Fallback::class.java
                )
                handleException(e, fallbackActivity)
                val logMessage = if (e.cause != null) e.cause.toString() else e.toString()
                handler.post { listener.text = "Launching failed: $logMessage" }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun handleCacheCleaning(cacheDexDir: File, handler: Handler, listener: TextView) {
        if (cacheDexDir.exists() && cacheDexDir.isDirectory) {
            handler.post {
                listener.text =
                    "-> " + cacheDexDir.absolutePath + " not empty, do cleaning"
            }
            for (file in Objects.requireNonNull(cacheDexDir.listFiles())) {
                if (file.delete()) {
                    handler.post {
                        listener.append(
                            """
-> ${file.name} deleted"""
                        )
                    }
                }
            }
        } else {
            handler.post {
                listener.text =
                    "-> " + cacheDexDir.absolutePath + " is empty, skip cleaning"
            }
        }
    }

    @Throws(Exception::class)
    private fun getPathList(classLoader: ClassLoader): Any? {
        val pathListField =
            Objects.requireNonNull(classLoader.javaClass.superclass).getDeclaredField("pathList")
        pathListField.isAccessible = true
        return pathListField[classLoader]
    }

    @Throws(Exception::class)
    private fun processDexFiles(
        mcInfo: ApplicationInfo,
        cacheDexDir: File,
        pathList: Any,
        handler: Handler,
        listener: TextView,
        launcherDexName: String
    ) {
        val addDexPath = pathList.javaClass.getDeclaredMethod(
            "addDexPath",
            String::class.java,
            File::class.java
        )
        val launcherDex = File(cacheDexDir, launcherDexName)

        copyFile(assets.open(launcherDexName), launcherDex)
        handler.post {
            listener.append(
                """
-> $launcherDexName copied to ${launcherDex.absolutePath}"""
            )
        }

        if (launcherDex.setReadOnly()) {
            addDexPath.invoke(pathList, launcherDex.absolutePath, null)
            handler.post { listener.append("\n-> $launcherDexName added to dex path list") }
        }

        ZipFile(mcInfo.sourceDir).use { zipFile ->
            for (i in 2 downTo 0) {
                val dexName = "classes" + (if (i == 0) "" else i) + ".dex"
                val dexFile = zipFile.getEntry(dexName)
                if (dexFile != null) {
                    val mcDex = File(cacheDexDir, dexName)
                    copyFile(zipFile.getInputStream(dexFile), mcDex)
                    handler.post {
                        listener.append(
                            """
-> ${mcInfo.sourceDir}/$dexName copied to ${mcDex.absolutePath}"""
                        )
                    }
                    if (mcDex.setReadOnly()) {
                        addDexPath.invoke(pathList, mcDex.absolutePath, null)
                        handler.post { listener.append("\n-> $dexName added to dex path list") }
                    }
                }
            }
        }
    }

    @Throws(Exception::class)
    private fun processNativeLibraries(
        mcInfo: ApplicationInfo,
        pathList: Any,
        handler: Handler,
        listener: TextView
    ) {
        val addNativePath = pathList.javaClass.getDeclaredMethod(
            "addNativePath",
            MutableCollection::class.java
        )
        val libDirList = ArrayList<String>()
        val libdir = File(mcInfo.nativeLibraryDir)
        if (libdir.list() == null || Objects.requireNonNull(libdir.listFiles()).isEmpty() || (mcInfo.flags and ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS) != ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS) {
            loadUnextractedLibs(mcInfo)
            libDirList.add("$dataDir/libs/")
        }
        libDirList.add(mcInfo.nativeLibraryDir)
        addNativePath.invoke(pathList, libDirList)
        handler.post {
            listener.append(
                """
-> ${mcInfo.nativeLibraryDir} added to native library directory path"""
            )
        }
    }

    @Throws(Exception::class)
    private fun loadUnextractedLibs(appInfo: ApplicationInfo) {
        val inStream = FileInputStream(getApkWithLibs(appInfo))
        val bufInStream = BufferedInputStream(inStream)
        val inZipStream = ZipInputStream(bufInStream)
        val zipPath = "lib/" + Build.SUPPORTED_ABIS[0] + "/"
        val outPath = applicationInfo.dataDir + "/libs/"
        val dir = File(outPath)
        dir.mkdir()
        extractDir(inZipStream, zipPath, outPath)
    }

    private fun getApkWithLibs(pkg: ApplicationInfo): String {
        val sn = pkg.splitSourceDirs
        if (!sn.isNullOrEmpty()) {
            val currentAbi = Build.SUPPORTED_ABIS[0].replace('-', '_')
            for (n in sn) {
                if (n.contains(currentAbi)) {
                    return n
                }
            }
        }
        return pkg.sourceDir
    }

    @Throws(ClassNotFoundException::class)
    private fun launchMinecraft(mcInfo: ApplicationInfo) {
        val launcherClass = classLoader.loadClass("com.mojang.minecraftpe.Launcher")
        val mcActivity = Intent(this, launcherClass)
        mcActivity.putExtra("MC_SRC", mcInfo.sourceDir)

        if (mcInfo.splitSourceDirs != null) {
            val listSrcSplit = ArrayList<String?>()
            mcInfo.splitSourceDirs?.let { Collections.addAll(listSrcSplit, *it) }
            mcActivity.putExtra("MC_SPLIT_SRC", listSrcSplit)
        }
        startActivity(mcActivity)
        finish()
    }

    private fun handleException(e: Exception, fallbackActivity: Intent) {
        val logMessage = if (e.cause != null) e.cause.toString() else e.toString()
        fallbackActivity.putExtra("LOG_STR", logMessage)
        startActivity(fallbackActivity)
        finish()
    }

    companion object {
        @Throws(Exception::class)
        private fun extractDir(zip: ZipInputStream, zipFolder: String, outFolder: String) {
            var ze: ZipEntry
            while ((zip.nextEntry.also { ze = it }) != null) {
                if (ze.name.startsWith(zipFolder)) {
                    val strippedName = ze.name.substring(zipFolder.length)
                    val path = outFolder + strippedName
                    val out = Files.newOutputStream(Paths.get(path))
                    val outBuf = BufferedOutputStream(out)
                    val buffer = ByteArray(9000)
                    var len: Int
                    while ((zip.read(buffer).also { len = it }) != -1) {
                        outBuf.write(buffer, 0, len)
                    }
                    outBuf.close()
                }
            }
            zip.close()
        }

        @Throws(IOException::class)
        private fun copyFile(from: InputStream, to: File) {
            val parentDir = to.parentFile
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                throw IOException("Failed to create directories")
            }
            if (!to.exists() && !to.createNewFile()) {
                throw IOException("Failed to create new file")
            }
            BufferedInputStream(from).use { input ->
                BufferedOutputStream(Files.newOutputStream(to.toPath())).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while ((input.read(buffer).also { bytesRead = it }) != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
        }
    }
}
