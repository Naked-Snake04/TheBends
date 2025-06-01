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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.swing.JFrame

class AudioUtils {

    companion object {
        private var timeElapsed = 0.0
        private val accuracySeries = XYSeries("Точность в %")
        private var firstFrequency = -1.0
        private var maxAccuracy = -1.0
        private var maxSemitone = -1.0
        private var bendNotFull = 0.0
        private var isFirstSecond = false
        fun analyzeAudioFile(
            file: File,
            label: JLabel,
            selectedItem: Any?,
            selectedInstrumentType: Any?,
            semitone: Double
        ) {
            try {
                // Сбрасываем старые значения графика
                accuracySeries.clear()
                timeElapsed = 0.0
                isFirstSecond = false
                bendNotFull = 0.0
                firstFrequency = -1.0
                maxAccuracy = -1.0
                maxSemitone = -1.0
                val targetSemitone: Double = semitone
                var audioInputStream: AudioInputStream = AudioSystem.getAudioInputStream(file)
                var format = audioInputStream.format
                val sampleRate = format.sampleRate.toInt()
                var dataLineInfo = DataLine.Info(SourceDataLine::class.java, format)

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
                val bytesPerSample = format.frameSize / format.channels
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
//                    sourceLine.write(buffer, 0, buffer.size)
                    val audioData = when (bytesPerSample) {
                        1 -> buffer.map { it.toDouble() / 128.0 } // 8-битный звук
                        2 -> {
                            val shorts = ShortArray(bytesRead /2)
                            for (i in shorts.indices) {
                                shorts[i] = ((buffer[2 * i + 1].toInt() shl 8) or (buffer[2 * i].toInt() and 0xFF)).toShort()
                            }
                            shorts.map { it.toDouble() / 32768.0 } // 16-битный звук
                        }
                        4 -> {
                            // 32-битный звук (Float)
                            val floats = FloatArray(bytesRead / 4)
                            val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in floats.indices) {
                                floats[i] = byteBuffer.getFloat()
                            }
                            // Нужно преобразовать Float в Double для корректной работы
                            floats.map { it.toDouble() }
                        }
                        else -> throw UnsupportedOperationException("Неподдерживаемый формат аудио.")
                    }
                    val denoisedData = noiseReduction(audioData)
                    val outputBuffer = when (bytesPerSample) {
                        1 -> {
                            val byteArray = ByteArray(denoisedData.size)
                            denoisedData.forEachIndexed { i, sample ->
                                byteArray[i] = (sample * 127).toInt().toByte()
                            }
                            byteArray
                        }
                        2 -> {
                            val byteArray = ByteArray(denoisedData.size * 2)
                            val bb = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
                            denoisedData.forEach { sample ->
                                bb.putShort((sample * 32767).toInt().toShort())
                            }
                            byteArray
                        }
                        4 -> {
                            val byteArray = ByteArray(denoisedData.size * 4)
                            val bb = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
                            denoisedData.forEach { sample ->
                                bb.putFloat(sample.toFloat())
                            }
                            byteArray
                        }
                        else -> throw UnsupportedOperationException("Неподдерживаемый формат аудио.")
                    }

                    // Запись данных с проверкой кратности frameSize
                    var offset = 0
                    while (offset < outputBuffer.size) {
                        val chunk = min(outputBuffer.size - offset, sourceLine.available())
                        if (chunk <= 0) continue

                        // Убедимся, что записываем кратное frameSize количество байт
                        val alignedChunk = chunk - (chunk % format.frameSize)
                        if (alignedChunk > 0) {
                            sourceLine.write(outputBuffer, offset, alignedChunk)
                            offset += alignedChunk
                        }
                    }

                    val frequency = when (selectedItem) {
                        FFTLibraryEnum.J_TRANSFORMS -> detectFrequencyJTransform(denoisedData, sampleRate, selectedInstrumentType)
                        FFTLibraryEnum.APACHE_COMMONS_MATH -> detectFrequencyACM(audioData, sampleRate)
                        else -> throw UnsupportedOperationException("Библиотека пока не подключена")
                    }
                    if (frequency == null) {
                        SwingUtilities.invokeLater {
                            label.text = "Не удалось определить частоту."
                        }
                        continue // Пропускаем итерацию цикла
                    }

                    if (firstFrequency < 0) {
                        firstFrequency = frequency
                    }

                    val resultSemitone = calculateBentFrequency(firstFrequency, frequency) // ожидаемый полутон
                    if (resultSemitone > maxSemitone) maxSemitone = resultSemitone
                    val accuracy = (resultSemitone / targetSemitone * 100) // точность - отношение между полутонами
                    if (accuracy > maxAccuracy) maxAccuracy = accuracy // Максимальный бенд
                    bendNotFull = abs(maxSemitone - targetSemitone) // Бенд недотянут на сколько-то полутонов
                    SwingUtilities.invokeLater {
                        label.text = run {
                            accuracySeries.add(timeElapsed, accuracy)
                            timeElapsed += 1.0

                            val result = StringBuilder()
                            // Label не умеет в \n, поэтому для переноса строк используем HTML
                            result.append("<html>")
                            result.append("Ожидаемый полутон: ${"%.2f".format(targetSemitone)}<br>")
                            result.append("Вычисленный полутон: ${"%.2f".format(resultSemitone)}<br>")
                            result.append("Точность: ${"%.2f".format(accuracy)}<br>")
                            result.append("Максимальный полутон: ${"%.2f".format(maxSemitone)}<br>")
                            result.append("Максимальная точность: ${"%.2f".format(maxAccuracy)}<br>")
                            if (maxAccuracy < 100) result.append("Бенд недотянут на ${"%.2f".format(bendNotFull)}<br>")
                            else if (maxAccuracy > 100) result.append("Бенд перетянут на ${"%.2f".format(bendNotFull)}<br>")
                            result.append("</html>")

                            result.toString()
                        }
                    }
                    Thread.sleep(100) // Ждать 1/10 секунды для следующего анализа
                }

                val result = StringBuilder()
                result.append("<html>")
                result.append("Максимальный полутон: ${"%.2f".format(maxSemitone)}<br>")
                result.append("Максимальная точность: ${"%.2f".format(maxAccuracy)}<br>")
                if (maxAccuracy < 100) result.append("Бенд недотянут на ${"%.2f".format(bendNotFull)}<br>")
                else if (maxAccuracy > 100) result.append("Бенд перетянут на ${"%.2f".format(bendNotFull)}<br>")
                result.append("</html>")
                label.text = result.toString()

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
            val dataset = XYSeriesCollection(accuracySeries)
            val chart: JFreeChart = ChartFactory.createXYLineChart(
                "Точность бенда",
                "Время (с)",
                "Точность (%)",
                dataset
            )
            val chartPanel = ChartPanel(chart)
            val frame = JFrame("График точности бенда")
            frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            frame.add(chartPanel, BorderLayout.CENTER)
            frame.setSize(800, 600)
            frame.setLocationRelativeTo(null)
            frame.isVisible = true
        }

