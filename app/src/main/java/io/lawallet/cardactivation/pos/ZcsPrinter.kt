package io.lawallet.cardactivation.pos

import android.text.Layout
import android.util.Log
import com.zcs.sdk.DriverManager
import com.zcs.sdk.Printer
import com.zcs.sdk.SdkResult
import com.zcs.sdk.Sys
import com.zcs.sdk.print.PrnStrFormat
import com.zcs.sdk.print.PrnTextFont
import io.lawallet.cardactivation.ui.QrBitmap
import io.lawallet.cardactivation.util.cardDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Thin Kotlin wrapper over the ZCS SmartPos SDK printer (CS30Pro / Z92).
 * Ported from the wrapper's `PrintThread.java`: the printer is a system service
 * reached via `DriverManager` — no AIDL binding. All printing runs on IO.
 */
object ZcsPrinter {
    private const val TAG = "ZcsPrinter"
    private const val QR_SIZE = 360

    private val driver: DriverManager by lazy { DriverManager.getInstance() }
    private val sys: Sys by lazy { driver.baseSysDevice }
    private val printer: Printer by lazy { driver.printer }

    /** App-scoped IO so a fired print survives screen navigation (never cancelled). */
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var initialized = false

    sealed interface PrintResult {
        data object Success : PrintResult

        data object OutOfPaper : PrintResult

        data class Error(val message: String) : PrintResult
    }

    /**
     * Kicks SDK init off the UI thread (App / MainActivity / permission grant).
     * Fire-and-forget so the 1s power-on retry never blocks the main thread; the
     * print path calls [ensureReady] which actually waits for it.
     */
    fun init() {
        ioScope.launch { ensureReady() }
    }

    /**
     * Blocking, serialized SDK init — safe to call from IO. `@Synchronized` so two
     * concurrent callers (e.g. prewarm + a print) don't run overlapping sdkInit()s:
     * the second waits and then sees the result. Returns true once `SDK_OK`.
     */
    @Synchronized
    private fun ensureReady(): Boolean {
        if (initialized) return true
        return try {
            var status = sys.sdkInit()
            if (status != SdkResult.SDK_OK) {
                sys.sysPowerOn()
                Thread.sleep(1000)
                status = sys.sdkInit()
            }
            initialized = status == SdkResult.SDK_OK
            Log.i(TAG, "sdkInit status=$status initialized=$initialized")
            initialized
        } catch (t: Throwable) {
            Log.e(TAG, "sdkInit failed", t)
            false
        }
    }

    /** Sample slip with a QR — verifies the driver end-to-end. */
    suspend fun printTest(): PrintResult =
        printSlip(subtitle = "Printer test OK", qrData = "https://lawallet.io", caption = null)

    /** Prints an activation slip: message + the activation-URL QR. */
    suspend fun printActivation(
        url: String,
        message: String = "Activate your card",
    ): PrintResult = printSlip(subtitle = message, qrData = url, caption = cardDomain(url))

    /**
     * Fire-and-forget print on the app-scoped IO dispatcher so the paper feeds
     * the instant it's requested — never queued behind UI/animation work and
     * never cancelled by leaving the screen. Use from button handlers.
     */
    fun printActivationNow(
        url: String,
        message: String = "Activate your card",
    ) {
        ioScope.launch { printActivation(url, message) }
    }

    /** Warm the SDK so the first real print fires without paying init latency. */
    fun prewarm() {
        ioScope.launch { ensureReady() }
    }

    private suspend fun printSlip(
        subtitle: String,
        qrData: String,
        caption: String?,
    ): PrintResult =
        withContext(Dispatchers.IO) {
            try {
                if (!initialized && !ensureReady()) {
                    return@withContext PrintResult.Error("Printer SDK not initialized")
                }
                if (printer.printerStatus == SdkResult.SDK_PRN_STATUS_PAPEROUT) {
                    return@withContext PrintResult.OutOfPaper
                }

                val fmt = PrnStrFormat().apply { font = PrnTextFont.MONOSPACE }
                fmt.ali = Layout.Alignment.ALIGN_CENTER

                printer.setPrintLine(2) // top free margin

                fmt.textSize = 34
                printer.setPrintAppendString("LaWallet", fmt)
                printer.setPrintLine(1)

                fmt.textSize = 24
                printer.setPrintAppendString(subtitle, fmt)
                printer.setPrintLine(2)

                // Print the SAME ZXing-rendered QR shown on screen — the printer's
                // native QR encoder produces a visually different code for the same
                // data, so render once (QrBitmap) and print that bitmap to match.
                printer.setPrintAppendBitmap(
                    QrBitmap.create(qrData, QR_SIZE),
                    Layout.Alignment.ALIGN_CENTER,
                )

                if (caption != null) {
                    printer.setPrintLine(1)
                    fmt.textSize = 18
                    printer.setPrintAppendString(caption, fmt)
                }

                // Two trailing blank lines (as printed content) so the paper
                // actually feeds out — a trailing line-feed with no content after
                // it can be dropped, leaving the slip under the print head.
                printer.setPrintAppendString("\n", fmt)
                printer.setPrintAppendString("\n", fmt)
                printer.setPrintLine(6) // tear-off margin

                when (val result = printer.setPrintStart()) {
                    SdkResult.SDK_OK -> PrintResult.Success
                    else -> PrintResult.Error("Print failed (code $result)")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "print error", t)
                PrintResult.Error(t.message ?: "Print error")
            }
        }
}
