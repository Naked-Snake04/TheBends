enum class InstrumentType {
    ACOUSTIC, BASS, ELECTRIC;

    override fun toString(): String {
        return when (this) {
            ACOUSTIC -> "Акустическая гитара"
            BASS -> "Бас-гитара"
            ELECTRIC -> "Электрогитара"
        }
    }
}