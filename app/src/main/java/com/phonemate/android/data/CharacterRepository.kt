package com.phonemate.android.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.phonemate.android.R
import com.phonemate.android.domain.CharacterDefinition
import com.phonemate.android.domain.CharacterImageSource
import com.phonemate.android.domain.CharacterState
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class CharacterRepository(context: Context) {
    private val appContext = context.applicationContext
    private val rootDirectory = File(appContext.filesDir, "custom-characters")
    private val indexFile = File(rootDirectory, "characters.json")

    fun loadCharacters(): List<CharacterDefinition> {
        return listOf(BuiltInBlob) + loadCustomRecords()
            .mapNotNull(::toDefinition)
    }

    suspend fun registerCustomCharacter(
        displayName: String,
        sleepingUri: Uri?,
        standingUri: Uri?,
        walkingUri: Uri?,
        runningUri: Uri?
    ): CharacterDefinition = withContext(Dispatchers.IO) {
        val normalizedName = normalizeDisplayName(displayName)
        require(sleepingUri != null) {
            appContext.getString(R.string.char_repo_error_minimum_state_image_required)
        }
        val records = loadCustomRecords().toMutableList()
        throwIfDuplicateName(normalizedName, records, exceptId = null)

        val id = createUniqueId(normalizedName, records)
        val directory = File(rootDirectory, id)
        val sleeping = copyStateImage(directory, "sleeping", sleepingUri)
        val standing = standingUri?.let { copyStateImage(directory, "standing", it) }
        val walking = walkingUri?.let { copyStateImage(directory, "walking", it) }
        val running = runningUri?.let { copyStateImage(directory, "running", it) }
        val record = CustomCharacterRecord(
            id = id,
            displayName = normalizedName,
            sleepingFileName = sleeping.fileName,
            standingFileName = standing?.fileName.orEmpty(),
            walkingFileName = walking?.fileName.orEmpty(),
            runningFileName = running?.fileName.orEmpty(),
            sleepingOriginalName = sleeping.originalName.orEmpty(),
            standingOriginalName = standing?.originalName.orEmpty(),
            walkingOriginalName = walking?.originalName.orEmpty(),
            runningOriginalName = running?.originalName.orEmpty()
        )
        records.add(record)
        saveCustomRecords(records)
        requireNotNull(toDefinition(record))
    }

    suspend fun updateCustomCharacter(
        id: String,
        displayName: String,
        sleepingUri: Uri?,
        standingUri: Uri?,
        walkingUri: Uri?,
        runningUri: Uri?,
        clearedStates: Set<CharacterState> = emptySet()
    ): CharacterDefinition = withContext(Dispatchers.IO) {
        val normalizedName = normalizeDisplayName(displayName)
        val records = loadCustomRecords().toMutableList()
        val index = records.indexOfFirst { it.id.equals(id, ignoreCase = true) }
        require(index >= 0) { appContext.getString(R.string.char_repo_error_not_found) }
        throwIfDuplicateName(normalizedName, records, exceptId = id)

        val directory = File(rootDirectory, id)
        val existingRecord = records[index]
        val sleeping = sleepingUri?.let { copyStateImage(directory, "sleeping", it) }
        val standing = standingUri?.let { copyStateImage(directory, "standing", it) }
        val walking = walkingUri?.let { copyStateImage(directory, "walking", it) }
        val running = runningUri?.let { copyStateImage(directory, "running", it) }
        val record = CustomCharacterRecord(
            id = id,
            displayName = normalizedName,
            sleepingFileName = updatedFileName(sleeping, existingRecord.sleepingFileName, CharacterState.LYING, clearedStates),
            standingFileName = updatedFileName(standing, existingRecord.standingFileName, CharacterState.STANDING, clearedStates),
            walkingFileName = updatedFileName(walking, existingRecord.walkingFileName, CharacterState.WALKING, clearedStates),
            runningFileName = updatedFileName(running, existingRecord.runningFileName, CharacterState.RUNNING, clearedStates),
            sleepingOriginalName = updatedOriginalName(sleeping, existingRecord.sleepingOriginalName, CharacterState.LYING, clearedStates),
            standingOriginalName = updatedOriginalName(standing, existingRecord.standingOriginalName, CharacterState.STANDING, clearedStates),
            walkingOriginalName = updatedOriginalName(walking, existingRecord.walkingOriginalName, CharacterState.WALKING, clearedStates),
            runningOriginalName = updatedOriginalName(running, existingRecord.runningOriginalName, CharacterState.RUNNING, clearedStates)
        )
        require(record.sleepingFileName.isNotBlank()) {
            appContext.getString(R.string.char_repo_error_minimum_state_image_required)
        }
        if (sleeping == null && CharacterState.LYING in clearedStates) {
            clearStateImage(directory, "sleeping")
        }
        if (standing == null && CharacterState.STANDING in clearedStates) {
            clearStateImage(directory, "standing")
        }
        if (walking == null && CharacterState.WALKING in clearedStates) {
            clearStateImage(directory, "walking")
        }
        if (running == null && CharacterState.RUNNING in clearedStates) {
            clearStateImage(directory, "running")
        }
        records[index] = record
        saveCustomRecords(records)
        requireNotNull(toDefinition(record))
    }

    suspend fun deleteCustomCharacter(id: String): Boolean = withContext(Dispatchers.IO) {
        if (id.equals(BuiltInBlob.id, ignoreCase = true)) {
            return@withContext false
        }

        val records = loadCustomRecords().toMutableList()
        val removed = records.removeAll { it.id.equals(id, ignoreCase = true) }
        if (!removed) {
            return@withContext false
        }

        saveCustomRecords(records)
        File(rootDirectory, id).deleteRecursively()
        true
    }

    private fun loadCustomRecords(): List<CustomCharacterRecord> {
        if (!indexFile.exists()) {
            return emptyList()
        }

        return runCatching {
            val array = JSONArray(indexFile.readText())
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        CustomCharacterRecord(
                            id = item.optString("id"),
                            displayName = item.optString("displayName"),
                            sleepingFileName = item.optString("sleepingFileName", ""),
                            standingFileName = item.optString("standingFileName", "standing.gif"),
                            walkingFileName = item.optString("walkingFileName", "walking.gif"),
                            runningFileName = item.optString("runningFileName", "running.gif"),
                            sleepingOriginalName = item.optString("sleepingOriginalName", ""),
                            standingOriginalName = item.optString("standingOriginalName", ""),
                            walkingOriginalName = item.optString("walkingOriginalName", ""),
                            runningOriginalName = item.optString("runningOriginalName", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveCustomRecords(records: List<CustomCharacterRecord>) {
        rootDirectory.mkdirs()
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("id", record.id)
                    .put("displayName", record.displayName)
                    .put("sleepingFileName", record.sleepingFileName)
                    .put("standingFileName", record.standingFileName)
                    .put("walkingFileName", record.walkingFileName)
                    .put("runningFileName", record.runningFileName)
                    .put("sleepingOriginalName", record.sleepingOriginalName)
                    .put("standingOriginalName", record.standingOriginalName)
                    .put("walkingOriginalName", record.walkingOriginalName)
                    .put("runningOriginalName", record.runningOriginalName)
            )
        }
        indexFile.writeText(array.toString(2))
    }

    private fun toDefinition(record: CustomCharacterRecord): CharacterDefinition? {
        if (record.id.isBlank() || record.displayName.isBlank()) {
            return null
        }

        val directory = File(rootDirectory, record.id)
        val sleepingImage = imageSource(directory, "sleeping", record.sleepingFileName, record.sleepingOriginalName)
        val standingImage = imageSource(directory, "standing", record.standingFileName, record.standingOriginalName)
        val walkingImage = imageSource(directory, "walking", record.walkingFileName, record.walkingOriginalName)
        val runningImage = imageSource(directory, "running", record.runningFileName, record.runningOriginalName)
        if (sleepingImage == null) {
            return null
        }

        return CharacterDefinition(
            id = record.id,
            displayName = record.displayName,
            isCustom = true,
            sleepingImage = sleepingImage,
            standingImage = standingImage,
            walkingImage = walkingImage,
            runningImage = runningImage
        )
    }

    private fun imageSource(
        directory: File,
        stateName: String,
        fileName: String,
        originalName: String
    ): CharacterImageSource.FilePath? {
        if (fileName.isBlank()) return null
        val file = File(directory, "$stateName/$fileName")
        return file.takeIf(File::exists)?.let {
            CharacterImageSource.FilePath(it.absolutePath, originalName.ifBlank { null })
        }
    }

    private fun updatedFileName(
        copiedImage: CopiedImage?,
        existingFileName: String,
        state: CharacterState,
        clearedStates: Set<CharacterState>
    ): String = copiedImage?.fileName ?: existingFileName.takeUnless { state in clearedStates }.orEmpty()

    private fun updatedOriginalName(
        copiedImage: CopiedImage?,
        existingOriginalName: String,
        state: CharacterState,
        clearedStates: Set<CharacterState>
    ): String = copiedImage?.originalName ?: existingOriginalName.takeUnless { state in clearedStates }.orEmpty()

    private fun clearStateImage(characterDirectory: File, stateName: String) {
        File(characterDirectory, stateName).listFiles()?.forEach(File::delete)
    }

    private fun copyStateImage(characterDirectory: File, stateName: String, uri: Uri): CopiedImage {
        val extension = resolveSupportedExtension(uri, stateName)
        val stateDirectory = File(characterDirectory, stateName)
        stateDirectory.mkdirs()
        stateDirectory.listFiles { file -> file.name.startsWith("$stateName.") }
            ?.forEach(File::delete)

        val fileName = "$stateName$extension"
        val destination = File(stateDirectory, fileName)
        openImageInputStream(uri).use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return CopiedImage(fileName = fileName, originalName = displayNameFor(uri))
    }

    private data class CopiedImage(val fileName: String, val originalName: String?)

    private fun resolveSupportedExtension(uri: Uri, stateName: String): String {
        val extension = supportedExtensionFromDisplayName(uri)
            ?: extensionFromMime(appContext.contentResolver, uri)
            ?: extensionFromHeader(uri)

        require(extension != null) {
            appContext.getString(R.string.char_repo_error_image_extension, stateDisplayName(stateName))
        }
        return extension
    }

    private fun stateDisplayName(stateName: String): String {
        return when (stateName) {
            "sleeping" -> appContext.getString(R.string.label_sleeping)
            "standing" -> appContext.getString(R.string.label_standing)
            "walking" -> appContext.getString(R.string.label_walking)
            "running" -> appContext.getString(R.string.label_running)
            else -> stateName
        }
    }

    private fun supportedExtensionFromDisplayName(uri: Uri): String? {
        return displayNameFor(uri)
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.US)
            ?.takeIf(String::isNotBlank)
            ?.let { ".$it" }
            ?.takeIf { it in SupportedExtensions }
    }

    private fun displayNameFor(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = appContext.contentResolver.query(uri, null, null, null, null)
            val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME) ?: -1
            if (cursor != null && nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        } catch (_: IllegalArgumentException) {
            filePathFromBrokenContentUri(uri)?.substringAfterLast('/')
        } finally {
            cursor?.close()
        }
    }

    private fun normalizeDisplayName(displayName: String): String {
        val normalized = displayName.trim()
        require(normalized.isNotBlank()) { appContext.getString(R.string.char_repo_error_name_required) }
        return normalized
    }

    private fun throwIfDuplicateName(
        displayName: String,
        records: List<CustomCharacterRecord>,
        exceptId: String?
    ) {
        require(
            records.none {
                !it.id.equals(exceptId, ignoreCase = true)
                    && it.displayName.equals(displayName, ignoreCase = true)
            }
        ) { appContext.getString(R.string.char_repo_error_duplicate_name) }
    }

    private fun createUniqueId(displayName: String, records: List<CustomCharacterRecord>): String {
        val slug = displayName
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "character" }
        val baseId = "custom_$slug"
        var candidate = baseId
        var suffix = 2
        while (records.any { it.id.equals(candidate, ignoreCase = true) }) {
            candidate = "${baseId}_${suffix++}"
        }
        return candidate
    }

    private data class CustomCharacterRecord(
        val id: String,
        val displayName: String,
        val sleepingFileName: String,
        val standingFileName: String,
        val walkingFileName: String,
        val runningFileName: String,
        val sleepingOriginalName: String = "",
        val standingOriginalName: String = "",
        val walkingOriginalName: String = "",
        val runningOriginalName: String = ""
    )

    companion object {
        private val SupportedExtensions = setOf(".gif", ".png", ".jpg", ".jpeg")

        val BuiltInBlob = CharacterDefinition(
            id = "blob",
            displayName = "Blob",
            isCustom = false,
            sleepingImage = CharacterImageSource.Asset("characters/blob/blob-sleeping/doze_cycle_preview.gif"),
            standingImage = CharacterImageSource.Asset("characters/blob/blob-standing/blob-standing.gif"),
            walkingImage = CharacterImageSource.Asset("characters/blob/blob-walking/blob-walking.gif"),
            runningImage = CharacterImageSource.Asset("characters/blob/blob-running/blob-running.gif")
        )

        private fun extensionFromMime(contentResolver: ContentResolver, uri: Uri): String? {
            return when (contentResolver.getType(uri)?.lowercase(Locale.US)) {
                "image/gif" -> ".gif"
                "image/png" -> ".png"
                "image/jpeg" -> ".jpg"
                else -> null
            }
        }
    }

    private fun extensionFromHeader(uri: Uri): String? {
        val header = ByteArray(12)
        val bytesRead = runCatching {
            openImageInputStream(uri).use { input ->
                input.read(header)
            }
        }.getOrDefault(0)

        return when {
            bytesRead >= 6 && isGifHeader(header) -> ".gif"
            bytesRead >= 8 && isPngHeader(header) -> ".png"
            bytesRead >= 3 && isJpegHeader(header) -> ".jpg"
            else -> null
        }
    }

    private fun openImageInputStream(uri: Uri): InputStream {
        val resolverError = runCatching {
            appContext.contentResolver.openInputStream(uri)
        }.fold(
            onSuccess = { input ->
                if (input != null) {
                    return input
                }
                IllegalArgumentException(appContext.getString(R.string.char_repo_error_image_open_failed))
            },
            onFailure = { it }
        )

        val fallbackPath = filePathFromBrokenContentUri(uri)
        if (fallbackPath != null) {
            return runCatching {
                FileInputStream(File(fallbackPath))
            }.getOrElse {
                throw IllegalArgumentException(
                    appContext.getString(R.string.char_repo_error_image_open_failed_from_path, fallbackPath),
                    it
                )
            }
        }

        throw IllegalArgumentException(
            resolverError.message ?: appContext.getString(R.string.char_repo_error_image_open_failed),
            resolverError
        )
    }

    private fun filePathFromBrokenContentUri(uri: Uri): String? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            return null
        }
        val path = Uri.decode(uri.encodedPath ?: uri.path ?: return null)
        return path
            .substringAfter("/storage/", missingDelimiterValue = "")
            .takeIf(String::isNotBlank)
            ?.let { "/storage/$it" }
    }

    private fun isGifHeader(header: ByteArray): Boolean {
        return header[0] == 'G'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == '8'.code.toByte() &&
            (header[4] == '7'.code.toByte() || header[4] == '9'.code.toByte()) &&
            header[5] == 'a'.code.toByte()
    }

    private fun isPngHeader(header: ByteArray): Boolean {
        val pngHeader = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A
        )
        return header.take(pngHeader.size).toByteArray().contentEquals(pngHeader)
    }

    private fun isJpegHeader(header: ByteArray): Boolean {
        return header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() &&
            header[2] == 0xFF.toByte()
    }
}
