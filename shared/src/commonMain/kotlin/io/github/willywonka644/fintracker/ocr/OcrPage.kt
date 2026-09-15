package io.github.willywonka644.fintracker.ocr

import kotlinx.serialization.Serializable

/**
 * One recognised line of text, together with where it sits on the photographed
 * receipt.
 *
 * Issue #84. Amount and date can be found in the flat text because both are
 * recognisable by their shape — a money value looks like a money value wherever
 * it stands. A shop name does not: it looks like any other text and is
 * identifiable **only by its position on the paper**, at the top and usually in
 * larger print than the rest. That is exactly the information `visionText.text`
 * throws away, so the merchant needs this type instead.
 *
 * Coordinates are pixels in the upright image, the frame ML Kit reports its
 * bounding boxes in. They are meaningless on their own — a receipt photographed
 * from further away yields smaller numbers throughout — so any rule built on
 * them has to be relative, either to [OcrPage.imageHeight] or to the other lines.
 */
@Serializable
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    /**
     * Which `Text.TextBlock` this line came from. ML Kit's own grouping of lines
     * into blocks is a hint worth keeping: a shop's name and its address often
     * land in one block, while an article column forms another.
     */
    val blockIndex: Int,
    /**
     * ML Kit's own confidence for the line. Nullable only so that test fixtures
     * can leave it out — the Android recogniser always reports a value.
     */
    val confidence: Float? = null,
    /**
     * Line rotation in degrees, same nullability caveat as [confidence].
     *
     * Nothing reads this today, and that is deliberate rather than an oversight.
     * It was captured to test whether print size could identify the shop name;
     * the box height is inflated by tilt and has to be corrected by this angle
     * before it means anything, which is how that idea was measured and
     * rejected (see `ReceiptParser.extractMerchant`). It is kept because
     * gathering it costs nothing on every future scan, while re-capturing a
     * sample of real receipts costs a scanning session.
     */
    val angle: Float? = null,
) {
    val height: Int get() = bottom - top
    val width: Int get() = right - left
}

/**
 * The full result of recognising one receipt photo: the flat text exactly as ML
 * Kit produced it, plus the same content resolved into positioned lines.
 *
 * [rawText] is carried alongside rather than reconstructed from [lines] on
 * purpose. The amount and date logic was measured against ML Kit's own
 * concatenation over twelve real receipts (#82); joining the lines back together
 * here would produce something very nearly identical, and "very nearly" is how a
 * working parser quietly regresses. The text path keeps its original input.
 */
@Serializable
data class OcrPage(
    val rawText: String,
    val lines: List<OcrLine>,
    val imageWidth: Int,
    val imageHeight: Int,
)
