package com.example.hybrid_ai_app.onboarding.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.example.hybrid_ai_app.core.data.remote.PlanAttachmentDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Mirrors the caps enforced by the backend's `validateImportPayload`. */
const val MAX_PLAN_ATTACHMENTS = 5
const val MAX_PLAN_ATTACHMENT_BYTES = 8L * 1024 * 1024

private const val PDF_MIME = "application/pdf"
private const val JPEG_MIME = "image/jpeg"

// Photos of a routine only need to be legible, not archival. Downscaling before base64 keeps the
// request inside the backend's 8 MB budget and the client's 90s timeout.
private const val MAX_IMAGE_EDGE_PX = 1600
private const val IMAGE_JPEG_QUALITY = 85

/** A file the user attached, ready to send plus the label the chip shows. */
data class PlanAttachment(
    val dto: PlanAttachmentDto,
    val displayName: String
)

/** Why a file could not be attached. The UI maps these to localized copy. */
enum class PlanAttachmentError {
    UNSUPPORTED_TYPE,
    TOO_LARGE,
    TOO_MANY,
    UNREADABLE
}

class PlanAttachmentException(val error: PlanAttachmentError) : Exception(error.name)

/**
 * Turns a document/photo the user picked into the base64 part the backend forwards to Gemini.
 *
 * PDFs travel as-is; images are re-encoded to a bounded JPEG, so the outgoing mime type is not
 * necessarily the source one.
 */
@Singleton
class PlanAttachmentReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun read(uri: Uri): Result<PlanAttachment> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)
                ?: throw PlanAttachmentException(PlanAttachmentError.UNREADABLE)

            // Cheap guard before pulling anything into memory: a 200 MB scan is rejected by its
            // declared size, not by an OutOfMemoryError.
            queryLong(uri, OpenableColumns.SIZE)?.let { declaredSize ->
                if (mimeType == PDF_MIME && declaredSize > MAX_PLAN_ATTACHMENT_BYTES) {
                    throw PlanAttachmentException(PlanAttachmentError.TOO_LARGE)
                }
            }

            val bytes = when {
                mimeType == PDF_MIME ->
                    resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw PlanAttachmentException(PlanAttachmentError.UNREADABLE)

                mimeType.startsWith("image/") -> downscaleToJpeg(uri)

                else -> throw PlanAttachmentException(PlanAttachmentError.UNSUPPORTED_TYPE)
            }

            if (bytes.size > MAX_PLAN_ATTACHMENT_BYTES) {
                throw PlanAttachmentException(PlanAttachmentError.TOO_LARGE)
            }

            PlanAttachment(
                dto = PlanAttachmentDto(
                    mimeType = if (mimeType == PDF_MIME) PDF_MIME else JPEG_MIME,
                    data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                ),
                displayName = queryString(uri, OpenableColumns.DISPLAY_NAME)
                    ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
            )
        }
    }

    private fun downscaleToJpeg(uri: Uri): ByteArray {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw PlanAttachmentException(PlanAttachmentError.UNREADABLE)

        val largestEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (largestEdge <= 0) throw PlanAttachmentException(PlanAttachmentError.UNREADABLE)

        val decodeOptions = BitmapFactory.Options().apply {
            // Powers of two only — that is the granularity inSampleSize actually honours.
            inSampleSize = generateSequence(1) { it * 2 }
                .first { largestEdge / it <= MAX_IMAGE_EDGE_PX }
        }

        val bitmap = resolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
            ?: throw PlanAttachmentException(PlanAttachmentError.UNREADABLE)

        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private fun queryString(uri: Uri, column: String): String? =
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }

    private fun queryLong(uri: Uri, column: String): Long? =
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
}
