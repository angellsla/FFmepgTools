package com.devobject.ffmpegtools.core.files

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/**
 * 将应用私有缓存目录通过 SAF（Storage Access Framework）暴露给系统文件管理器。
 *
 * 其他应用或系统文件管理器可通过「FFmpeg 工具箱缓存」根目录浏览、读取
 * [context.cacheDir] 下的文件与子目录。
 *
 * 当前实现为只读：支持查看与打开文件，不支持创建、删除或修改，
 * 避免外部误操作影响正在运行的 FFmpeg 任务。
 */
class CacheDocumentsProvider : DocumentsProvider() {

    companion object {
        private const val ROOT_ID = "cache-root"
        private const val AUTHORITY_SUFFIX = ".cache.documents"
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val context = context ?: return MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val cacheDir = context.cacheDir
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        result.newRow().apply {
            add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
            add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, cacheDir.documentId)
            add(DocumentsContract.Root.COLUMN_TITLE, "FFmpeg 工具箱缓存")
            add(DocumentsContract.Root.COLUMN_SUMMARY, cacheDir.absolutePath)
            add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_LOCAL_ONLY)
            add(DocumentsContract.Root.COLUMN_ICON, context.applicationInfo.icon)
            add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
        }
        return result
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        documentId?.let { fileForId(it) }?.takeIf { it.exists() }?.let { includeFile(result, it) }
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        parentDocumentId?.let { fileForId(it) }
            ?.takeIf { it.isDirectory }
            ?.listFiles()
            ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            ?.forEach { includeFile(result, it) }
        return result
    }

    @Throws(FileNotFoundException::class)
    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: android.os.CancellationSignal?
    ): ParcelFileDescriptor {
        val file = documentId?.let { fileForId(it) }
            ?: throw FileNotFoundException("Invalid document id: $documentId")
        if (!file.exists()) throw FileNotFoundException("File not found: $documentId")
        // 只读打开；即使 mode 包含 'w' 也忽略，保持只读语义。
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getDocumentType(documentId: String?): String {
        return documentId?.let { fileForId(it) }?.mimeType
            ?: DocumentsContract.Document.MIME_TYPE_DIR
    }

    override fun isChildDocument(parentDocumentId: String?, documentId: String?): Boolean {
        if (parentDocumentId == null || documentId == null) return false
        val parent = fileForId(parentDocumentId)
        val child = fileForId(documentId)
        return child.startsWith(parent)
    }

    /**
     * documentId 为文件绝对路径。缓存目录外的路径视为无效。
     */
    private fun fileForId(documentId: String): File {
        val context = context ?: throw FileNotFoundException("Context not available")
        val file = File(documentId)
        val cacheDir = context.cacheDir
        // 安全校验：必须位于应用缓存目录内。
        if (!file.startsWith(cacheDir)) {
            throw FileNotFoundException("Access denied: $documentId")
        }
        return file
    }

    private fun includeFile(cursor: MatrixCursor, file: File) {
        cursor.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, file.documentId)
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
            add(DocumentsContract.Document.COLUMN_SIZE, if (file.isFile) file.length() else 0L)
            add(DocumentsContract.Document.COLUMN_MIME_TYPE, file.mimeType)
            add(
                DocumentsContract.Document.COLUMN_FLAGS,
                if (file.isDirectory) DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE else 0
            )
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
        }
    }

    private val File.documentId: String get() = absolutePath

    private val File.mimeType: String
        get() = when {
            isDirectory -> DocumentsContract.Document.MIME_TYPE_DIR
            extension.isBlank() -> "application/octet-stream"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
                ?: "application/octet-stream"
        }

    private fun File.startsWith(other: File): Boolean = canonicalPath.startsWith(other.canonicalPath)

    private val DEFAULT_ROOT_PROJECTION: Array<String> = arrayOf(
        DocumentsContract.Root.COLUMN_ROOT_ID,
        DocumentsContract.Root.COLUMN_DOCUMENT_ID,
        DocumentsContract.Root.COLUMN_TITLE,
        DocumentsContract.Root.COLUMN_SUMMARY,
        DocumentsContract.Root.COLUMN_FLAGS,
        DocumentsContract.Root.COLUMN_ICON,
        DocumentsContract.Root.COLUMN_MIME_TYPES
    )

    private val DEFAULT_DOCUMENT_PROJECTION: Array<String> = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_FLAGS,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )
}
