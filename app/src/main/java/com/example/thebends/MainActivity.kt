package com.example.thebends

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.example.thebends.core.Audio
import com.example.thebends.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    //             TODO:
    //              1)Сделать классы
    //                ~~а)Audio, в котором будут записываться звуки~~
    //                б)Tuner, в котором будет высчитываться частота звука и определяться нота
    //              2)Расчёты вычисления запихнуть в freq в activity_main
    //              3)Запихнуть алгоритм по обработке бэндов в Tuner
    //              4)Добавить в ActivityMain соответствующие кнопки для показа что бэнд взят верно
    //                а также выбор параметров для этого)

    private lateinit var binding: ActivityMainBinding
    private lateinit var tuner: Tuner
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO),
                1
            )
        }

        tuner = Tuner(this, binding)
    }

    override fun onResume() {
        super.onResume()
        getPreferences()
        tuner.start()
    }

    override fun onPause() {
        super.onPause()
        tuner.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        System.runFinalization()
    }

    private fun getPreferences() {
        val preferences: SharedPreferences = PreferenceManager
            .getDefaultSharedPreferences(this);
        var audio: Audio = tuner.getAudio()
        if (audio != null) {
            audio
        }
    }
}