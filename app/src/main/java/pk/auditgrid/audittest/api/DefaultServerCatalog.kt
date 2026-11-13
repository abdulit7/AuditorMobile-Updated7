package pk.auditgrid.audittest.api

import pk.auditgrid.audittest.model.*

/**
 * All dummy catalog data has been deleted.
 * Users, roles, cities, territories, locations, sub-locations, shops, distributors,
 * product groups, products, and audit forms are downloaded from the server and
 * stored in the local mobile SQLite database (`AuditGridMobileDatabase`), or imported
 * manually via a Form Package (.json).
 */
object DefaultServerCatalog {
    val cities: List<City> = emptyList()
    val territories: List<Territory> = emptyList()
    val locations: List<LocationItem> = emptyList()
    val subLocations: List<SubLocation> = emptyList()
    val shops: List<Shop> = emptyList()
    val groups: List<ProductGroup> = emptyList()
    val products: List<Product> = emptyList()
    val distributors: List<Distributor> = emptyList()
    val forms: List<AuditFormDefinition> = emptyList()
    val formConfigs: FormConfigMap = emptyMap()
}
