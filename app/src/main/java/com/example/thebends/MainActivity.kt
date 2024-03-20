package com.example.thebends

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.thebends.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

//             TODO:
//              1)Сделать классы
//                а)Audio, в котором будут записываться звуки
//                б)Tuner, в котором будет высчитываться частота звука и определяться нота
//              2)Расчёты вычисления запихнуть в freq в activity_main
//              3)Запихнуть алгоритм по обработке бэндов в Tuner
//              4)Добавить в ActivityMain соответствующие кнопки для показа что бэнд взят верно
//                а также выбор параметров для этого)

    }
}