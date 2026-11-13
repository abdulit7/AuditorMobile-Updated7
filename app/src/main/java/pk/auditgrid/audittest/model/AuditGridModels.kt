package pk.auditgrid.audittest.model

data class City(
    val id: String,
    val code: String,
    val name: String,
    val province: String,
    val region: String,
    val lat: Double,
    val lng: Double,
    val status: String,
    val createdAt: String = ""
)

data class Territory(
    val id: String,
    val code: String,
    val name: String,
    val cityId: String,
    val cityName: String = "",
    val distributorId: String = "",
    val distributorName: String,
    val distributorCode: String,
    val obCode: String = "",
    val obName: String = "",
    val assignedAuditorIds: List<String>,
    val lat: Double,
    val lng: Double,
    val status: String,
    val updatedAt: String = "",
    val version: Int = 0
)

data class LocationItem(
    val id: String,
    val code: String,
    val name: String,
    val territoryId: String,
    val territoryName: String = "",
    val cityId: String,
    val cityName: String = "",
    val areaClassification: String = "Urban Commercial",
    val distributorId: String = "",
    val distributorCode: String = "",
    val distributorName: String = "",
    val obCode: String = "",
    val obName: String = "",
    val subLocationsCount: Int = 0,
    val shopsCount: Int = 0,
    val gpsStatus: String = "GPS Verified",
    val lat: Double,
    val lng: Double,
    val status: String
)

data class SubLocation(
    val id: String,
    val code: String,
    val name: String,
    val locationId: String,
    val locationName: String = "",
    val territoryId: String,
    val territoryName: String = "",
    val cityId: String,
    val cityName: String = "",
    val marketType: String = "Main Bazaar",
    val postalCode: String = "",
    val distributorCode: String = "",
    val distributorName: String = "",
    val obCode: String = "",
    val obName: String = "",
    val shopsCount: Int = 0,
    val lat: Double,
    val lng: Double,
    val status: String
)

data class Shop(
    val id: String,
    val code: String,
    val name: String,
    val ownerName: String,
    val phone: String,
    val subLocationId: String,
    val subLocationName: String = "",
    val locationId: String,
    val locationName: String = "",
    val territoryId: String,
    val territoryName: String = "",
    val cityId: String,
    val cityName: String = "",
    val shopCategory: String = "General Store",
    val shopType: String = "General Store",
    val distributorId: String = "",
    val distributorCode: String = "",
    val distributorName: String = "",
    val obCode: String = "",
    val obName: String = "",
    val lat: Double,
    val lng: Double,
    val allowedRadiusMeters: Int = 150,
    val gpsStatus: String = "GPS Verified",
    val gpsAccuracyMeters: Int? = null,
    val status: String = "Active",
    val lastVerifiedAt: String = "",
    val lastAudit: String = "Never",
    val addressLine: String = "",
    val address: String = ""
)

data class ProductGroup(
    val id: String,
    val code: String,
    val name: String,
    val divisionTitle: String = "",
    val description: String = "",
    val storageType: String = "",
    val defaultStackingMaxCtn: Int = 10,
    val badgeStyle: String = "groupA",
    val status: String = "Active"
)

data class Product(
    val id: String,
    val sku: String,
    val name: String,
    val groupId: String,
    val groupName: String,
    val unitPerBox: String,
    val unitsCountPerBox: Int,
    val boxesPerCtn: Int,
    val pricePerBoxRs: Double,
    val stackingStandard: String = "",
    val shelfLifeDays: Int = 365,
    val status: String = "Active"
)

data class UserRole(
    val id: String,
    val code: String,
    val name: String,
    val surface: String,
    val description: String = "",
    val isSystemRole: Boolean = false,
    val permissions: Map<String, Boolean> = emptyMap()
)

data class AppUser(
    val id: String,
    val code: String,
    val userType: String = "Mobile Auditor",
    val canLoginFromMobile: Boolean = true,
    val canLoginFromWeb: Boolean = false,
    val username: String,
    val fullName: String,
    val email: String = "",
    val phone: String = "",
    val roleId: String = "",
    val surfaceAccess: String = "Mobile PWA",
    val assignedCityIds: List<String> = emptyList(),
    val assignedTerritoryIds: List<String> = emptyList(),
    val assignedGroupIds: List<String> = emptyList(),
    val status: String = "Active",
    val lastActiveAt: String = ""
)

data class Distributor(
    val id: String,
    val code: String,
    val setupName: String,
    val distributorName: String,
    val fatherName: String,
    val cnic: String,
    val phone: String,
    val cityId: String,
    val territoryId: String,
    val completeAddress: String,
    val groupIds: List<String>,
    val productStatuses: Map<String, String>,
    val lat: Double,
    val lng: Double,
    val gpsStatus: String = "GPS Verified",
    val status: String,
    val createdAt: String = ""
)

