package com.bluescope.ai.ai

/** Replaceable view of the embedded Bluetooth SIG Assigned Numbers snapshot. */
interface AssignedNumbersCatalog {
    val sourceLabel: String
    val snapshotVersion: String

    fun company(companyId: Int): CompanyDefinition?
    fun service(uuid: String): ServiceDefinition?
    fun characteristic(uuid: String): CharacteristicDefinition?
    fun appearance(appearance: Int): AppearanceDefinition?
}

data class CompanyDefinition(val id: Int, val name: String)

data class ServiceDefinition(
    val uuid: String,
    val name: String,
    val category: String,
    val possibleUse: String,
)

data class CharacteristicDefinition(
    val uuid: String,
    val name: String,
    val meaning: String,
)

data class AppearanceDefinition(
    val value: Int,
    val category: String,
    val description: String,
)

/** Curated offline baseline. Unknown numbers deliberately remain unknown. */
class EmbeddedAssignedNumbersCatalog : AssignedNumbersCatalog {
    override val sourceLabel: String = "Bluetooth SIG Assigned Numbers"
    override val snapshotVersion: String = "2026-10"

    private val companies = listOf(
        CompanyDefinition(0x0006, "Microsoft"),
        CompanyDefinition(0x000D, "Texas Instruments"),
        CompanyDefinition(0x000F, "Broadcom"),
        CompanyDefinition(0x004C, "Apple"),
        CompanyDefinition(0x0059, "Nordic Semiconductor"),
        CompanyDefinition(0x0075, "Samsung Electronics"),
        CompanyDefinition(0x00E0, "Google"),
    ).associateBy(CompanyDefinition::id)

    private val services = listOf(
        ServiceDefinition("1800", "Generic Access", "Système Bluetooth", "identité et apparence GAP"),
        ServiceDefinition("1801", "Generic Attribute", "Système Bluetooth", "évolution de la base GATT"),
        ServiceDefinition("180A", "Device Information", "Informations", "informations déclarées par le périphérique"),
        ServiceDefinition("180D", "Heart Rate", "Santé", "mesure de fréquence cardiaque"),
        ServiceDefinition("180F", "Battery", "Énergie", "niveau de batterie"),
        ServiceDefinition("1812", "Human Interface Device", "Interface", "clavier, souris, télécommande ou contrôleur"),
        ServiceDefinition("1816", "Cycling Speed and Cadence", "Sport", "vitesse et cadence cyclistes"),
        ServiceDefinition("181A", "Environmental Sensing", "Capteur", "mesures environnementales"),
        ServiceDefinition("181C", "User Data", "Santé", "données de profil utilisateur"),
        ServiceDefinition("1826", "Fitness Machine", "Sport", "équipement de fitness"),
    ).associateBy { normalizeBluetoothUuid(it.uuid) }

    private val characteristics = listOf(
        CharacteristicDefinition("2A00", "Device Name", "nom GAP déclaré"),
        CharacteristicDefinition("2A01", "Appearance", "catégorie GAP déclarée"),
        CharacteristicDefinition("2A19", "Battery Level", "niveau de batterie en pourcentage"),
        CharacteristicDefinition("2A29", "Manufacturer Name String", "nom du fabricant déclaré"),
        CharacteristicDefinition("2A37", "Heart Rate Measurement", "mesure de fréquence cardiaque"),
        CharacteristicDefinition("2A38", "Body Sensor Location", "position du capteur corporel"),
        CharacteristicDefinition("2A4A", "HID Information", "version et propriétés HID"),
        CharacteristicDefinition("2A4B", "Report Map", "descripteur des rapports HID"),
        CharacteristicDefinition("2A4D", "Report", "rapport d'entrée ou de sortie HID"),
    ).associateBy { normalizeBluetoothUuid(it.uuid) }

    private val appearances = listOf(
        AppearanceDefinition(0x0040, "Téléphone", "téléphone générique"),
        AppearanceDefinition(0x0080, "Ordinateur", "ordinateur générique"),
        AppearanceDefinition(0x00C0, "Montre", "montre générique"),
        AppearanceDefinition(0x00C1, "Montre", "montre de sport"),
        AppearanceDefinition(0x0140, "Écran", "écran générique"),
        AppearanceDefinition(0x0180, "Télécommande", "télécommande générique"),
        AppearanceDefinition(0x0300, "Thermomètre", "thermomètre générique"),
        AppearanceDefinition(0x0340, "Santé", "capteur de fréquence cardiaque générique"),
        AppearanceDefinition(0x03C0, "Interface", "périphérique d'interface générique"),
        AppearanceDefinition(0x03C1, "Interface", "clavier"),
        AppearanceDefinition(0x03C2, "Interface", "souris"),
        AppearanceDefinition(0x03C4, "Interface", "manette de jeu"),
        AppearanceDefinition(0x0540, "Capteur", "capteur générique"),
        AppearanceDefinition(0x0543, "Capteur", "capteur de température"),
        AppearanceDefinition(0x0544, "Capteur", "capteur d'humidité"),
    ).associateBy(AppearanceDefinition::value)

    override fun company(companyId: Int): CompanyDefinition? = companies[companyId]
    override fun service(uuid: String): ServiceDefinition? = services[normalizeBluetoothUuid(uuid)]
    override fun characteristic(uuid: String): CharacteristicDefinition? = characteristics[normalizeBluetoothUuid(uuid)]
    override fun appearance(appearance: Int): AppearanceDefinition? = appearances[appearance]
}

/** Converts 16-bit, 32-bit and standard-base 128-bit UUIDs to one canonical form. */
fun normalizeBluetoothUuid(value: String): String {
    val compact = value.trim().removePrefix("0x").replace("-", "").uppercase()
    return when (compact.length) {
        4 -> "0000${compact}00001000800000805F9B34FB"
        8 -> "${compact}00001000800000805F9B34FB"
        else -> compact
    }
}
