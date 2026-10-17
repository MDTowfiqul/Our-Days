package app.ourdays.cloud.play

import android.content.Context
import app.ourdays.cloud.DriveBackend
import app.ourdays.cloud.cloudHttp
import com.google.android.gms.auth.GoogleAuthUtil
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
 */
class GmsDriveBackend(private val context: Context) : DriveBackend {
    private val scope = "oauth2:https://www.googleapis.com/auth/drive.file"

    private fun token(account: String): String =
        GoogleAuthUtil.getToken(context, account, scope)

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

    private suspend fun deleteByName(account: String, name: String) {
        listBackups(account).filter { it.name == name }.forEach { delete(account, it.id) }
    }

    private suspend fun delete(account: String, id: String) {
        val t = token(account)
        cloudHttp.newCall(
            Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$id")
                .header("Authorization", "Bearer $t")
                .delete().build(),
        ).execute().use { }
    }

    override suspend fun listBackups(account: String): List<DriveBackend.DriveFile> {
        val q = java.net.URLEncoder.encode(
            "'appDataFolder' in parents and name contains 'our-days-backup' and trashed = false",
            "UTF-8",
        )
        val text = authGet(
            account,
            "https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id,name,modifiedTime)&orderBy=modifiedTime desc&pageSize=50",
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
