package io.legado.app.lib.prefs

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import io.legado.app.R
import io.legado.app.utils.progressAdd

class SeekBarPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    private var mMinValue = 0
    private var mMaxValue = 1000

    val minValue: Int
        get() = mMinValue

    val maxValue: Int
        get() = mMaxValue

    private var mSeekBar: SeekBar? = null
    private var mValueText: TextView? = null

    private var seekPlus: ImageView? = null
    private var seekReduce: ImageView? = null

    var value: Int = 0
        set(value) {
            field = value.coerceIn(mMinValue, mMaxValue)
            persistInt(field)
            mSeekBar?.progress = field - mMinValue
            mValueText?.text = field.toString()
        }

    init {
        layoutResource = R.layout.view_preference_seekbar
        val a = context.obtainStyledAttributes(attrs, R.styleable.NumberPickerPreference)
        mMinValue = a.getInt(R.styleable.NumberPickerPreference_MinValue, mMinValue)
        mMaxValue = a.getInt(R.styleable.NumberPickerPreference_MaxValue, mMaxValue)
        a.recycle()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        bindLegacyView(holder.itemView)
    }

    /** Updates the persisted value and notifies Compose-backed preference rows. */
    fun updateValue(value: Int) {
        val next = value.coerceIn(mMinValue, mMaxValue)
        if (this.value != next) {
            this.value = next
            notifyChanged()
        }
    }

    /** Preserves the legacy SeekBar listener timing after the thumb is released. */
    fun finishValueChange() {
        val currentValue = value
        if (callChangeListener(currentValue)) {
            updateValue(currentValue)
        }
    }

    private fun bindLegacyView(view: android.view.View) {
        mSeekBar = view.findViewById(R.id.seek_bar)
        mValueText = view.findViewById(R.id.tv_seek_value)
        seekPlus = view.findViewById(R.id.iv_seek_plus)
        seekReduce = view.findViewById(R.id.iv_seek_reduce)
        (view.findViewById<TextView>(R.id.preference_title))?.text = title
        (view.findViewById<TextView>(R.id.preference_desc))?.text = summary
        mSeekBar?.apply {
            max = mMaxValue - mMinValue
            progress = value - mMinValue

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val newValue = progress + mMinValue
                    mValueText?.text = newValue.toString()
                    if (fromUser) {
                        value = newValue
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    val newValue = seekBar.progress + mMinValue
                    if (callChangeListener(newValue)) {
                        value = newValue
                    }
                }
            })
        }
        mValueText?.text = value.toString()
        seekPlus?.setOnClickListener {
            mSeekBar?.progressAdd(1)
            value++
        }
        seekReduce?.setOnClickListener {
            mSeekBar?.progressAdd(-1)
            value--
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getInt(index, 500)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        value = getPersistedInt((defaultValue as? Int) ?: 0)
    }


}
