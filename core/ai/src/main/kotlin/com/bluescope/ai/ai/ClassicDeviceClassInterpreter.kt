package com.bluescope.ai.ai

data class DeviceClassInterpretation(
    val category: String,
    val description: String,
)

/** Pure interpreter for Bluetooth Classic major device-class bits. */
internal object ClassicDeviceClassInterpreter {
    private const val MAJOR_MASK = 0x1F00

    fun interpret(deviceClass: Int): DeviceClassInterpretation? = when (deviceClass and MAJOR_MASK) {
        0x0100 -> DeviceClassInterpretation("Ordinateur", "classe majeure ordinateur")
        0x0200 -> DeviceClassInterpretation("Téléphone", "classe majeure téléphone")
        0x0300 -> DeviceClassInterpretation("Réseau", "classe majeure point d'accès réseau")
        0x0400 -> DeviceClassInterpretation("Audio/Vidéo", "classe majeure audio ou vidéo")
        0x0500 -> DeviceClassInterpretation("Périphérique", "classe majeure clavier, souris ou contrôleur")
        0x0600 -> DeviceClassInterpretation("Imagerie", "classe majeure impression ou imagerie")
        0x0700 -> DeviceClassInterpretation("Portable", "classe majeure appareil porté")
        0x0800 -> DeviceClassInterpretation("Jouet", "classe majeure jouet")
        0x0900 -> DeviceClassInterpretation("Santé", "classe majeure dispositif de santé")
        else -> null
    }
}
