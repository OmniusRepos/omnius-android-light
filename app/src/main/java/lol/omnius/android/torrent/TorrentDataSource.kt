package lol.omnius.android.torrent

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min

/**
 * ExoPlayer DataSource that reads from a torrent file on disk,
 * blocking reads until the piece at the current offset is downloaded.
 * This allows seeking to work — without it, ExoPlayer reads pre-allocated
 * zeros and fails to decode.
 */
@UnstableApi
class TorrentDataSource : BaseDataSource(false) {

    private var raf: RandomAccessFile? = null
    private var uri: Uri? = null
    private var filePosition: Long = 0
    private var bytesRemaining: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val file = File(dataSpec.uri.path!!)
        val raf = RandomAccessFile(file, "r")
        this.raf = raf

        val fileSize = TorrentStreamManager.getFileSize()
        val position = dataSpec.position
        filePosition = position

        raf.seek(position)

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            fileSize - position
        }

        transferInitializing(dataSpec)
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesRemaining <= 0) return C.RESULT_END_OF_INPUT

        val toRead = min(length.toLong(), bytesRemaining).toInt()

        // Block until the piece at current position is downloaded
        TorrentStreamManager.waitForPiece(filePosition)

        val read = raf!!.read(buffer, offset, toRead)
        if (read > 0) {
            filePosition += read
            bytesRemaining -= read
            bytesTransferred(read)
        }
        return if (read == -1) C.RESULT_END_OF_INPUT else read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try {
            raf?.close()
        } catch (_: Exception) {}
        raf = null
        uri = null
        transferEnded()
    }

    /**
     * Routes file:// URIs to TorrentDataSource, http(s):// to DefaultHttpDataSource.
     * This lets sidecar subtitle HTTP URLs work alongside torrent file playback.
     */
    /**
     * Routes URIs to the correct DataSource:
     * - The torrent video file → TorrentDataSource (piece-aware blocking reads)
     * - Other local files (subtitles) → FileDataSource (direct file read)
     * - HTTP URLs (API subtitles) → DefaultHttpDataSource
     */
    @UnstableApi
    class Factory : DataSource.Factory {
        private val httpFactory = DefaultHttpDataSource.Factory()
        private val fileFactory = FileDataSource.Factory()

        override fun createDataSource(): DataSource {
            return RoutingDataSource(
                TorrentDataSource(),
                httpFactory.createDataSource(),
                fileFactory.createDataSource(),
            )
        }
    }

    @UnstableApi
    private class RoutingDataSource(
        private val torrentSource: TorrentDataSource,
        private val httpSource: DataSource,
        private val fileSource: DataSource,
    ) : DataSource {
        private var activeSource: DataSource? = null

        override fun open(dataSpec: DataSpec): Long {
            val scheme = dataSpec.uri.scheme?.lowercase()
            activeSource = when {
                scheme == "http" || scheme == "https" -> httpSource
                isVideoFile(dataSpec.uri) -> torrentSource
                else -> fileSource  // subtitle .srt/.ass files etc.
            }
            return activeSource!!.open(dataSpec)
        }

        private fun isVideoFile(uri: Uri): Boolean {
            val path = uri.path ?: return false
            val streamPath = TorrentStreamManager.state.value.streamUrl ?: return false
            return path == streamPath
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return activeSource!!.read(buffer, offset, length)
        }

        override fun getUri(): Uri? = activeSource?.uri

        override fun close() {
            activeSource?.close()
            activeSource = null
        }

        override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
            torrentSource.addTransferListener(transferListener)
            httpSource.addTransferListener(transferListener)
            fileSource.addTransferListener(transferListener)
        }
    }
}
