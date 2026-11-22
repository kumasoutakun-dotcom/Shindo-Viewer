package jp.example.kyoshinviewer.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.TextWatcher
import android.text.style.MetricAffectingSpan
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.WeakHashMap

/**
 * 日本語文字はGenZenGothicKaiC、それ以外のうちAksharが字形を持つ文字はAksharで描画する。
 * 元のテキスト、色、サイズ、太字などの設定は変更しない。
 */
object BilingualFontStyler {

    private val installedViews = WeakHashMap<TextView, Boolean>()

    private class AksharTypefaceSpan(
        private val typeface: Typeface
    ) : MetricAffectingSpan() {
        override fun updateDrawState(textPaint: TextPaint) {
            textPaint.typeface = typeface
        }

        override fun updateMeasureState(textPaint: TextPaint) {
            textPaint.typeface = typeface
        }
    }

    fun install(
        root: View,
        japaneseTypeface: Typeface,
        aksharTypeface: Typeface
    ) {
        installRecursively(root, japaneseTypeface, aksharTypeface)
    }

    private fun installRecursively(
        view: View,
        japaneseTypeface: Typeface,
        aksharTypeface: Typeface
    ) {
        if (view is TextView) {
            installTextView(view, japaneseTypeface, aksharTypeface)
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                installRecursively(view.getChildAt(index), japaneseTypeface, aksharTypeface)
            }
        }
    }

    private fun installTextView(
        textView: TextView,
        japaneseTypeface: Typeface,
        aksharTypeface: Typeface
    ) {
        val alreadyInstalled = synchronized(installedViews) {
            if (installedViews.containsKey(textView)) {
                true
            } else {
                installedViews[textView] = true
                false
            }
        }
        if (alreadyInstalled) return

        val style = textView.typeface?.style ?: Typeface.NORMAL
        textView.typeface = Typeface.create(japaneseTypeface, style)

        val aksharPaint = Paint().apply {
            typeface = Typeface.create(aksharTypeface, style)
        }

        textView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(editable: Editable?) {
                if (editable != null) applyAksharSpans(editable, aksharPaint)
            }
        })

        val currentText = textView.text
        if (currentText is Editable) {
            applyAksharSpans(currentText, aksharPaint)
        } else {
            val styled = SpannableStringBuilder(currentText)
            applyAksharSpans(styled, aksharPaint)
            textView.setText(styled, TextView.BufferType.SPANNABLE)
        }
    }

    private fun applyAksharSpans(
        text: Editable,
        aksharPaint: Paint
    ) {
        text.getSpans(0, text.length, AksharTypefaceSpan::class.java)
            .forEach { text.removeSpan(it) }

        var runStart = -1
        var offset = 0

        while (offset < text.length) {
            val codePoint = Character.codePointAt(text, offset)
            val charCount = Character.charCount(codePoint)
            val glyph = !isJapaneseCodePoint(codePoint) &&
                aksharPaint.hasGlyph(String(Character.toChars(codePoint)))

            if (glyph) {
                if (runStart < 0) runStart = offset
            } else if (runStart >= 0) {
                text.setSpan(
                    AksharTypefaceSpan(aksharPaint.typeface),
                    runStart,
                    offset,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                runStart = -1
            }

            offset += charCount
        }

        if (runStart >= 0) {
            text.setSpan(
                AksharTypefaceSpan(aksharPaint.typeface),
                runStart,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun isJapaneseCodePoint(codePoint: Int): Boolean {
        return codePoint in 0x3000..0x303F ||       // CJK symbols and Japanese punctuation
            codePoint in 0x3040..0x309F ||          // Hiragana
            codePoint in 0x30A0..0x30FF ||          // Katakana
            codePoint in 0x31F0..0x31FF ||          // Katakana phonetic extensions
            codePoint in 0x3400..0x4DBF ||          // CJK unified ideographs extension A
            codePoint in 0x4E00..0x9FFF ||          // CJK unified ideographs
            codePoint in 0xF900..0xFAFF ||          // CJK compatibility ideographs
            codePoint in 0xFF61..0xFF9F ||          // Halfwidth Katakana
            codePoint in 0x20000..0x2FA1F           // Supplementary CJK ideographs
    }
}
