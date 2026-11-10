package app.ourdays.cloud.play

import android.content.Context
import app.ourdays.cloud.DriveBackend
import app.ourdays.cloud.DriveConsentNeeded
import app.ourdays.cloud.cloudHttp
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.OutputStream

/**
 * Google Drive über die REST-API im versteckten appDataFolder.
 * Das Konto wählt die Oberfläche per System-Dialog, hier wird nur das OAuth-Token geholt.
 *
 * Einmalig nötig (nur Play-Variante): In der Google Cloud Console ein OAuth-Projekt
 * anlegen, Drive-API aktivieren und den SHA-1 der Signatur hinterlegen, sonst lehnt
 * Google das Token ab.
 *
 * Der appDataFolder braucht zusätzlich den Scope drive.appdata — drive.file allein
 * reicht nicht, die Drive-API antwortet sonst mit HTTP 403 ("The granted scopes do
 * not allow use of the Application Data folder.").
 */
class GmsDriveBackend(private val context: Context) : DriveBackend {
    private val scope = "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.appdata"

    private fun token(account: String): String =
        try {
            GoogleAuthUtil.getToken(context, account, scope)
        } catch (e: UserRecoverableAuthException) {
            throw DriveConsentNeeded(e.intent)
        }

    private fun authGet(account: String, url: String): String {
        val t = token(account)
        try {
            cloudHttp.newCall(
                Request.Builder().url(url).header("Authorization", "Bearer $t").get().build(),
            ).execute().use { resp ->
                val text = resp.body!!.string()
                if (resp.code == 401) {
                    GoogleAuthUtil.invalidateToken(context, t)
                    throw java.io.IOException("drive unauthorized")
                }
                if (!resp.isSuccessful) throw java.io.IOException("drive http ${resp.code}: ${text.take(200)}")
                return text
            }
        } catch (e: java.io.IOException) {
            if (e.message == "drive unauthorized") return authGetRetry(account, url, t)
            throw e
        }
    }

    private fun authGetRetry(account: String, url: String, old: String): String {
        GoogleAuthUtil.invalidateToken(context, old)
        val t = token(account)
        cloudHttp.newCall(
            Request.Builder().url(url).header("Authorization", "Bearer $t").get().build(),
        ).execute().use { resp ->
            val text = resp.body!!.string()
            if (!resp.isSuccessful) throw java.io.IOException("drive http ${resp.code}: ${text.take(200)}")
            return text
        }
    }

    override suspend fun upload(account: String, name: String, file: File): String {
        val t = token(account)
        val metadata = JSONObject()
            .put("name", name)
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("metadata", null, metadata)
            .addFormDataPart("file", name, file.asRequestBody("application/octet-stream".toMediaType()))
            .build()
        // Vorherige Sicherung gleichen Namens ersetzen, damit der Ordner nicht vollmüllt
        runCatching { deleteByName(account, name) }
        cloudHttp.newCall(
            Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
                .header("Authorization", "Bearer $t")
                .post(body).build(),
        ).execute().use { resp ->
            val text = resp.body!!.string()
            if (resp.code == 401) {
                GoogleAuthUtil.invalidateToken(context, t)
                return upload(account, name, file)
            }
            if (!resp.isSuccessful) throw java.io.IOException("drive http ${resp.code}: ${text.take(200)}")
            return JSONObject(text).getString("id")
        }
    }

    override suspend fun rename(account: String, id: String, newName: String) {
        val t = token(account)
        val body = JSONObject().put("name", newName).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        cloudHttp.newCall(
            Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$id")
                .header("Authorization", "Bearer $t")
                .patch(body).build(),
        ).execute().use { resp ->
            val text = resp.body!!.string()
            if (resp.code == 401) {
                GoogleAuthUtil.invalidateToken(context, t)
                return rename(account, id, newName)
            }
            if (!resp.isSuccessful) throw java.io.IOException("drive http ${resp.code}: ${text.take(200)}")
        }
    }

    override suspend fun delete(account: String, id: String) {
        val t = token(account)
        cloudHttp.newCall(
            Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$id")
                .header("Authorization", "Bearer $t")
                .delete().build(),
        ).execute().use { }
    }

    private suspend fun deleteByName(account: String, name: String) {
        listBackups(account).filter { it.name == name }.forEach { delete(account, it.id) }
    }

    override suspend fun listBackups(account: String): List<DriveBackend.DriveFile> {
        // Der appDataFolder liegt in einem eigenen "space": Ohne spaces=appDataFolder
        // sucht files.list nur im normalen Drive und findet nie etwas — der q-Filter
        // allein reicht nicht. Darum sah eine neue Installation keine alten
        // Sicherungen, obwohl sie im Drive lagen (die angezeigte Liste kam nur
        // aus dem lokalen Verzeichnis). So steht es auch in Googles Doku:
        // "To search for files in the application data folder, set the spaces
        // field to appDataFolder and use the files.list method."
        val q = java.net.URLEncoder.encode(
            "'appDataFolder' in parents and trashed = false",
            "UTF-8",
        )
        val text = authGet(
            account,
            "https://www.googleapis.com/drive/v3/files?q=$q&spaces=appDataFolder&fields=files(id,name,modifiedTime,size)&orderBy=modifiedTime desc&pageSize=50",
        )
        val files = JSONObject(text).optJSONArray("files") ?: return emptyList()
        return (0 until files.length()).map { i ->
            val o = files.getJSONObject(i)
            DriveBackend.DriveFile(
                id = o.getString("id"),
                name = o.getString("name"),
                modified = runCatching {
                    java.time.Instant.parse(o.getString("modifiedTime")).toEpochMilli()
                }.getOrDefault(0L),
                size = o.optLong("size", 0),
            )
        }
    }

    override suspend fun download(account: String, id: String, out: OutputStream) {
        val t = token(account)
        cloudHttp.newCall(
            Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$id?alt=media")
                .header("Authorization", "Bearer $t")
                .get().build(),
        ).execute().use { resp ->
            if (resp.code == 401) {
                GoogleAuthUtil.invalidateToken(context, t)
                return download(account, id, out)
            }
            if (!resp.isSuccessful) throw java.io.IOException("drive http ${resp.code}")
            resp.body!!.byteStream().use { it.copyTo(out) }
        }
    }

    override fun invalidate(account: String) {
        runCatching {
            val t = GoogleAuthUtil.getToken(context, account, scope)
            GoogleAuthUtil.invalidateToken(context, t)
        }
    }

}
