package com.example.cargomanifestapp

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup penuh Stowing Cargo + foto BTB.
 *
 * Format ZIP:
 *   cargo_backup.json
 *   photos/<nama_file.jpg>
 *
 * JSON menyimpan data Cargo dan mapping setiap cargoKey ke nama file foto.
 * Saat restore URI lama TIDAK dipakai lagi; URI baru dibuat dari FileProvider
 * aplikasi sehingga aman dipindahkan ke HP lain.
 */
object CargoBackupManager {
    private const val BACKUP_FILE = "cargo_backup.json"
    private const val PHOTOS_DIR = "photos/"
    private const val BACKUP_VERSION = 1

    fun exportBackup(context: Context, destination: Uri, cargoItems: List<CargoItem>) {
        val photoPrefs = context.getSharedPreferences("cargo_photos", Context.MODE_PRIVATE)
        val allPhotos = JSONObject(photoPrefs.getString("items", "{}") ?: "{}")
        val mapping = JSONObject()
        val photoFiles = linkedMapOf<String, File>()

        for (item in cargoItems) {
            val key = cargoKey(item)
            val array = allPhotos.optJSONArray(key) ?: continue
            val names = JSONArray()
            for (i in 0 until array.length()) {
                val uriString = array.optString(i).trim()
                val file = BtbPhotoStorage.resolvePhotoFile(context, uriString) ?: continue
                if (!file.exists() || !file.isFile) continue

                var uniqueName = file.name
                if (photoFiles.containsKey(uniqueName) && photoFiles[uniqueName]?.canonicalPath != file.canonicalPath) {
                    uniqueName = "${System.currentTimeMillis()}_${i}_${file.name}"
                }
                photoFiles[uniqueName] = file
                names.put(uniqueName)
            }
            if (names.length() > 0) mapping.put(key, names)
        }

        val root = JSONObject().apply {
            put("format", "Cargo Manifest Backup")
            put("version", BACKUP_VERSION)
            put("createdAt", System.currentTimeMillis())
            put("cargo", JSONArray().apply {
                cargoItems.forEach { item ->
                    put(JSONObject().apply {
                        put("noPag", item.noPag)
                        put("customer", item.customer)
                        put("description", item.description)
                        put("pti", item.pti)
                        put("pcsQty", item.pcsQty)
                        put("weight", item.weight)
                        put("subTotal", item.subTotal)
                    })
                }
            })
            put("photoMapping", mapping)
            // Loot Crew ikut dibackup. Data Stowing/BTB tetap master dan tidak dikurangi.
            put("crewLoot", context.getSharedPreferences("crew_loot_storage", Context.MODE_PRIVATE)
                .getString("transactions", "[]") ?: "[]")
            // Simpan pengaturan Manifest yang relevan (target loot, prioritas, WMX).
            val settings = context.getSharedPreferences("manifest_settings", Context.MODE_PRIVATE)
            put("manifestSettings", JSONObject().apply {
                put("target_loot_kg", settings.getString("target_loot_kg", "0") ?: "0")
                put("customer_priority_order", settings.getString("customer_priority_order", "") ?: "")
                put("wmx_saved_senders", settings.getString("wmx_saved_senders", "") ?: "")
            })
        }

        context.contentResolver.openOutputStream(destination)?.use { output ->
            ZipOutputStream(output.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(BACKUP_FILE))
                zip.write(root.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                photoFiles.forEach { (name, file) ->
                    zip.putNextEntry(ZipEntry(PHOTOS_DIR + name))
                    FileInputStream(file).use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: error("Tidak dapat membuka lokasi penyimpanan backup")
    }

    /**
     * Restore mengganti seluruh daftar Cargo aktif dengan isi backup.
     * Foto lama dibersihkan agar mapping tidak bercampur dengan backup baru.
     */
    fun restoreBackup(context: Context, source: Uri): Int {
        val tempDir = File(context.cacheDir, "cargo_restore_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name.replace('\\', '/')
                        if (!entry.isDirectory && (name == BACKUP_FILE || name.startsWith(PHOTOS_DIR))) {
                            val target = File(tempDir, name)
                            val canonicalRoot = tempDir.canonicalFile
                            val canonicalTarget = target.canonicalFile
                            if (canonicalTarget.path.startsWith(canonicalRoot.path + File.separator)) {
                                canonicalTarget.parentFile?.mkdirs()
                                FileOutputStream(canonicalTarget).use { output -> zip.copyTo(output) }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("Tidak dapat membaca file backup")

            val jsonFile = File(tempDir, BACKUP_FILE)
            require(jsonFile.exists()) { "File bukan Backup Cargo Manifest yang valid" }
            val root = JSONObject(jsonFile.readText(Charsets.UTF_8))
            require(root.optString("format") == "Cargo Manifest Backup") { "Format backup tidak dikenali" }

            val cargoArray = root.optJSONArray("cargo") ?: JSONArray()
            val cargo = mutableListOf<CargoItem>()
            for (i in 0 until cargoArray.length()) {
                val obj = cargoArray.optJSONObject(i) ?: continue
                cargo.add(
                    CargoItem(
                        noPag = obj.optString("noPag"),
                        customer = obj.optString("customer"),
                        description = obj.optString("description"),
                        pti = obj.optString("pti"),
                        pcsQty = obj.optString("pcsQty"),
                        weight = obj.optString("weight"),
                        subTotal = obj.optString("subTotal")
                    )
                )
            }

            // Bersihkan foto/mapping lama, lalu restore persis isi backup.
            BtbPhotoStorage.clearAllPhotos(context)
            context.getSharedPreferences("cargo_photos", Context.MODE_PRIVATE).edit().clear().apply()

            val restoredMapping = JSONObject()
            val mapping = root.optJSONObject("photoMapping") ?: JSONObject()
            val targetPhotoDir = BtbPhotoStorage.getPhotoDirectory(context)

            val keys = mapping.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val names = mapping.optJSONArray(key) ?: continue
                val restoredUris = JSONArray()
                for (i in 0 until names.length()) {
                    val name = names.optString(i).trim()
                    if (name.isBlank() || name.contains("/") || name.contains("\\")) continue
                    val sourceFile = File(tempDir, PHOTOS_DIR + name)
                    if (!sourceFile.exists() || !sourceFile.isFile) continue

                    var finalName = name
                    var targetFile = File(targetPhotoDir, finalName)
                    var counter = 1
                    while (targetFile.exists()) {
                        finalName = "${counter++}_${name}"
                        targetFile = File(targetPhotoDir, finalName)
                    }
                    sourceFile.copyTo(targetFile, overwrite = false)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", targetFile)
                    restoredUris.put(uri.toString())
                }
                if (restoredUris.length() > 0) restoredMapping.put(key, restoredUris)
            }

            val cargoJson = JSONArray().apply {
                cargo.forEach { item ->
                    put(JSONObject().apply {
                        put("noPag", item.noPag)
                        put("customer", item.customer)
                        put("description", item.description)
                        put("pti", item.pti)
                        put("pcsQty", item.pcsQty)
                        put("weight", item.weight)
                        put("subTotal", item.subTotal)
                    })
                }
            }

            context.getSharedPreferences("stowing_prefs", Context.MODE_PRIVATE).edit()
                .putString("saved_cargo_list", cargoJson.toString()).apply()
            context.getSharedPreferences("cargo_photos", Context.MODE_PRIVATE).edit()
                .putString("items", restoredMapping.toString()).apply()
            context.getSharedPreferences("stowing_draft", Context.MODE_PRIVATE).edit().clear().apply()

            // Restore Loot Crew dan pengaturan Manifest bila tersedia.
            val crewLootRaw = root.optString("crewLoot", "[]")
            context.getSharedPreferences("crew_loot_storage", Context.MODE_PRIVATE).edit()
                .putString("transactions", crewLootRaw).apply()
            val manifestSettings = root.optJSONObject("manifestSettings")
            if (manifestSettings != null) {
                context.getSharedPreferences("manifest_settings", Context.MODE_PRIVATE).edit()
                    .putString("target_loot_kg", manifestSettings.optString("target_loot_kg", "0"))
                    .putString("customer_priority_order", manifestSettings.optString("customer_priority_order", ""))
                    .putString("wmx_saved_senders", manifestSettings.optString("wmx_saved_senders", ""))
                    .apply()
            }

            return cargo.size
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun cargoKey(item: CargoItem): String =
        listOf(item.noPag, item.customer, item.description, item.pti, item.pcsQty, item.weight, item.subTotal)
            .joinToString("\u001F") { it.trim().uppercase() }
}
