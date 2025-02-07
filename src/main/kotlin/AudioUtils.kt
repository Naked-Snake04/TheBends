import java.io.File
import javax.sound.sampled.*
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.math.*
import org.jtransforms.fft.DoubleFFT_1D
import org.apache.commons.math3.transform.*

class AudioUtils {

    companion object {
        fun analyzeAudioFile(file: File, label: JLabel, selectedItem: Any?) {

            try {
                val audioInputStream: AudioInputStream = AudioSystem.getAudioInputStream(file)
                val format = audioInputStream.format
                val sampleRate = format.sampleRate.toInt()
                val dataLineInfo = DataLine.Info(SourceDataLine::class.java, format)
                val bytesPerSample = format.frameSize / format.channels

                if (!AudioSystem.isLineSupported(dataLineInfo)) {
                    SwingUtilities.invokeLater {
                        label.text = "Линия воспроизведения не поддерживается."
                    }
                    return
                }

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
    }
}