data class AuditFormDefinition(
    val id: String,
    val code: String,
    val title: String,
    val shortTitle: String,
    val category: String,
    val kind: String = "builtin",
    val target: String = "distributor",
    val description: String = "",
    val fieldsCount: Int = 0,
    val requiresBackgroundGPS: Boolean = true,
    val allowMultipleEntriesPerProduct: Boolean = true,
    val allowPhotoCapture: Boolean = true,
    val status: String = "Active",
    val updatedAt: String = "",
    val version: Int = 0
)

data class AuditCapturedImage(
    val id: String,
    val url: String,
    val caption: String,
    val capturedAt: String
)

data class DistributorAuditLineItem(
    val id: String,
    val productId: String,
    val sku: String,
    val productName: String,
    val boxesPerCtn: Int,
    val unitPerBox: String,
    val unitsCountPerBox: Int,
    val pricePerBoxRs: Double,
    val qtyInCtn: Int,
    val qtyInBox: Int,
    val totalCtns: Double,
    val dispatchDate: String,
    val stacking: String,
    val remarks: String,
    val stockValueRs: Double
)

data class DistributorProductAuditReport(
    val id: String,
    val reportCode: String,
    val formId: String,
    val distributorId: String,
    val distributorCode: String,
    val distributorSetupName: String,
    val distributorOwnerName: String,
    val groupCode: String,
    val groupName: String,
    val cityId: String,
    val cityName: String,
    val territoryId: String,
    val territoryCode: String,
    val territoryName: String,
    val auditorId: String,
    val auditorCode: String,
    val auditorName: String,
    val auditorUsername: String,
    val reportDateDisplay: String,
    val submittedDate: String,
    val submittedTime: String,
    val submittedAtISO: String,
    val mobileLat: Double,
    val mobileLng: Double,
    val mobileAccuracyMeters: Int,
    val distributorLat: Double,
    val distributorLng: Double,
    val distanceMeters: Int,
    val allowedRadiusMeters: Int,
    val locationMatched: Boolean,
    val items: List<DistributorAuditLineItem>,
    val totalCtns: Double,
    val totalStockValueRs: Double,
    val capturedImages: List<AuditCapturedImage>,
    val overallComments: String? = null,
    val reviewStatus: String? = null,
    val reviewNote: String? = null,
    val planId: String? = null,
    val planItemId: String? = null
)

data class RetailShopAuditItem(
    val shopId: String,
    val shopCode: String,
    val shopName: String,
    val ownerName: String,
    val phone: String,
    val addressLine: String,
    val shopCategory: String,
    val shopStatus: String = "Open",
    val productACount: Int,
    val productBCount: Int,
    val displayRating: String,
    val packedProductQty: Int,
    val regularity: String,
    val salesFloTagged: String = "",
    val doubleTag: String = "",
    val obVisit: String = "",
    val computerizedBill: String = "",
    val remarks: String,
    val capturedImages: List<AuditCapturedImage>,
    val auditStatus: String,
    val auditedAt: String,
    val mobileLat: Double,
    val mobileLng: Double,
    val mobileAccuracyMeters: Int,
    val shopLat: Double,
    val shopLng: Double,
    val distanceMeters: Int,
    val allowedRadiusMeters: Int,
    val locationMatched: Boolean
)

data class RetailMarketShopAuditReport(
    val id: String,
    val reportCode: String,
    val formId: String,
    val cityId: String,
    val cityName: String,
    val territoryId: String,
    val territoryCode: String,
    val territoryName: String,
    val locationId: String,
    val locationCode: String,
    val locationName: String,
    val subLocationId: String,
    val subLocationCode: String,
    val subLocationName: String,
    val auditorId: String,
    val auditorCode: String,
    val auditorName: String,
    val auditorUsername: String,
    val reportDateDisplay: String,
    val submittedDate: String,
    val submittedTime: String,
    val submittedAtISO: String,
    val totalShopsInSubLocation: Int,
    val auditedShopsCount: Int,
    val totalProductACount: Int,
    val totalProductBCount: Int,
    val totalPackedProductQty: Int,
    val matchedLocationShopsCount: Int,
    val shopAudits: List<RetailShopAuditItem>,
    val reviewStatus: String? = null,
    val reviewNote: String? = null,
    val planId: String? = null,
    val planItemId: String? = null
)

