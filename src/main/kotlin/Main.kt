import java.awt.*
import java.io.File
import java.text.NumberFormat
import javax.swing.*
import kotlin.concurrent.thread

fun main() {
    val frame = JFrame("The Bends")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.setSize(600, 400)

    val label = JLabel("Загрузите аудио-файл для анализа.", SwingConstants.CENTER)
    label.font = Font("Arial", Font.PLAIN, 18)
    frame.add(label, BorderLayout.CENTER)

    val comboBox = JComboBox(FFTLibraryEnum.entries.toTypedArray())
    frame.add(comboBox, BorderLayout.NORTH)

    val fileChooser = JFileChooser().apply {
        val initialDirectory1 = File("C:\\temp\\TheBends\\src\\main\\resources")
        val initialDirectory2 = File("C:\\Users\\Savel\\IdeaProjects\\TheBends\\src\\main\\resources")
        currentDirectory = if (initialDirectory1.exists()) {
            initialDirectory1
        } else if (initialDirectory2.exists()) {
            initialDirectory2
        } else{
            File(System.getProperty("user.home"))
        }
    }

    val loadButton = JButton("Загрузить файл")
    val numberFormat = NumberFormat.getInstance().apply {
        isGroupingUsed = false
    }
    val textFieldSemitone = JFormattedTextField(numberFormat) // полутона
    textFieldSemitone.columns = 5
    textFieldSemitone.toolTipText = "Введите количество полутонов"
    val labelSemitone = JLabel("Кол-во полутонов: ")

    val panel = JPanel()
    panel.add(labelSemitone)
    panel.add(textFieldSemitone)
    panel.add(loadButton)

    loadButton.addActionListener {
        val selectedItem = comboBox.selectedItem
        val returnValue = fileChooser.showOpenDialog(frame)

        val semitoneValue = when (val value = textFieldSemitone.value) {
            is Number -> value.toDouble()
            else -> {
                SwingUtilities.invokeLater {
                    label.text = "Ошибка: Введите корректное число для полутонов"
                }
                return@addActionListener
            }
        }

        if (returnValue == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            label.text = "Обрабатывается: ${file.name}"
            thread {
                try {
                    AudioUtils.analyzeAudioFile(file, label, selectedItem, semitoneValue)
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        label.text = "Ошибка при обработке файла: ${e.message}"
                    }
                }
            }
        }
    }

    frame.add(panel, BorderLayout.SOUTH)
    frame.isVisible = true
}
