package com.itmakesome.pickupmemo2.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.itmakesome.pickupmemo2.data.BaeminLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 배민 로그를 캐시 디렉토리에 txt 파일로 기록하고,
 * FileProvider URI를 반환하는 내보내기 유틸 (FEAT-13).
 *
 * authority: {packageName}.fileprovider
 * 경로: cacheDir/exports/baemin_log_YYYYMMDD_HHmmss.txt
 */
object BaeminLogExporter {

    private const val EXPORTS_DIR = "exports"
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    /**
     * IO 스레드에서 로그를 파일로 쓰고 FileProvider URI를 반환한다.
     * 이전 내보내기 파일은 최신 1개만 남기도록 삭제한다.
     */
    suspend fun export(context: Context): Uri = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val dir = File(app.cacheDir, EXPORTS_DIR).apply { mkdirs() }
        // 이전 내보내기 파일 제거 (최신 1개만 노출)
        dir.listFiles()?.forEach { it.delete() }

        val logs = BaeminLogRepository.getAll()  // 최신순 (DAO ORDER BY capturedAt DESC)

        val sb = StringBuilder()
        for (e in logs) {
            sb.append('[').append(TimeFormat.formatTimestamp(e.capturedAt)).append("]\n")
            sb.append("Package: ").append(e.packageName).append('\n')
            sb.append("Type: ").append(e.eventType).append('\n')
            sb.append("Text: ").append(e.text).append("\n\n")
        }

        val file = File(dir, TimeFormat.exportFileName(System.currentTimeMillis()))
        file.writeText(sb.toString())

        FileProvider.getUriForFile(
            app,
            app.packageName + FILE_PROVIDER_SUFFIX,
            file
        )
    }

    /**
     * 공유 Intent를 생성한다. 메인 스레드에서 호출 가능.
     */
    fun buildShareIntent(uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
