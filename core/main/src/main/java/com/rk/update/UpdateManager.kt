package com.rk.update

import com.rk.libcommons.application
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.localBinDir
import java.io.File

class UpdateManager {
    fun onUpdate(){
        val initFilex: File = localBinDir().child("init")
        if(initFilex.exists()){
            initFilex.delete()
        }

        writeAssetScript("init.sh", initFilex)
    }

    private fun writeAssetScript(assetName: String, target: File) {
        target.createFileIfNot()
        target.writeText(application!!.assets.open(assetName).bufferedReader().use { it.readText() }.replace("\r\n", "\n"))
        target.setExecutable(true, false)
    }
}