        private fun detectFrequencyJTransform(audioData: List<Double>, sampleRate: Int, instrumentType: Any?): Double? {
            val fftSize = audioData.size
            if (fftSize < 2) return null

            // Применение окна Хэмминга
            val windowedData = calculateHammingWindow(audioData, fftSize)

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
            val cleanedMagnitudes = suppressHarmonics(magnitudes, instrumentType, sampleRate, fftSize)
            // Вычисление частоты
            val frequency = findFrequency(cleanedMagnitudes, sampleRate, fftSize)
            return if (frequency!! in 20.0..20000.0) frequency else null
        }

        private fun detectFrequencyACM(audioData: List<Double>, sampleRate: Int): Double? {
            val fftSize = audioData.size
            if (fftSize < 2) return null

            // Применение окна Хэмминга
            val windowedData = calculateHammingWindow(audioData, fftSize)
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

            val frequency = findFrequency(magnitudes, sampleRate, fftSize)
            return if (frequency!! in 20.0..20000.0) frequency else null
        }

        private fun calculateBentFrequency(originalFrequency: Double?, frequency: Double?): Double {
            return 12.0 * log2(frequency!! / originalFrequency!!)
        }

        private fun calculateHammingWindow(audioData: List<Double>, fftSize: Int) : DoubleArray {
            val windowedData = DoubleArray(fftSize)
            for (i in windowedData.indices) {
                windowedData[i] = audioData[i] * (0.54 - 0.46 * cos(2.0 * Math.PI * i / (fftSize - 1)))
            }
            return windowedData
        }

        private fun findFrequency(magnitudes: DoubleArray, sampleRate: Int, fftSize: Int): Double? {
            // делаем Нойз гейт на основе среднего отклонения
            val mean = magnitudes.average()
            val stdDev = sqrt(magnitudes.map { (it - mean).pow(2) }.average())
            val threshold = mean + stdDev * 2.0

            val peakIndex = magnitudes.indices
                .filter { magnitudes[it] >= threshold }
                .maxByOrNull { magnitudes[it] } ?: return null

            // Вычисление частоты
            return sampleRate * peakIndex.toDouble() / fftSize
        }

        private fun noiseReduction(audioData: List<Double>, alpha: Double = 0.03): List<Double> {
            if (audioData.isEmpty()) return emptyList()
            val filtered = MutableList(audioData.size) { 0.0 }
            filtered[0] = audioData[0]

            for (i in 1 until audioData.size) {
                filtered[i] = alpha * audioData[i] + (1 - alpha) * filtered[i-1]
            }
            return filtered
        }

        private fun suppressHarmonics(magnitudes: DoubleArray, type: Any?, sampleRate: Int, fftSize: Int) : DoubleArray {
            val result = magnitudes.copyOf()
            val (minFreq, maxFreq, harmonicWeights) = when (type) {
                InstrumentType.BASS -> Triple(40, 500, listOf(0.5, 0.3, 0.15, 0.1))
                InstrumentType.ACOUSTIC -> Triple(80, 1200, listOf(0.6, 0.4, 0.25, 0.15))
                InstrumentType.ELECTRIC -> Triple(70, 2000, listOf(0.7, 0.5, 0.3, 0.2))
                else -> Triple(0, 0, listOf(0, 0, 0, 0))
            }
            for (i in result.indices) {
                val frequency = i * sampleRate.toDouble() / fftSize
                if(frequency < minFreq || frequency > maxFreq) continue

                for ((harmonicIndex, weight) in harmonicWeights.withIndex()) {
                    val harmonicFreq = frequency * (harmonicIndex + 2)
                    val harmonicBin = (harmonicFreq * fftSize / sampleRate).toInt()
                    if (harmonicBin in result.indices) {
                        result[i] += result[harmonicBin] * weight.toDouble()
                    }
                }
            }
            return result
        }
    }
}