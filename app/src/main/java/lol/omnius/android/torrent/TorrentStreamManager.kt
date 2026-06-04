package lol.omnius.android.torrent

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.libtorrent4j.*
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File

data class SubtitleFileInfo(
    val path: String,
    val name: String,
    val mimeType: String,
)

data class TorrentStreamState(
    val status: String = "idle",
    val downloaded: Long = 0L,
    val totalSize: Long = 0L,
    val downloadSpeed: Long = 0L,
    val peers: Int = 0,
    val progress: Double = 0.0,
    val streamUrl: String? = null,
    val error: String? = null,
    val isStuck: Boolean = false,
    val isStreaming: Boolean = false,
    val streamReady: Boolean = false,
    val subtitleFiles: List<SubtitleFileInfo> = emptyList(),
    val piecesReady: Int = 0,
    val piecesTotal: Int = 0,
)

object TorrentStreamManager {

    private const val TAG = "TorrentStream"
    private const val SEEK_AHEAD_PIECES = 15

    private val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "sub", "vtt", "idx")
    private val SUBTITLE_MIME = mapOf(
        "srt" to "application/x-subrip",
        "ass" to "text/x-ssa",
        "ssa" to "text/x-ssa",
        "vtt" to "text/vtt",
        "sub" to "application/x-subrip",
        "idx" to "application/x-subrip",
    )

    private val TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.moeking.me:6969/announce",
        "udp://explodie.org:6969/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://tracker.theoks.net:6969/announce",
    )

    private var sessionManager: SessionManager? = null
    private var saveDir: File? = null
    private var pollJob: Job? = null
    private var currentHash: String? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Active torrent info (set after metadata received)
    private var currentHandle: TorrentHandle? = null
    private var currentFileOffset: Long = 0L
    private var currentFileSize: Long = 0L
    private var currentPieceLength: Long = 0L
    private var currentFirstPiece: Int = 0
    private var currentLastPiece: Int = 0

    private val _state = MutableStateFlow(TorrentStreamState())
    val state = _state.asStateFlow()

    fun getFileSize(): Long = currentFileSize

    fun initialize(context: Context) {
        if (sessionManager != null) return

        saveDir = File(context.cacheDir, "torrents").apply { mkdirs() }

        val sm = SessionManager()

        val params = SessionParams(SettingsPack().apply {
            connectionsLimit(100)
            activeDownloads(1)
            activeSeeds(0)
            setEnableDht(true)
            setEnableLsd(true)
        })

        sm.start(params)
        sessionManager = sm
        Log.i(TAG, "Session started, DHT bootstrapping")
    }

    /**
     * Blocks until the piece covering the given file byte offset is downloaded.
     * Called from TorrentDataSource on ExoPlayer's IO thread — safe to block.
     * Also boosts priority of that piece + next few pieces.
     */
    fun waitForPiece(fileByteOffset: Long) {
        val handle = currentHandle ?: return
        if (currentPieceLength <= 0) return

        val absoluteOffset = currentFileOffset + fileByteOffset
        val pieceIndex = (absoluteOffset / currentPieceLength).toInt()
            .coerceIn(currentFirstPiece, currentLastPiece)

        // Fast path: piece confirmed available
        try {
            if (handle.havePiece(pieceIndex)) return
        } catch (_: Exception) { return }

        // Boost priority + set deadline on this piece and ahead
        try {
            val endPiece = (pieceIndex + SEEK_AHEAD_PIECES).coerceAtMost(currentLastPiece)
            for (i in pieceIndex..endPiece) {
                handle.piecePriority(i, Priority.TOP_PRIORITY)
                handle.setPieceDeadline(i, 1)
            }
        } catch (_: Exception) {}

        Log.i(TAG, "waitForPiece: waiting for piece $pieceIndex (fileOffset=$fileByteOffset, absOffset=$absoluteOffset, pieceLen=$currentPieceLength)")

        // Poll until piece is available — wait as long as stream is active
        var waited = 0
        while (_state.value.isStreaming) {
            try {
                if (handle.havePiece(pieceIndex)) {
                    Log.i(TAG, "waitForPiece: piece $pieceIndex ready after ${waited}ms")
                    return
                }
            } catch (_: Exception) { return }
            Thread.sleep(200)
            waited += 200
            if (waited % 5000 == 0) {
                try {
                    val status = handle.status()
                    Log.i(TAG, "waitForPiece: still waiting for piece $pieceIndex (${waited/1000}s, totalDone=${status.totalDone()/1_048_576}MB, peers=${status.numPeers()})")
                } catch (_: Exception) {}
            }
        }
        Log.w(TAG, "waitForPiece: stream stopped while waiting for piece $pieceIndex")
    }

    /**
     * Called when the player seeks to a new position.
     * Reprioritizes pieces around the seek target so libtorrent downloads them first.
     */
    fun seekTo(positionMs: Long, durationMs: Long) {
        val handle = currentHandle ?: return
        if (durationMs <= 0 || currentFileSize <= 0 || currentPieceLength <= 0) return

        // Estimate byte offset within the file based on time ratio
        val ratio = positionMs.toDouble() / durationMs
        val byteOffset = (ratio * currentFileSize).toLong().coerceIn(0, currentFileSize - 1)

        // Calculate the piece index
        val absoluteOffset = currentFileOffset + byteOffset
        val seekPiece = (absoluteOffset / currentPieceLength).toInt()
            .coerceIn(currentFirstPiece, currentLastPiece)

        Log.i(TAG, "Seek: ${positionMs/1000}s/${durationMs/1000}s -> byte $byteOffset, piece $seekPiece")

        try {
            // Boost next N pieces from seek position with deadlines (Popcorn Time technique)
            val endPiece = (seekPiece + SEEK_AHEAD_PIECES).coerceAtMost(currentLastPiece)
            for (i in seekPiece..endPiece) {
                handle.piecePriority(i, Priority.TOP_PRIORITY)
                handle.setPieceDeadline(i, 1)
            }

            // Always keep last 2 pieces high priority (container headers)
            for (i in maxOf(currentLastPiece - 1, currentFirstPiece)..currentLastPiece) {
                handle.piecePriority(i, Priority.TOP_PRIORITY)
                handle.setPieceDeadline(i, 1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "seekTo priority error", e)
        }
    }

    fun startStream(hash: String, title: String, fileIndex: Int? = null) {
        val sm = sessionManager ?: run {
            _state.value = TorrentStreamState(error = "Torrent engine not initialized", status = "error")
            return
        }

        stopStream()

        currentHash = hash
        _state.value = TorrentStreamState(
            status = "finding_peers",
            isStreaming = true,
        )

        val trackerParams = TRACKERS.joinToString("") { "&tr=${java.net.URLEncoder.encode(it, "UTF-8")}" }
        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
        val magnet = "magnet:?xt=urn:btih:$hash&dn=$encodedTitle$trackerParams"

        scope.launch {
            try {
                Log.i(TAG, "Fetching magnet: $hash")
                val torrentHandle: TorrentHandle

                sm.download(magnet, saveDir!!, torrent_flags_t())

                // Wait for metadata (up to 60 seconds)
                var waited = 0
                while (true) {
                    val handle = sm.find(Sha1Hash.parseHex(hash))
                    if (handle != null && handle.status().hasMetadata()) {
                        torrentHandle = handle
                        break
                    }
                    delay(500)
                    waited += 500
                    if (waited >= 60_000) {
                        _state.value = _state.value.copy(
                            error = "Timeout waiting for torrent metadata",
                            status = "error",
                            isStreaming = false,
                        )
                        return@launch
                    }
                }

                Log.i(TAG, "Metadata received")

                val ti = torrentHandle.torrentFile()
                val numFiles = ti.numFiles()

                // Choose file: explicit valid index, or auto-select the largest
                // file. The server uses -1 as the "auto-select" sentinel (and
                // sends it for episode torrents), so treat any negative/null
                // index as auto, not as a literal file index.
                val targetIndex = fileIndex?.takeIf { it in 0 until numFiles } ?: run {
                    var largestIdx = 0
                    var largestSize = 0L
                    for (i in 0 until numFiles) {
                        val size = ti.files().fileSize(i)
                        if (size > largestSize) {
                            largestSize = size
                            largestIdx = i
                        }
                    }
                    largestIdx
                }

                // Scan for subtitle files in torrent
                val subtitleInfos = mutableListOf<SubtitleFileInfo>()
                val subtitleIndices = mutableListOf<Int>()
                for (i in 0 until numFiles) {
                    val fileName = ti.files().fileName(i)
                    val ext = fileName.substringAfterLast('.', "").lowercase()
                    if (ext in SUBTITLE_EXTENSIONS) {
                        subtitleIndices.add(i)
                        val subPath = File(saveDir!!, ti.files().filePath(i)).absolutePath
                        val displayName = fileName.substringBeforeLast('.')
                            .replace('.', ' ').replace('_', ' ')
                        subtitleInfos.add(SubtitleFileInfo(
                            path = subPath,
                            name = displayName,
                            mimeType = SUBTITLE_MIME[ext] ?: "application/x-subrip",
                        ))
                        Log.i(TAG, "Found subtitle file [$i]: $fileName")
                    }
                }

                // Set file priorities: video + subtitle files
                val priorities = Array(numFiles) { Priority.IGNORE }
                priorities[targetIndex] = Priority.DEFAULT
                for (idx in subtitleIndices) {
                    priorities[idx] = Priority.DEFAULT  // small files, download quickly
                }
                torrentHandle.prioritizeFiles(priorities)

                // Enable sequential download
                torrentHandle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)

                // Calculate piece range for target file
                val fileStorage = ti.files()
                val fileOffset = fileStorage.fileOffset(targetIndex)
                val fileSize = fileStorage.fileSize(targetIndex)
                val pieceLength = ti.pieceLength().toLong()
                val firstPiece = (fileOffset / pieceLength).toInt()
                val lastPiece = ((fileOffset + fileSize - 1) / pieceLength).toInt()

                // Store for seekTo()
                currentHandle = torrentHandle
                currentFileOffset = fileOffset
                currentFileSize = fileSize
                currentPieceLength = pieceLength
                currentFirstPiece = firstPiece
                currentLastPiece = lastPiece

                // Require first piece + last piece before playback starts.
                // ExoPlayer needs both: start of file for headers, end for MP4 moov atom.
                // waitForPiece() handles everything in between during playback.
                val preparePieces = mutableListOf(firstPiece)
                torrentHandle.piecePriority(firstPiece, Priority.TOP_PRIORITY)
                torrentHandle.setPieceDeadline(firstPiece, 1)

                // Last piece (container footer / moov atom) — required before start
                if (lastPiece != firstPiece) {
                    preparePieces.add(lastPiece)
                    torrentHandle.piecePriority(lastPiece, Priority.TOP_PRIORITY)
                    torrentHandle.setPieceDeadline(lastPiece, 1)
                }

                val filePath = File(saveDir!!, fileStorage.filePath(targetIndex)).absolutePath
                val totalSize = fileSize

                Log.i(TAG, "File: $filePath")
                Log.i(TAG, "  fileOffset=$fileOffset, fileSize=$fileSize, pieceLength=$pieceLength")
                Log.i(TAG, "  firstPiece=$firstPiece, lastPiece=$lastPiece, totalPieces=${lastPiece - firstPiece + 1}")
                Log.i(TAG, "  preparePieces=$preparePieces")

                _state.value = _state.value.copy(
                    status = "buffering",
                    totalSize = totalSize,
                    subtitleFiles = subtitleInfos,
                )

                // Poll progress — ready when all prepare pieces downloaded + 5MB sequential
                var stuckTimer = 0

                pollJob = scope.launch {
                    var ready = false
                    while (_state.value.isStreaming) {
                        delay(1000)
                        try {
                            val status = torrentHandle.status()
                            val downloaded = status.totalDone()
                            val speed = status.downloadRate().toLong()
                            val peers = status.numPeers()
                            val progress = if (totalSize > 0) (downloaded.toDouble() / totalSize * 100.0) else 0.0

                            if (!ready) {
                                // Check each critical piece
                                val pieceStatus = preparePieces.map { p ->
                                    "$p=${torrentHandle.havePiece(p)}"
                                }
                                val readyCount = preparePieces.count { torrentHandle.havePiece(it) }
                                val allPrepareReady = readyCount == preparePieces.size

                                // Ready as soon as first piece is available
                                ready = allPrepareReady

                                stuckTimer++
                                val isStuck = stuckTimer >= 20 && progress < 0.5 && peers < 2

                                _state.value = _state.value.copy(
                                    downloaded = downloaded,
                                    downloadSpeed = speed,
                                    peers = peers,
                                    progress = progress,
                                    isStuck = isStuck,
                                    streamReady = ready,
                                    streamUrl = if (ready) filePath else null,
                                    piecesReady = readyCount,
                                    piecesTotal = preparePieces.size,
                                )

                                Log.i(TAG, "Poll: ${downloaded / 1_048_576}MB, ${speed / 1024}KB/s, $peers peers, preparePieces=$pieceStatus, allReady=$allPrepareReady, ready=$ready")

                                if (ready) {
                                    Log.i(TAG, "Ready! ${downloaded / 1_048_576}MB downloaded. File: $filePath")
                                }
                            } else {
                                // Keep updating stats after ready (for player UI)
                                _state.value = _state.value.copy(
                                    downloaded = downloaded,
                                    downloadSpeed = speed,
                                    peers = peers,
                                    progress = progress,
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Poll error", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "startStream failed", e)
                _state.value = _state.value.copy(
                    error = e.message ?: "Stream failed",
                    status = "error",
                    isStreaming = false,
                )
            }
        }
    }

    fun stopStream() {
        pollJob?.cancel()
        pollJob = null
        val hash = currentHash
        currentHash = null
        currentHandle = null
        currentFileOffset = 0L
        currentFileSize = 0L
        currentPieceLength = 0L
        currentFirstPiece = 0
        currentLastPiece = 0

        if (hash != null) {
            try {
                val sm = sessionManager ?: return
                val handle = sm.find(Sha1Hash.parseHex(hash))
                if (handle != null) {
                    sm.remove(handle)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error removing torrent", e)
            }
        }

        // Clean download directory
        saveDir?.let { dir ->
            try {
                dir.listFiles()?.forEach { it.deleteRecursively() }
            } catch (_: Exception) {}
        }

        _state.value = TorrentStreamState()
    }

    fun shutdown() {
        stopStream()
        scope.cancel()
        try {
            sessionManager?.stop()
        } catch (_: Exception) {}
        sessionManager = null
    }

    // --- Live tracker scrape (BEP 15 UDP protocol) ---

    data class ScrapeStats(val seeders: Int, val leechers: Int)

    /**
     * Scrape live seed/peer counts for multiple torrent hashes via UDP trackers.
     * Returns a map of hash → ScrapeStats. Must be called from a coroutine (IO dispatcher).
     */
    suspend fun scrapePeers(hashes: List<String>): Map<String, ScrapeStats> {
        return withContext(Dispatchers.IO) {
            val results = java.util.concurrent.ConcurrentHashMap<String, ScrapeStats>()
            val jobs = hashes.map { hash ->
                async {
                    try {
                        val stats = scrapeFromTrackers(hash)
                        if (stats != null) results[hash] = stats
                    } catch (e: Exception) {
                        Log.w(TAG, "Scrape failed for $hash: ${e.message}")
                    }
                }
            }
            jobs.forEach { it.await() }
            results.toMap()
        }
    }

    private fun scrapeFromTrackers(hash: String): ScrapeStats? {
        val infoHash = try {
            hexToBytes(hash)
        } catch (_: Exception) { return null }
        if (infoHash.size != 20) return null

        for (tracker in TRACKERS) {
            try {
                val hostPort = tracker
                    .removePrefix("udp://")
                    .removeSuffix("/announce")
                val stats = udpScrape(hostPort, infoHash)
                if (stats != null) return stats
            } catch (_: Exception) { }
        }
        return null
    }

    private fun udpScrape(hostPort: String, infoHash: ByteArray): ScrapeStats? {
        val socket = java.net.DatagramSocket()
        socket.soTimeout = 3000
        try {
            val parts = hostPort.split(":")
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: return null
            val addr = java.net.InetAddress.getByName(host)

            // Step 1: Connect
            val txId = (Math.random() * Int.MAX_VALUE).toInt()
            val connectReq = java.nio.ByteBuffer.allocate(16)
            connectReq.putLong(0x41727101980L) // magic
            connectReq.putInt(0)               // action: connect
            connectReq.putInt(txId)
            val connectPacket = java.net.DatagramPacket(connectReq.array(), 16, addr, port)
            socket.send(connectPacket)

            val respBuf = ByteArray(16)
            val respPacket = java.net.DatagramPacket(respBuf, 16)
            socket.receive(respPacket)

            val resp = java.nio.ByteBuffer.wrap(respBuf)
            val action = resp.int
            val respTxId = resp.int
            val connectionId = resp.long
            if (action != 0 || respTxId != txId) return null

            // Step 2: Scrape
            val txId2 = (Math.random() * Int.MAX_VALUE).toInt()
            val scrapeReq = java.nio.ByteBuffer.allocate(36)
            scrapeReq.putLong(connectionId)
            scrapeReq.putInt(2)        // action: scrape
            scrapeReq.putInt(txId2)
            scrapeReq.put(infoHash)
            val scrapePacket = java.net.DatagramPacket(scrapeReq.array(), 36, addr, port)
            socket.send(scrapePacket)

            val scrapeBuf = ByteArray(20)
            val scrapeRespPacket = java.net.DatagramPacket(scrapeBuf, 20)
            socket.receive(scrapeRespPacket)

            val scrapeResp = java.nio.ByteBuffer.wrap(scrapeBuf)
            val scrapeAction = scrapeResp.int
            val scrapeTxId = scrapeResp.int
            if (scrapeAction != 2 || scrapeTxId != txId2) return null

            val seeders = scrapeResp.int
            scrapeResp.int // completed — skip
            val leechers = scrapeResp.int

            return ScrapeStats(seeders, leechers)
        } catch (_: Exception) {
            return null
        } finally {
            socket.close()
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
