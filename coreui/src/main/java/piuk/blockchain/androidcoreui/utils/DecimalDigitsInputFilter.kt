package piuk.blockchain.androidcoreui.utils

import android.text.InputFilter
import android.text.Spanned
import java.util.regex.Pattern

class DecimalDigitsInputFilter(digitsBeforeZero: Int, digitsAfterZero: Int, private val prefixOrSuffix: String = "") :
    InputFilter {

    private val mPattern = Pattern.compile("-?[0-9]{0," + digitsBeforeZero + "}+((\\.[0-9]{0," +
            digitsAfterZero + "})?)||(\\.)?")

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        if (source.toString() == prefixOrSuffix) return null
        val replacement = source.subSequence(start, end).toString()

        val newVal = (dest.subSequence(0, dstart).toString() + replacement +
                dest.subSequence(dend, dest.length).toString())
        val matcher = mPattern.matcher(newVal.removePrefix(prefixOrSuffix).removeSuffix(prefixOrSuffix))

        if (matcher.matches()) return null
        return if (source.isEmpty()) dest.subSequence(dstart, dend) else ""
    }
}