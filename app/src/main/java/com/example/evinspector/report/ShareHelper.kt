package com.example.evinspector.report

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands report files to other apps through the system share sheet using FileProvider URIs.
 * No storage permission is needed, and recipients get read access to just these files.
 */
object ShareHelper {

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun shareReports(context: Context, pdf: File, csv: File) {
        val uris = arrayListOf(uriFor(context, pdf), uriFor(context, csv))
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "EV charger inspection report")
            clipData = ClipData.newRawUri("Inspection reports", uris[0]).apply {
                addItem(ClipData.Item(uris[1]))
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share inspection reports"))
    }

    /** Returns false if no installed app can display PDFs. */
    fun openPdf(context: Context, pdf: File): Boolean {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, pdf), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(view)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
