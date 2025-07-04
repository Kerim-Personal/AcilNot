package com.codenzi.acilnot

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

class SelectionAwareEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private var selectionChangedListener: ((Int, Int) -> Unit)? = null

    fun setOnSelectionChangedListener(listener: (Int, Int) -> Unit) {
        this.selectionChangedListener = listener
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        selectionChangedListener?.invoke(selStart, selEnd)
    }
}