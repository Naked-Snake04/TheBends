import java.io.File
import javax.sound.sampled.*
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.math.*
import org.jtransforms.fft.DoubleFFT_1D
import org.apache.commons.math3.transform.*
import org.jfree.chart.ChartFactory
import org.jfree.chart.ChartPanel
import org.jfree.chart.JFreeChart
import org.jfree.data.xy.XYSeries
import org.jfree.data.xy.XYSeriesCollection
import java.awt.BorderLayout
import javax.swing.JFrame

class AudioUtils {

    companion object {
        private var timeElapsed = 0.0
        private val frequencySeries = XYSeries("Частота звука")

        fun analyzeAudioFile(file: File, label: JLabel, selectedItem: Any?) {
            try {
                var audioInputStream: AudioInputStream = AudioSystem.getAudioInputStream(file)
                var format = audioInputStream.format
                val sampleRate = format.sampleRate.toInt()
                var dataLineInfo = DataLine.Info(SourceDataLine::class.java, format)
                val bytesPerSample = format.frameSize / format.channels

                if (!AudioSystem.isLineSupported(dataLineInfo)) {
                    val supportedFormat = AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        format.sampleRate,
                        16, // Переводим 32-бит в 16-бит
                        format.channels,
                        format.channels * 2, // Размер фрейма для 16-битного звука
                        format.sampleRate,
                        false
                    )
                    val supportedDataLineInfo = DataLine.Info(SourceDataLine::class.java, supportedFormat)
                    if (!AudioSystem.isLineSupported(supportedDataLineInfo)) {
                        SwingUtilities.invokeLater {
                            label.text = "Система не поддерживает формат файла: $format"
                        }
                        return
                    }
                    audioInputStream = AudioSystem.getAudioInputStream(supportedFormat, audioInputStream)
                    format = supportedFormat
                    dataLineInfo = supportedDataLineInfo
                }

                createChart()

                val sourceLine = AudioSystem.getLine(dataLineInfo) as SourceDataLine
                sourceLine.open(format)
                sourceLine.start()

                val chunkFrames = when (selectedItem) {
                    FFTLibraryEnum.APACHE_COMMONS_MATH -> 8192 // Для этой либы нужно чтобы блок данных был размером степени два
                    else -> sampleRate / 5 // Количество фреймов на 1/5 секунды, так стабильнее звук идёт
                }
                val chunkSize = chunkFrames * format.frameSize // Размер данных в байтах
                val buffer = ByteArray(chunkSize)

                while (true) {
                    val bytesRead = audioInputStream.read(buffer, 0, chunkSize)
                    if (bytesRead == -1) break
                    sourceLine.write(buffer, 0, buffer.size)
                    val audioData = when (bytesPerSample) {
                        1 -> buffer.map { it.toDouble() / 128.0 } // 8-битный звук
                        2 -> {
                            val shorts = ShortArray(bytesRead /2)
                            for (i in shorts.indices) {
                                shorts[i] = ((buffer[2 * i + 1].toInt() shl 8) or (buffer[2 * i].toInt() and 0xFF)).toShort()
                            }
                            shorts.map { it.toDouble() / 32768.0 } // 16-битный звук
                        }
                        else -> throw UnsupportedOperationException("Неподдерживаемый формат аудио.")
                    }

                    val frequency = when (selectedItem) {
                        FFTLibraryEnum.J_TRANSFORMS -> detectFrequencyJTransform(audioData, sampleRate)
                        FFTLibraryEnum.APACHE_COMMONS_MATH -> detectFrequencyACM(audioData, sampleRate)
                        else -> throw UnsupportedOperationException("Библиотека пока не подключена")
                    }
                    SwingUtilities.invokeLater {
                        label.text = if (frequency != null) {
                            frequencySeries.add(timeElapsed, frequency)
                            timeElapsed += 1.0 // Увеличиваем время на 1 секунду
                            "Обнаружена частота: %.2f Гц.".format(frequency)
                        } else {
                            "Не удалось определить частоту."
                        }
                    }
                    Thread.sleep(100) // Ждать 1/10 секунды для следующего анализа
                }

                sourceLine.drain()
                sourceLine.close()

            } catch (e: UnsupportedAudioFileException) {
                SwingUtilities.invokeLater {
                    label.text = "Неподдерживаемый формат файла."
                }
            }
            catch (e: Exception) {
                SwingUtilities.invokeLater {
                    label.text = "Ошибка при обработке файла: ${e.message}"
                }
            }
        }

