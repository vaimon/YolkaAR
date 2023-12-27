package ru.mmcs.arplaygroud

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.skydoves.colorpickerview.listeners.ColorListener
import ru.mmcs.arplaygroud.databinding.ActivityIntroBinding


class IntroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIntroBinding

    private var color: Int = -1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntroBinding.inflate(layoutInflater)
        color = ContextCompat.getColor(applicationContext, R.color.colorAccent)
        setListeners()
        setContentView(binding.root)
    }

    private fun setListeners() {
        binding.colorPickerView.setColorListener(ColorListener { color, fromUser ->
            binding.btnStart.backgroundTintList = ColorStateList.valueOf(color)
            this.color = color
        })
        binding.btnStart.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).putExtra("color", color))
        }
    }
}