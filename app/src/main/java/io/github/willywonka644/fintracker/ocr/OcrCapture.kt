package io.github.willywonka644.fintracker.ocr

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text

/**
 * Turns ML Kit's result into the positioned form the shared parser understands.
 *
 * Lines without a bounding box are dropped: the whole point of this type is the
 * position, and a line without one cannot contribute to a geometric rule. Their
 * text is not lost — [OcrPage.rawText] still carries ML Kit's own concatenation
 * in full, which is what the amount and date logic reads.
 */
fun Text.toOcrPage(imageWidth: Int, imageHeight: Int): OcrPage {
    val lines = textBlocks.flatMapIndexed { blockIndex, block ->
        block.lines.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            OcrLine(
                text = line.text,
                left = box.left,
                top = box.top,
                right = box.right,
                bottom = box.bottom,
                blockIndex = blockIndex,
                confidence = line.confidence,
                angle = line.angle,
            )
        }
    }
    return OcrPage(
        rawText = text,
        lines = lines,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
    )
}

/**
 * The page dimensions in the frame ML Kit reports bounding boxes in.
 *
 * [InputImage.getWidth] and [InputImage.getHeight] describe the image as stored,
 * while the boxes come back in the **upright** image. For a photo held portrait
 * but stored landscape with a rotation flag, using the stored dimensions puts
 * every box outside the page and makes "how far down the receipt is this line"
 * nonsense. Hence the swap on the quarter turns.
 */
fun InputImage.uprightSize(): Pair<Int, Int> =
    if (rotationDegrees == 90 || rotationDegrees == 270) height to width else width to height

// A debug-only dump of every recognised page used to live here, writing the
// positioned lines to app-private storage as JSON. It is how the sample for #84
// was gathered, and it is gone because it kept writing the full text of every
// receipt — addresses and card number fragments included — to a phone that runs
// a debug build every day. Restore it from the history of this file if the
// merchant rule ever needs re-measuring; a file, not logcat, because the ring
// buffer drops the oldest scans and a single log message is cut off at ~4 KB,
// which makes a truncated capture look like a parser fault.