        private fun createChart() {
            // Создаем панель для графика
            val dataset = XYSeriesCollection(frequencySeries)
            val chart: JFreeChart = ChartFactory.createXYLineChart(
                "Частота звука в реальном времени",
                "Время (с)",
                "Частота (Гц)",
                dataset
            )
            val chartPanel = ChartPanel(chart)
            val frame = JFrame("График частоты звука")
            frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            frame.add(chartPanel, BorderLayout.CENTER)
            frame.setSize(800, 600)
            frame.isVisible = true
        }

        private fun detectFrequencyJTransform(audioData: List<Double>, sampleRate: Int): Double? {
            val fftSize = audioData.size
            if (fftSize < 2) return null

            // Применение окна Хэмминга
            val windowedData = DoubleArray(fftSize)
            for (i in windowedData.indices) {
                windowedData[i] = audioData[i] * (0.54 - 0.46 * cos(2.0 * Math.PI * i / (fftSize - 1)))
            }

            // Выполнение FFT
            val fft = DoubleFFT_1D(fftSize.toLong())
            val fftArray = DoubleArray(fftSize * 2) // Реальная и мнимая части
            System.arraycopy(windowedData, 0, fftArray, 0, fftSize)
            fft.realForwardFull(fftArray)

            // Вычисление амплитуд
            val magnitudes = DoubleArray(fftSize / 2) // Нам интересна только первая половина
            for (i in magnitudes.indices) {
                val real = fftArray[2 * i]
                val imag = fftArray[2 * i + 1]
                magnitudes[i] = sqrt(real * real + imag * imag)
            }

            val threshold = magnitudes.maxOrNull()?.times(0.1) ?: return null // Порог 10% от максимума

            // Поиск пикового значения
            val peakIndex = magnitudes.indices
                .filter { magnitudes[it] >= threshold }
                .maxByOrNull { magnitudes[it] } ?: return null

            // Вычисление частоты
            val frequency = sampleRate * peakIndex.toDouble() / fftSize
            return if (frequency in 20.0..20000.0) frequency else null
        }

        private fun detectFrequencyACM(audioData: List<Double>, sampleRate: Int): Double? {
            val fftSize = audioData.size
            if (fftSize < 2) return null

            // Применение окна Хэмминга
            val windowedData = DoubleArray(fftSize)
            for (i in windowedData.indices) {
                windowedData[i] = audioData[i] * (0.54 - 0.46 * cos(2.0 * Math.PI * i / (fftSize - 1)))
            }
            // Выполнение FFT с использованием Apache Commons Math
            val transformer = FastFourierTransformer(DftNormalization.STANDARD)
            val transformed = transformer.transform(windowedData, TransformType.FORWARD)

            // Вычисление амплитуд
            val magnitudes = DoubleArray(fftSize / 2)
            for (i in magnitudes.indices) {
                val real = transformed[i].real
                val imag = transformed[i].imaginary
                magnitudes[i] = sqrt(real * real + imag * imag)
            }
            // Поиск пикового значения с порогом
            val threshold = magnitudes.maxOrNull()?.times(0.1) ?: return null // Порог 10% от максимума
            val peakIndex = magnitudes.indices
                .filter { magnitudes[it] >= threshold }
                .maxByOrNull { magnitudes[it] } ?: return null

            // Вычисление частоты
            val frequency = sampleRate * peakIndex.toDouble() / fftSize
            return if (frequency in 20.0..20000.0) frequency else null
        }
/*
        private fun calculateBentFrequency(originalFrequency: Double?, semitone: Double): Double {
            if (originalFrequency != null) {
                return originalFrequency * 2.0.pow(semitone / 12.0)
            }
            return 0.0
        }*/
    }
}