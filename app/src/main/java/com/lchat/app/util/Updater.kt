package com.lchat.app.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object Updater {

    private const val GITHUB_API =
        "https://api.github.com/repos/tear360/L-Chat/releases/latest"
    private const val APK_ASSET_NAME = "app-release.apk"
    private const val PREFS = "lchat_update"
    private const val KEY_IGNORED = "ignored_tag"

    private val executor = Executors.newSingleThreadExecutor()

    fun checkForUpdate(context: Context, onResult: (String?, String?) -> Unit) {
        executor.execute {
            var latestTag: String? = null
            var downloadUrl: String? = null
            try {
                val conn = URL(GITHUB_API).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    latestTag = json.optString("tag_name", "")
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.optString("name") == APK_ASSET_NAME) {
                                downloadUrl = asset.optString("browser_download_url")
                                break
                            }
                        }
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                latestTag = null
            }

            latestTag?.let { tag ->
                val ignored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_IGNORED, null)
                onResult(tag, if (ignored == tag) null else downloadUrl)
            }
        }
    }

    fun promptUpdate(context: Context, tag: String, url: String) {
        AlertDialog.Builder(context)
            .setTitle("Mise à jour disponible")
            .setMessage("La version $tag est disponible.\nVoulez-vous la télécharger et l'installer ?")
            .setPositiveButton("Mettre à jour") { _, _ ->
                downloadAndInstall(context, url)
            }
            .setNegativeButton("Plus tard") { dialog, _ ->
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_IGNORED, tag).apply()
                dialog.dismiss()
            }
            .show()
    }

    private fun downloadAndInstall(context: Context, url: String) {
        Toast.makeText(context, "Téléchargement en cours...", Toast.LENGTH_SHORT).show()
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("L Chat")
            .setDescription("Téléchargement de la mise à jour")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "LChat-$url.hashCode().apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) == downloadId) {
                    val context = ctx ?: return
                    context.unregisterReceiver(this)
                    val apkUri = dm.getUriForDownloadedFile(downloadId)
                    if (apkUri != null) {
                        installApk(context, apkUri)
                    }
                }
            }
        }, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
    }

    private fun installApk(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}