data class CustomAuditSubmission(
    val id: String,
    val reportCode: String,
    val formId: String,
    val formCode: String = "",
    val formTitle: String,
    val targetType: String,
    val targetId: String,
    val targetCode: String = "",
    val targetName: String,
    val cityName: String = "",
    val territoryCode: String = "",
    val territoryName: String = "",
    val auditorId: String,
    val auditorName: String,
    val submittedDate: String = "",
    val submittedTime: String = "",
    val submittedAtISO: String,
    val mobileLat: Double = 0.0,
    val mobileLng: Double = 0.0,
    val mobileAccuracyMeters: Int = 0,
    val distanceMeters: Int = 0,
    val allowedRadiusMeters: Int = 150,
    val locationMatched: Boolean,
    val answers: Map<String, String> = emptyMap(),
    val capturedImages: List<AuditCapturedImage> = emptyList(),
    val reviewStatus: String = "Submitted",
    val reviewNote: String? = null,
    val planId: String? = null,
    val planItemId: String? = null
)

data class PendingOutboxItem(
    val id: String,
    val kind: String, // "distributor" | "retail" | "custom"
    val reportCode: String,
    val formId: String,
    val formTitle: String,
    val targetName: String,
    val territoryCode: String,
    val cityName: String,
    val createdAtDisplay: String,
    val createdAtISO: String,
    val locationMatched: Boolean,
    val distributorReport: DistributorProductAuditReport? = null,
    val retailReport: RetailMarketShopAuditReport? = null,
    val customSubmission: CustomAuditSubmission? = null,
    val lastError: String? = null
)

data class PlanItem(
    val id: String,
    val formId: String,
    val formTitle: String,
    val targetType: String,
    val targetId: String,
    val targetLabel: String,
    val cityId: String = "",
    val territoryId: String = "",
    val shopIds: List<String> = emptyList(),
    val status: String = "Pending",
    val doneAt: String? = null,
    val reportKind: String? = null,
    val reportId: String? = null,
    val reportCode: String? = null,
    val skipReason: String? = null
)

data class AuditPlan(
    val id: String,
    val auditorId: String,
    val auditorName: String? = null,
    val planDate: String,
    val title: String,
    val notes: String = "",
    val status: String,
    val startedAt: String? = null,
    val items: List<PlanItem>
)

data class FormField(
    val key: String,
    val label: String,
    val helpText: String? = null,
    val placeholder: String? = null,
    val type: String, // 'number' | 'date' | 'select' | 'text' | 'textarea' | 'photo' | 'yesno'
    val required: Boolean = false,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val options: List<String> = emptyList(),
    val optionsLocked: Boolean = false,
    val min: Double? = null,
    val max: Double? = null
)

data class FormSettings(
    val allowPhotoCapture: Boolean = true,
    val maxPhotos: Int = 6,
    val requirePhoto: Boolean = false,
    val allowMultipleEntriesPerProduct: Boolean = true,
    val requireGps: Boolean = true,
    val allowedRadiusMeters: Int = 150,
    val maxAccuracyMeters: Int = 100
)

data class FormConfigPayload(
    val formId: String,
    val version: Int = 1,
    val settings: FormSettings = FormSettings(),
    val fields: List<FormField> = emptyList()
)

typealias FormConfigMap = Map<String, FormConfigPayload>

const val DISTRIBUTOR_FORM_ID = "form-distributor-product-audit"
const val RETAIL_FORM_ID = "form-retail-market-shop-audit"

class FormHelper(private val cfg: FormConfigPayload?) {
    val version: Int = cfg?.version ?: 0
    val settings: FormSettings = cfg?.settings ?: FormSettings()

    private fun fieldOf(key: String): FormField? = cfg?.fields?.find { it.key == key }

    fun label(key: String, fallback: String): String = fieldOf(key)?.label ?: fallback
    fun visible(key: String): Boolean = fieldOf(key)?.visible ?: true
    fun required(key: String, fallback: Boolean = false): Boolean = fieldOf(key)?.required ?: fallback
    fun options(key: String, fallback: List<String>): List<String> =
        fieldOf(key)?.options?.takeIf { it.isNotEmpty() } ?: fallback
    fun placeholder(key: String, fallback: String = ""): String = fieldOf(key)?.placeholder ?: fallback
    fun help(key: String): String? = fieldOf(key)?.helpText
}

fun makeFormHelper(cfg: FormConfigPayload?): FormHelper = FormHelper(cfg)

data class Session(
    val user: AppUser,
    val role: UserRole?,
    val surface: String,
    val isGlobal: Boolean
)

data class BootstrapPayload(
    val cities: List<City>,
    val territories: List<Territory>,
    val locations: List<LocationItem>,
    val subLocations: List<SubLocation>,
    val shops: List<Shop>,
    val distributors: List<Distributor>,
    val groups: List<ProductGroup>,
    val products: List<Product>,
    val users: List<AppUser>,
    val roles: List<UserRole>,
    val forms: List<AuditFormDefinition>,
    val formConfigs: FormConfigMap,
    val mapsApiKey: String,
    val formsBundleHash: String = ""
)

