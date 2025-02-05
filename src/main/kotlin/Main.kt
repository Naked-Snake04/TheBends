import java.awt.*
import javax.swing.*
import kotlin.concurrent.thread

fun main() {
    val frame = JFrame("The Bends")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.setSize(400,200)

    val label = JLabel("Загрузите аудио-файл для анализа.", SwingConstants.CENTER)
    label.font = Font("Arial", Font.PLAIN, 18)
    frame.add(label, BorderLayout.CENTER)

    val comboBox = JComboBox(FFTLibraryEnum.entries.toTypedArray())
    frame.add(comboBox, BorderLayout.NORTH)

    val fileChooser = JFileChooser();
    val loadButton = JButton("Загрузить файл")

    loadButton.addActionListener {
        var selectedItem = comboBox.selectedItem
        val returnValue = fileChooser.showOpenDialog(frame)

        if (returnValue == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            label.text = "Обрабатывается: ${file.name}"
            thread {
                try {
                    AudioUtils.analyzeAudioFile(file, label, selectedItem)
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        label.text = "Ошибка при обработке файла: ${e.message}"
                    }
                }
            }
        }
    }

    frame.add(loadButton, BorderLayout.SOUTH)
    frame.isVisible = true
}
