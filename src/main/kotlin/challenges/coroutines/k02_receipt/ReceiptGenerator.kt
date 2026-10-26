package challenges.coroutines.k02_receipt

class Receipt(val orderId: Int, val sizeBytes: Int)

/** Legacy Java storage layer. Every call does blocking disk I/O. */
interface ReceiptStorage {
    fun readTemplate(): ByteArray
    fun save(orderId: Int, pdf: ByteArray)
}

/**
 * Builds the PDF receipt after checkout. The "Order placed!" screen calls this from
 * viewModelScope.launch { }, which runs on the main thread.
 */
class ReceiptGenerator(private val storage: ReceiptStorage) {

    suspend fun generate(orderId: Int): Receipt {
        val template = storage.readTemplate()
        val pdf = renderPdf(template, orderId)
        storage.save(orderId, pdf)
        return Receipt(orderId, pdf.size)
    }

    /** Lays out and rasterizes the receipt. Pure CPU work, about 300ms on a mid-range phone. */
    private fun renderPdf(template: ByteArray, orderId: Int): ByteArray {
        val end = System.nanoTime() + RENDER_NANOS
        var hash = orderId.toLong()
        while (System.nanoTime() < end) hash = hash * 31 + template.size
        return ByteArray(1024) { (hash shr (it % 64)).toByte() }
    }

    companion object {
        private const val RENDER_NANOS = 300_000_000L
    }
}