data class FormBundlePayload(
    val packageVersion: String = "1.0",
    val syncedAt: String = "",
    val bundleHash: String = "",
    val forms: List<AuditFormDefinition> = emptyList(),
    val formConfigs: FormConfigMap = emptyMap()
)

data class PlanLink(
    val planId: String,
    val itemId: String
)

data class AuditPreset(
    val distributorId: String? = null,
    val subLocationId: String? = null,
    val targetId: String? = null,
    val shopIds: List<String> = emptyList()
)

fun AppUser.linkedTerritoryIds(
    allTerritories: List<Territory>,
    isGlobal: Boolean = false
): Set<String> {
    val direct = LinkedHashSet<String>()
    direct.addAll(assignedTerritoryIds.filter { it.isNotBlank() })
    for (t in allTerritories) {
        if (t.assignedAuditorIds.contains(id)) {
            direct.add(t.id)
        }
    }
    if (direct.isNotEmpty()) return direct
    if (assignedCityIds.isNotEmpty()) {
        val citySet = assignedCityIds.toSet()
        val fromCities = allTerritories.filter { citySet.contains(it.cityId) }.map { it.id }.toSet()
        if (fromCities.isNotEmpty()) return fromCities
    }
    if (isGlobal) {
        return allTerritories.map { it.id }.toSet()
    }
    return direct
}

fun AppUser.linkedCityIds(
    allCities: List<City>,
    allTerritories: List<Territory>,
    isGlobal: Boolean = false
): Set<String> {
    val terIds = linkedTerritoryIds(allTerritories, isGlobal)
    val combined = LinkedHashSet<String>()
    combined.addAll(assignedCityIds.filter { it.isNotBlank() })
    for (t in allTerritories) {
        if (terIds.contains(t.id) && t.cityId.isNotBlank()) {
            combined.add(t.cityId)
        }
    }
    if (combined.isNotEmpty()) return combined
    if (isGlobal) {
        return allCities.map { it.id }.toSet()
    }
    return combined
}

fun AppUser.withNormalizedLinks(
    allCities: List<City>,
    allTerritories: List<Territory>,
    isGlobal: Boolean = false
): AppUser {
    val resolvedTerritories = linkedTerritoryIds(allTerritories, isGlobal).toList()
    val resolvedCities = linkedCityIds(allCities, allTerritories, isGlobal).toList()
    return copy(
        assignedTerritoryIds = resolvedTerritories,
        assignedCityIds = resolvedCities
    )
}

fun Distributor.isLinkedToScope(
    assignedTerritoryIds: Set<String>,
    assignedTerritories: List<Territory>
): Boolean {
    if (status != "Active") return false
    if (assignedTerritoryIds.contains(territoryId)) return true
    return assignedTerritories.any { t ->
        (t.distributorId.isNotBlank() && t.distributorId == id) ||
            (t.distributorCode.isNotBlank() && code.isNotBlank() && t.distributorCode.equals(code, ignoreCase = true)) ||
            (t.distributorName.isNotBlank() && setupName.isNotBlank() && t.distributorName.equals(setupName, ignoreCase = true))
    }
}

data class AuditorDuty(
    val id: String,
    val code: String,
    val auditorId: String,
    val cityId: String,
    val cityIds: List<String> = emptyList(),
    val cityName: String = "",
    val cityNames: List<String> = emptyList(),
    val territoryId: String = "",
    val territoryIds: List<String> = emptyList(),
    val territoryName: String = "",
    val territoryNames: List<String> = emptyList(),
    val locationId: String = "",
    val distributorsCount: Int = 0,
    val distributorNames: List<String> = emptyList(),
    val shopsCount: Int = 0,
    val title: String = "",
    val dutyDate: String = "",
    val notes: String = "",
    val status: String = "Assigned", // 'Assigned' | 'In Progress' | 'Completed' | 'Cancelled'
    val assignedByName: String = "",
    val notified: Boolean = true,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    val scheduledDate: String get() = dutyDate.ifBlank { createdAt.take(10) }
}

data class AuditorNotification(
    val id: String,
    val userId: String,
    val title: String,
    val message: String,
    val type: String = "duty_assigned",
    val entityType: String = "duty",
    val entityId: String = "",
    val isRead: Boolean = false,
    val createdAt: String = ""
) {
    val dutyId: String? get() = if (entityType.equals("duty", ignoreCase = true) || entityId.startsWith("dty-", ignoreCase = true)) entityId else null
}

data class DutySyncResult(
    val auditorId: String,
    val user: AppUser? = null,
    val assignedCityIds: List<String>,
    val assignedTerritoryIds: List<String>,
    val assignedGroupIds: List<String> = emptyList(),
    val duties: List<AuditorDuty>,
    val notifications: List<AuditorNotification>,
    val unreadCount: Int = 0,
    val bootstrap: BootstrapPayload? = null,
    val syncedAt: String = ""
)


