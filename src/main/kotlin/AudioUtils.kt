import java.io.File
import javax.sound.sampled.*
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.math.*
import org.jtransforms.fft.DoubleFFT_1D

class AudioUtils {

    companion object {
        fun analyzeAudioFile(file: File, label: JLabel) {

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

                val chunkFrames = sampleRate / 10 // Количество фреймов на 1/10 секунды
                val chunkSize = chunkFrames * format.frameSize // Размер данных в байтах для 1/10 секунды
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

                    val frequency = detectFrequency(audioData, sampleRate)
                    SwingUtilities.invokeLater {
                        label.text = if (frequency != null) {
                            "Обнаружена частота: %.2f Гц.".format(frequency)
                        } else {
                            "Не удалось определить частоту."
                        }
                    }

/*
                    // Убедимся, что количество байтов кратно размеру фрейма
                    val alignedBytes = bytesRead - (bytesRead % format.frameSize)
                    if (alignedBytes > 0) {
                        sourceLine.write(buffer, 0, buffer.size)
                        val audioData = buffer.map { it.toDouble() / 128.0 }
                        val frequency = detectFrequency(audioData, sampleRate)

                        if (frequency != null) {
                            SwingUtilities.invokeLater {
                                label.text = "Обнаружена частота: %.2f Гц.".format(frequency)
                            }
                        } else {
                            SwingUtilities.invokeLater {
                                label.text = "Не удалось определить частоту."
                            }
                        }
                    }
*/
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

        private fun detectFrequency(audioData: List<Double>, sampleRate: Int): Double? {
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
    }
}