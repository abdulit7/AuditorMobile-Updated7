package pk.auditgrid.audittest.api

import org.json.JSONArray
import org.json.JSONObject
import pk.auditgrid.audittest.model.*

object AuditGridJsonParsers {

    fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            val s = optString(i, "")
            if (s.isNotEmpty()) list.add(s)
        }
        return list
    }

    fun parseUser(o: JSONObject): AppUser = AppUser(
        id = o.optString("id"),
        code = o.optString("code"),
        userType = o.optString("userType", "mobile"),
        canLoginFromMobile = o.optBoolean("canLoginFromMobile", true),
        canLoginFromWeb = o.optBoolean("canLoginFromWeb", false),
        username = o.optString("username"),
        fullName = o.optString("fullName"),
        email = o.optString("email"),
        phone = o.optString("phone"),
        roleId = o.optString("roleId"),
        surfaceAccess = o.optString("surfaceAccess", "Mobile PWA"),
        assignedCityIds = o.optJSONArray("assignedCityIds").toStringList(),
        assignedTerritoryIds = o.optJSONArray("assignedTerritoryIds").toStringList(),
        assignedGroupIds = o.optJSONArray("assignedGroupIds").toStringList(),
        status = o.optString("status", "Active"),
        lastActiveAt = o.optString("lastActiveAt")
    )

    fun parseRole(o: JSONObject): UserRole {
        val permsObj = o.optJSONObject("permissions")
        val perms = mutableMapOf<String, Boolean>()
        if (permsObj != null) {
            val keys = permsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                perms[k] = permsObj.optBoolean(k, true)
            }
        }
        return UserRole(
            id = o.optString("id"),
            code = o.optString("code"),
            name = o.optString("name"),
            surface = o.optString("surface", "Mobile PWA"),
            description = o.optString("description"),
            isSystemRole = o.optBoolean("isSystemRole", false),
            permissions = perms
        )
    }

    fun parseCity(o: JSONObject): City = City(
        id = o.optString("id"),
        code = o.optString("code"),
        name = o.optString("name"),
        province = o.optString("province"),
        region = o.optString("region"),
        lat = o.optDouble("lat", 0.0),
        lng = o.optDouble("lng", 0.0),
        status = o.optString("status", "Active"),
        createdAt = o.optString("createdAt")
    )

    fun parseTerritory(o: JSONObject): Territory = Territory(
        id = o.optString("id"),
        code = o.optString("code"),
        name = o.optString("name"),
        cityId = o.optString("cityId"),
        cityName = o.optString("cityName"),
        distributorId = o.optString("distributorId"),
        distributorName = o.optString("distributorName"),
        distributorCode = o.optString("distributorCode"),
        obCode = o.optString("obCode"),
        obName = o.optString("obName"),
        assignedAuditorIds = o.optJSONArray("assignedAuditorIds").toStringList(),
        lat = o.optDouble("lat", 0.0),
        lng = o.optDouble("lng", 0.0),
        status = o.optString("status", "Active"),
        updatedAt = o.optString("updatedAt"),
        version = o.optInt("version", 0)
    )

    fun parseLocation(o: JSONObject): LocationItem = LocationItem(
        id = o.optString("id"),
        code = o.optString("code"),
        name = o.optString("name"),
        territoryId = o.optString("territoryId"),
        territoryName = o.optString("territoryName"),
        cityId = o.optString("cityId"),
        cityName = o.optString("cityName"),
        areaClassification = o.optString("areaClassification", "Urban Commercial"),
        distributorId = o.optString("distributorId"),
        distributorCode = o.optString("distributorCode"),
        distributorName = o.optString("distributorName"),
        obCode = o.optString("obCode"),
        obName = o.optString("obName"),
        subLocationsCount = o.optInt("subLocationsCount", 0),
        shopsCount = o.optInt("shopsCount", 0),
        gpsStatus = o.optString("gpsStatus", "GPS Verified"),
        lat = o.optDouble("lat", 0.0),
        lng = o.optDouble("lng", 0.0),
        status = o.optString("status", "Active")
    )

    fun parseSubLocation(o: JSONObject): SubLocation = SubLocation(
        id = o.optString("id"),
        code = o.optString("code"),
        name = o.optString("name"),
        locationId = o.optString("locationId"),
        locationName = o.optString("locationName"),
        territoryId = o.optString("territoryId"),
        territoryName = o.optString("territoryName"),
        cityId = o.optString("cityId"),
        cityName = o.optString("cityName"),
        marketType = o.optString("marketType", "Main Bazaar"),
        postalCode = o.optString("postalCode"),
        distributorCode = o.optString("distributorCode"),
        distributorName = o.optString("distributorName"),
        obCode = o.optString("obCode"),
        obName = o.optString("obName"),
        shopsCount = o.optInt("shopsCount", 0),
        lat = o.optDouble("lat", 0.0),
        lng = o.optDouble("lng", 0.0),
        status = o.optString("status", "Active")
    )

    fun parseShop(o: JSONObject): Shop = Shop(
        id = o.optString("id"),
        code = o.optString("code"),
        name = o.optString("name"),
        ownerName = o.optString("ownerName"),
        phone = o.optString("phone"),
        subLocationId = o.optString("subLocationId"),
        subLocationName = o.optString("subLocationName"),
        locationId = o.optString("locationId"),
        locationName = o.optString("locationName"),
        territoryId = o.optString("territoryId"),
        territoryName = o.optString("territoryName"),
        cityId = o.optString("cityId"),
        cityName = o.optString("cityName"),
        shopCategory = o.optString("shopCategory", o.optString("shopType", "General Store")),
        shopType = o.optString("shopType", o.optString("shopCategory", "General Store")),
        distributorId = o.optString("distributorId"),
        distributorCode = o.optString("distributorCode"),
        distributorName = o.optString("distributorName"),
        obCode = o.optString("obCode"),
        obName = o.optString("obName"),
        lat = o.optDouble("lat", 0.0),
        lng = o.optDouble("lng", 0.0),
        allowedRadiusMeters = o.optInt("allowedRadiusMeters", 150),
        gpsStatus = o.optString("gpsStatus", "GPS Verified"),
        gpsAccuracyMeters = if (o.has("gpsAccuracyMeters") && !o.isNull("gpsAccuracyMeters")) o.optInt("gpsAccuracyMeters") else null,
        status = o.optString("status", "Active"),
        lastVerifiedAt = o.optString("lastVerifiedAt"),
        lastAudit = o.optString("lastAudit", "Never"),
        addressLine = o.optString("addressLine", o.optString("address")),
        address = o.optString("address", o.optString("addressLine"))
    )

    fun parseDistributor(o: JSONObject): Distributor {
        val psObj = o.optJSONObject("productStatuses")
        val ps = mutableMapOf<String, String>()
        if (psObj != null) {
            val keys = psObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                ps[k] = psObj.optString(k, "Active")
            }
        }
        return Distributor(
            id = o.optString("id"),
            code = o.optString("code"),
            setupName = o.optString("setupName"),
            distributorName = o.optString("distributorName"),
            fatherName = o.optString("fatherName"),
            cnic = o.optString("cnic"),
            phone = o.optString("phone"),
            cityId = o.optString("cityId"),
            territoryId = o.optString("territoryId"),
            completeAddress = o.optString("completeAddress"),
            groupIds = o.optJSONArray("groupIds").toStringList(),
            productStatuses = ps,
            lat = o.optDouble("lat", 0.0),
            lng = o.optDouble("lng", 0.0),
            gpsStatus = o.optString("gpsStatus", "GPS Verified"),
            status = o.optString("status", "Active"),
            createdAt = o.optString("createdAt")
        )
    }

    fun parseProductGroup(o: JSONObject): ProductGroup = ProductGroup(
        id = o.optString("id"),
        code = o.optString("code"),
        name = o.optString("name"),
        divisionTitle = o.optString("divisionTitle"),
        description = o.optString("description"),
        storageType = o.optString("storageType", "Ambient Dry"),
        defaultStackingMaxCtn = o.optInt("defaultStackingMaxCtn", 10),
        badgeStyle = o.optString("badgeStyle", "groupA"),
        status = o.optString("status", "Active")
    )

    fun parseProduct(o: JSONObject): Product = Product(
        id = o.optString("id"),
        sku = o.optString("sku"),
        name = o.optString("name"),
        groupId = o.optString("groupId"),
        groupName = o.optString("groupName"),
        unitPerBox = o.optString("unitPerBox"),
        unitsCountPerBox = o.optInt("unitsCountPerBox", 0),
        boxesPerCtn = o.optInt("boxesPerCtn", 1),
        pricePerBoxRs = o.optDouble("pricePerBoxRs", 0.0),
        stackingStandard = o.optString("stackingStandard"),
        shelfLifeDays = o.optInt("shelfLifeDays", 365),
        status = o.optString("status", "Active")
    )

    fun parseFormDefinition(o: JSONObject): AuditFormDefinition = AuditFormDefinition(
        id = o.optString("id"),
        code = o.optString("code"),
        title = o.optString("title"),
        shortTitle = o.optString("shortTitle").ifEmpty { o.optString("title") },
        category = o.optString("category"),
        kind = o.optString("kind", "builtin"),
        target = o.optString("target").ifEmpty {
            if (o.optString("id") == RETAIL_FORM_ID) "subLocation" else "distributor"
        },
        description = o.optString("description"),
        fieldsCount = o.optInt("fieldsCount", o.optJSONArray("fields")?.length() ?: 0),
        requiresBackgroundGPS = o.optBoolean("requiresBackgroundGPS", true),
        allowMultipleEntriesPerProduct = o.optBoolean("allowMultipleEntriesPerProduct", true),
        allowPhotoCapture = o.optBoolean("allowPhotoCapture", true),
        status = o.optString("status", "Active"),
        updatedAt = o.optString("updatedAt"),
        version = o.optInt("version", 0)
    )

    fun parseFormConfig(o: JSONObject): FormConfigPayload {
        val sObj = o.optJSONObject("settings")
        val settings = if (sObj != null) {
            FormSettings(
                allowPhotoCapture = sObj.optBoolean("allowPhotoCapture", true),
                maxPhotos = sObj.optInt("maxPhotos", 6),
                requirePhoto = sObj.optBoolean("requirePhoto", false),
                allowMultipleEntriesPerProduct = sObj.optBoolean("allowMultipleEntriesPerProduct", true),
                requireGps = sObj.optBoolean("requireGps", true),
                allowedRadiusMeters = sObj.optInt("allowedRadiusMeters", 150),
                maxAccuracyMeters = sObj.optInt("maxAccuracyMeters", 100)
            )
        } else {
            FormSettings()
        }
        val fieldsArr = o.optJSONArray("fields")
        val fields = mutableListOf<FormField>()
        if (fieldsArr != null) {
            for (i in 0 until fieldsArr.length()) {
                val f = fieldsArr.optJSONObject(i) ?: continue
                fields.add(
                    FormField(
                        key = f.optString("key"),
                        label = f.optString("label"),
                        helpText = f.optString("helpText").takeIf { it.isNotEmpty() },
                        placeholder = f.optString("placeholder").takeIf { it.isNotEmpty() },
                        type = f.optString("type", "text"),
                        required = f.optBoolean("required", false),
                        visible = f.optBoolean("visible", true),
                        locked = f.optBoolean("locked", false),
                        options = f.optJSONArray("options").toStringList(),
                        optionsLocked = f.optBoolean("optionsLocked", false),
                        min = if (f.has("min") && !f.isNull("min")) f.optDouble("min") else null,
                        max = if (f.has("max") && !f.isNull("max")) f.optDouble("max") else null
                    )
                )
            }
        }
        return FormConfigPayload(
            formId = o.optString("formId"),
            version = o.optInt("version", 1),
            settings = settings,
            fields = fields
        )
    }

    fun parseCapturedImage(o: JSONObject): AuditCapturedImage = AuditCapturedImage(
        id = o.optString("id"),
        url = o.optString("url"),
        caption = o.optString("caption"),
        capturedAt = o.optString("capturedAt")
    )

    fun parseDistributorLineItem(o: JSONObject): DistributorAuditLineItem = DistributorAuditLineItem(
        id = o.optString("id"),
        productId = o.optString("productId"),
        sku = o.optString("sku"),
        productName = o.optString("productName"),
        boxesPerCtn = o.optInt("boxesPerCtn", 1),
        unitPerBox = o.optString("unitPerBox"),
        unitsCountPerBox = o.optInt("unitsCountPerBox", 0),
        pricePerBoxRs = o.optDouble("pricePerBoxRs", 0.0),
        qtyInCtn = o.optInt("qtyInCtn", 0),
        qtyInBox = o.optInt("qtyInBox", 0),
        totalCtns = o.optDouble("totalCtns", 0.0),
        dispatchDate = o.optString("dispatchDate"),
        stacking = o.optString("stacking"),
        remarks = o.optString("remarks"),
        stockValueRs = o.optDouble("stockValueRs", 0.0)
    )

    fun parseDistributorReport(o: JSONObject): DistributorProductAuditReport {
        val itemsArr = o.optJSONArray("items")
        val items = mutableListOf<DistributorAuditLineItem>()
        if (itemsArr != null) {
            for (i in 0 until itemsArr.length()) {
                itemsArr.optJSONObject(i)?.let { items.add(parseDistributorLineItem(it)) }
            }
        }
        val imgsArr = o.optJSONArray("capturedImages")
        val imgs = mutableListOf<AuditCapturedImage>()
        if (imgsArr != null) {
            for (i in 0 until imgsArr.length()) {
                imgsArr.optJSONObject(i)?.let { imgs.add(parseCapturedImage(it)) }
            }
        }
        return DistributorProductAuditReport(
            id = o.optString("id"),
            reportCode = o.optString("reportCode"),
            formId = o.optString("formId", DISTRIBUTOR_FORM_ID),
            distributorId = o.optString("distributorId"),
            distributorCode = o.optString("distributorCode"),
            distributorSetupName = o.optString("distributorSetupName"),
            distributorOwnerName = o.optString("distributorOwnerName"),
            groupCode = o.optString("groupCode"),
            groupName = o.optString("groupName"),
            cityId = o.optString("cityId"),
            cityName = o.optString("cityName"),
            territoryId = o.optString("territoryId"),
            territoryCode = o.optString("territoryCode"),
            territoryName = o.optString("territoryName"),
            auditorId = o.optString("auditorId"),
            auditorCode = o.optString("auditorCode"),
            auditorName = o.optString("auditorName"),
            auditorUsername = o.optString("auditorUsername"),
            reportDateDisplay = o.optString("reportDateDisplay"),
            submittedDate = o.optString("submittedDate"),
            submittedTime = o.optString("submittedTime"),
            submittedAtISO = o.optString("submittedAtISO"),
            mobileLat = o.optDouble("mobileLat", 0.0),
            mobileLng = o.optDouble("mobileLng", 0.0),
            mobileAccuracyMeters = o.optInt("mobileAccuracyMeters", 0),
            distributorLat = o.optDouble("distributorLat", 0.0),
            distributorLng = o.optDouble("distributorLng", 0.0),
            distanceMeters = o.optInt("distanceMeters", 0),
            allowedRadiusMeters = o.optInt("allowedRadiusMeters", 150),
            locationMatched = o.optBoolean("locationMatched", false),
            items = items,
            totalCtns = o.optDouble("totalCtns", 0.0),
            totalStockValueRs = o.optDouble("totalStockValueRs", 0.0),
            capturedImages = imgs,
            overallComments = o.optString("overallComments").takeIf { it.isNotEmpty() },
            reviewStatus = o.optString("reviewStatus").takeIf { it.isNotEmpty() },
            reviewNote = o.optString("reviewNote").takeIf { it.isNotEmpty() },
            planId = o.optString("planId").takeIf { it.isNotEmpty() },
            planItemId = o.optString("planItemId").takeIf { it.isNotEmpty() }
        )
    }

    fun parseRetailShopAuditItem(o: JSONObject): RetailShopAuditItem {
        val imgsArr = o.optJSONArray("capturedImages")
        val imgs = mutableListOf<AuditCapturedImage>()
        if (imgsArr != null) {
            for (i in 0 until imgsArr.length()) {
                imgsArr.optJSONObject(i)?.let { imgs.add(parseCapturedImage(it)) }
            }
        }
        val statusVal = o.optString("shopStatus", "Open").ifBlank { "Open" }
        val regVal = o.optString("regularity", "No").ifBlank { "No" }
        val obVisitVal = o.optString("obVisit").ifBlank {
            when {
                statusVal == "Close" -> "Never"
                regVal == "Yes" -> "Regular"
                else -> "Not Regular"
            }
        }
        return RetailShopAuditItem(
            shopId = o.optString("shopId"),
            shopCode = o.optString("shopCode"),
            shopName = o.optString("shopName"),
            ownerName = o.optString("ownerName"),
            phone = o.optString("phone"),
            addressLine = o.optString("addressLine"),
            shopCategory = o.optString("shopCategory"),
            shopStatus = statusVal,
            productACount = o.optInt("productACount", 0),
            productBCount = o.optInt("productBCount", 0),
            displayRating = o.optString("displayRating", "N/A"),
            packedProductQty = o.optInt("packedProductQty", 0),
            regularity = regVal,
            salesFloTagged = o.optString("salesFloTagged", "No"),
            doubleTag = o.optString("doubleTag", "No"),
            obVisit = obVisitVal,
            computerizedBill = o.optString("computerizedBill", "No"),
            remarks = o.optString("remarks"),
            capturedImages = imgs,
            auditStatus = o.optString("auditStatus", "Done"),
            auditedAt = o.optString("auditedAt"),
            mobileLat = o.optDouble("mobileLat", 0.0),
            mobileLng = o.optDouble("mobileLng", 0.0),
            mobileAccuracyMeters = o.optInt("mobileAccuracyMeters", 0),
            shopLat = o.optDouble("shopLat", 0.0),
            shopLng = o.optDouble("shopLng", 0.0),
            distanceMeters = o.optInt("distanceMeters", 0),
            allowedRadiusMeters = o.optInt("allowedRadiusMeters", 150),
            locationMatched = o.optBoolean("locationMatched", false)
        )
    }

    fun parseRetailReport(o: JSONObject): RetailMarketShopAuditReport {
        val saArr = o.optJSONArray("shopAudits")
        val sa = mutableListOf<RetailShopAuditItem>()
        if (saArr != null) {
            for (i in 0 until saArr.length()) {
                saArr.optJSONObject(i)?.let { sa.add(parseRetailShopAuditItem(it)) }
            }
        }
        return RetailMarketShopAuditReport(
            id = o.optString("id"),
            reportCode = o.optString("reportCode"),
            formId = o.optString("formId", RETAIL_FORM_ID),
            cityId = o.optString("cityId"),
            cityName = o.optString("cityName"),
            territoryId = o.optString("territoryId"),
            territoryCode = o.optString("territoryCode"),
            territoryName = o.optString("territoryName"),
            locationId = o.optString("locationId"),
            locationCode = o.optString("locationCode"),
            locationName = o.optString("locationName"),
            subLocationId = o.optString("subLocationId"),
            subLocationCode = o.optString("subLocationCode"),
            subLocationName = o.optString("subLocationName"),
            auditorId = o.optString("auditorId"),
            auditorCode = o.optString("auditorCode"),
            auditorName = o.optString("auditorName"),
            auditorUsername = o.optString("auditorUsername"),
            reportDateDisplay = o.optString("reportDateDisplay"),
            submittedDate = o.optString("submittedDate"),
            submittedTime = o.optString("submittedTime"),
            submittedAtISO = o.optString("submittedAtISO"),
            totalShopsInSubLocation = o.optInt("totalShopsInSubLocation", 0),
            auditedShopsCount = o.optInt("auditedShopsCount", sa.size),
            totalProductACount = o.optInt("totalProductACount", 0),
            totalProductBCount = o.optInt("totalProductBCount", 0),
            totalPackedProductQty = o.optInt("totalPackedProductQty", 0),
            matchedLocationShopsCount = o.optInt("matchedLocationShopsCount", 0),
            shopAudits = sa,
            reviewStatus = o.optString("reviewStatus").takeIf { it.isNotEmpty() },
            reviewNote = o.optString("reviewNote").takeIf { it.isNotEmpty() },
            planId = o.optString("planId").takeIf { it.isNotEmpty() },
            planItemId = o.optString("planItemId").takeIf { it.isNotEmpty() }
        )
    }

    fun parsePlanItem(o: JSONObject): PlanItem = PlanItem(
        id = o.optString("id"),
        formId = o.optString("formId"),
        formTitle = o.optString("formTitle"),
        targetType = o.optString("targetType", "distributor"),
        targetId = o.optString("targetId"),
        targetLabel = o.optString("targetLabel"),
        cityId = o.optString("cityId"),
        territoryId = o.optString("territoryId"),
        shopIds = o.optJSONArray("shopIds").toStringList(),
        status = o.optString("status", "Pending"),
        doneAt = o.optString("doneAt").takeIf { it.isNotEmpty() },
        reportKind = o.optString("reportKind").takeIf { it.isNotEmpty() },
        reportId = o.optString("reportId").takeIf { it.isNotEmpty() },
        reportCode = o.optString("reportCode").takeIf { it.isNotEmpty() },
        skipReason = o.optString("skipReason").takeIf { it.isNotEmpty() }
    )

    fun parsePlan(o: JSONObject): AuditPlan {
        val itemsArr = o.optJSONArray("items")
        val items = mutableListOf<PlanItem>()
        if (itemsArr != null) {
            for (i in 0 until itemsArr.length()) {
                itemsArr.optJSONObject(i)?.let { items.add(parsePlanItem(it)) }
            }
        }
        return AuditPlan(
            id = o.optString("id"),
            auditorId = o.optString("auditorId"),
            auditorName = o.optString("auditorName").takeIf { it.isNotEmpty() },
            planDate = o.optString("planDate"),
            title = o.optString("title"),
            notes = o.optString("notes"),
            status = o.optString("status", "Planned"),
            startedAt = o.optString("startedAt").takeIf { it.isNotEmpty() },
            items = items
        )
    }

    fun parseBootstrap(o: JSONObject, fallbackSession: Session): BootstrapPayload {
        fun <T> list(key: String, parser: (JSONObject) -> T): List<T> {
            val arr = o.optJSONArray(key) ?: return emptyList()
            val out = mutableListOf<T>()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { out.add(parser(it)) }
            }
            return out
        }
        val fcObj = o.optJSONObject("formConfigs")
        val fcMap = mutableMapOf<String, FormConfigPayload>()
        if (fcObj != null) {
            val keys = fcObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                fcObj.optJSONObject(k)?.let { fcMap[k] = parseFormConfig(it) }
            }
        }
        val citiesList = list("cities", ::parseCity)
        val territoriesList = list("territories", ::parseTerritory)
        val rawUsers = list("users", ::parseUser).ifEmpty { listOf(fallbackSession.user) }
        val rolesList = list("roles", ::parseRole).ifEmpty { listOfNotNull(fallbackSession.role) }

        // Ensure the active session user is always present in usersList and all users have normalized city & territory links
        val hasSessionUser = rawUsers.any {
            it.id == fallbackSession.user.id ||
                (it.username.isNotBlank() && it.username.equals(fallbackSession.user.username, ignoreCase = true))
        }
        val combinedUsers = if (hasSessionUser) rawUsers else (rawUsers + fallbackSession.user)
        val usersList = combinedUsers.map { u ->
            val isUserGlobal = (u.id == fallbackSession.user.id && fallbackSession.isGlobal)
            u.withNormalizedLinks(citiesList, territoriesList, isUserGlobal)
        }

        return BootstrapPayload(
            cities = citiesList,
            territories = territoriesList,
            locations = list("locations", ::parseLocation),
            subLocations = list("subLocations", ::parseSubLocation),
            shops = list("shops", ::parseShop),
            distributors = list("distributors", ::parseDistributor),
            groups = list("groups", ::parseProductGroup),
            products = list("products", ::parseProduct),
            users = usersList,
            roles = rolesList,
            forms = list("forms", ::parseFormDefinition),
            formConfigs = fcMap,
            mapsApiKey = o.optString("mapsApiKey", ""),
            formsBundleHash = o.optString("formsBundleHash", "")
        )
    }

    fun parseCustomSubmission(o: JSONObject): CustomAuditSubmission {
        val tObj = o.optJSONObject("target")
        val ansObj = o.optJSONObject("answers")
        val answersMap = mutableMapOf<String, String>()
        if (ansObj != null) {
            val keys = ansObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = ansObj.opt(k)
                if (v != null && v != JSONObject.NULL) {
                    answersMap[k] = v.toString()
                }
            }
        }
        val imgsArr = o.optJSONArray("capturedImages") ?: o.optJSONArray("photos")
        val imgs = mutableListOf<AuditCapturedImage>()
        if (imgsArr != null) {
            for (i in 0 until imgsArr.length()) {
                val imgObj = imgsArr.optJSONObject(i) ?: continue
                val url = imgObj.optString("url").ifEmpty {
                    val pid = imgObj.optString("photoId")
                    if (pid.isNotEmpty()) "/api/photos/$pid" else ""
                }
                imgs.add(
                    AuditCapturedImage(
                        id = imgObj.optString("id").ifEmpty { imgObj.optString("photoId", "img-$i") },
                        url = url,
                        caption = imgObj.optString("caption"),
                        capturedAt = imgObj.optString("capturedAt")
                    )
                )
            }
        }
        return CustomAuditSubmission(
            id = o.optString("id"),
            reportCode = o.optString("reportCode"),
            formId = o.optString("formId"),
            formCode = o.optString("formCode"),
            formTitle = o.optString("formTitle"),
            targetType = o.optString("targetType", tObj?.optString("type") ?: "distributor"),
            targetId = o.optString("targetId").ifEmpty { tObj?.optString("id") ?: "" },
            targetCode = o.optString("targetCode").ifEmpty { tObj?.optString("code") ?: "" },
            targetName = o.optString("targetName").ifEmpty { tObj?.optString("name") ?: "" },
            cityName = o.optString("cityName").ifEmpty { tObj?.optString("cityName") ?: "" },
            territoryCode = o.optString("territoryCode").ifEmpty { tObj?.optString("territoryCode") ?: "" },
            territoryName = o.optString("territoryName").ifEmpty { tObj?.optString("territoryName") ?: "" },
            auditorId = o.optString("auditorId"),
            auditorName = o.optString("auditorName"),
            submittedDate = o.optString("submittedDate"),
            submittedTime = o.optString("submittedTime"),
            submittedAtISO = o.optString("submittedAtISO"),
            mobileLat = o.optDouble("mobileLat", 0.0),
            mobileLng = o.optDouble("mobileLng", 0.0),
            mobileAccuracyMeters = o.optInt("mobileAccuracyMeters", 0),
            distanceMeters = o.optInt("distanceMeters", 0),
            allowedRadiusMeters = o.optInt("allowedRadiusMeters", 150),
            locationMatched = o.optBoolean("locationMatched", false),
            answers = answersMap,
            capturedImages = imgs,
            reviewStatus = o.optString("reviewStatus", "Submitted").ifEmpty { "Submitted" },
            reviewNote = o.optString("reviewNote").takeIf { it.isNotEmpty() },
            planId = o.optString("planId").takeIf { it.isNotEmpty() },
            planItemId = o.optString("planItemId").takeIf { it.isNotEmpty() }
        )
    }

    fun serializeCapturedImage(img: AuditCapturedImage): JSONObject = JSONObject().apply {
        put("id", img.id)
        put("url", img.url)
        put("caption", img.caption)
        put("capturedAt", img.capturedAt)
    }

    fun serializeDistributorReport(r: DistributorProductAuditReport): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("reportCode", r.reportCode)
        put("formId", r.formId)
        put("distributorId", r.distributorId)
        put("distributorCode", r.distributorCode)
        put("distributorSetupName", r.distributorSetupName)
        put("distributorOwnerName", r.distributorOwnerName)
        put("groupCode", r.groupCode)
        put("groupName", r.groupName)
        put("cityId", r.cityId)
        put("cityName", r.cityName)
        put("territoryId", r.territoryId)
        put("territoryCode", r.territoryCode)
        put("territoryName", r.territoryName)
        put("auditorId", r.auditorId)
        put("auditorCode", r.auditorCode)
        put("auditorName", r.auditorName)
        put("auditorUsername", r.auditorUsername)
        put("reportDateDisplay", r.reportDateDisplay)
        put("submittedDate", r.submittedDate)
        put("submittedTime", r.submittedTime)
        put("submittedAtISO", r.submittedAtISO)
        put("mobileLat", r.mobileLat)
        put("mobileLng", r.mobileLng)
        put("mobileAccuracyMeters", r.mobileAccuracyMeters)
        put("distributorLat", r.distributorLat)
        put("distributorLng", r.distributorLng)
        put("distanceMeters", r.distanceMeters)
        put("allowedRadiusMeters", r.allowedRadiusMeters)
        put("locationMatched", r.locationMatched)
        val itemsArr = JSONArray()
        r.items.forEach { item ->
            itemsArr.put(JSONObject().apply {
                put("id", item.id)
                put("productId", item.productId)
                put("sku", item.sku)
                put("productName", item.productName)
                put("boxesPerCtn", item.boxesPerCtn)
                put("unitPerBox", item.unitPerBox)
                put("unitsCountPerBox", item.unitsCountPerBox)
                put("pricePerBoxRs", item.pricePerBoxRs)
                put("qtyInCtn", item.qtyInCtn)
                put("qtyInBox", item.qtyInBox)
                put("totalCtns", item.totalCtns)
                put("dispatchDate", item.dispatchDate)
                put("stacking", item.stacking)
                put("remarks", item.remarks)
                put("stockValueRs", item.stockValueRs)
            })
        }
        put("items", itemsArr)
        put("totalCtns", r.totalCtns)
        put("totalStockValueRs", r.totalStockValueRs)
        val imgsArr = JSONArray()
        r.capturedImages.forEach { imgsArr.put(serializeCapturedImage(it)) }
        put("capturedImages", imgsArr)
        r.overallComments?.let { put("overallComments", it) }
        r.reviewStatus?.let { put("reviewStatus", it) }
        r.reviewNote?.let { put("reviewNote", it) }
        r.planId?.let { put("planId", it) }
        r.planItemId?.let { put("planItemId", it) }
    }

    fun serializeRetailReport(r: RetailMarketShopAuditReport): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("reportCode", r.reportCode)
        put("formId", r.formId)
        put("cityId", r.cityId)
        put("cityName", r.cityName)
        put("territoryId", r.territoryId)
        put("territoryCode", r.territoryCode)
        put("territoryName", r.territoryName)
        put("locationId", r.locationId)
        put("locationCode", r.locationCode)
        put("locationName", r.locationName)
        put("subLocationId", r.subLocationId)
        put("subLocationCode", r.subLocationCode)
        put("subLocationName", r.subLocationName)
        put("auditorId", r.auditorId)
        put("auditorCode", r.auditorCode)
        put("auditorName", r.auditorName)
        put("auditorUsername", r.auditorUsername)
        put("reportDateDisplay", r.reportDateDisplay)
        put("submittedDate", r.submittedDate)
        put("submittedTime", r.submittedTime)
        put("submittedAtISO", r.submittedAtISO)
        put("totalShopsInSubLocation", r.totalShopsInSubLocation)
        put("auditedShopsCount", r.auditedShopsCount)
        put("totalProductACount", r.totalProductACount)
        put("totalProductBCount", r.totalProductBCount)
        put("totalPackedProductQty", r.totalPackedProductQty)
        put("matchedLocationShopsCount", r.matchedLocationShopsCount)
        val saArr = JSONArray()
        r.shopAudits.forEach { sa ->
            val imgsArr = JSONArray()
            sa.capturedImages.forEach { imgsArr.put(serializeCapturedImage(it)) }
            saArr.put(JSONObject().apply {
                put("shopId", sa.shopId)
                put("shopCode", sa.shopCode)
                put("shopName", sa.shopName)
                put("ownerName", sa.ownerName)
                put("phone", sa.phone)
                put("addressLine", sa.addressLine)
                put("shopCategory", sa.shopCategory)
                put("shopStatus", sa.shopStatus.ifBlank { "Open" })
                put("productACount", sa.productACount)
                put("productBCount", sa.productBCount)
                put("displayRating", sa.displayRating)
                put("packedProductQty", sa.packedProductQty)
                put("regularity", sa.regularity)
                put("salesFloTagged", sa.salesFloTagged.ifBlank { "No" })
                put("doubleTag", sa.doubleTag.ifBlank { "No" })
                put("obVisit", sa.obVisit.ifBlank { if (sa.regularity == "Yes") "Regular" else "Not Regular" })
                put("computerizedBill", sa.computerizedBill.ifBlank { "No" })
                put("remarks", sa.remarks)
                put("capturedImages", imgsArr)
                put("auditStatus", sa.auditStatus)
                put("auditedAt", sa.auditedAt)
                put("mobileLat", sa.mobileLat)
                put("mobileLng", sa.mobileLng)
                put("mobileAccuracyMeters", sa.mobileAccuracyMeters)
                put("shopLat", sa.shopLat)
                put("shopLng", sa.shopLng)
                put("distanceMeters", sa.distanceMeters)
                put("allowedRadiusMeters", sa.allowedRadiusMeters)
                put("locationMatched", sa.locationMatched)
            })
        }
        put("shopAudits", saArr)
        r.reviewStatus?.let { put("reviewStatus", it) }
        r.reviewNote?.let { put("reviewNote", it) }
        r.planId?.let { put("planId", it) }
        r.planItemId?.let { put("planItemId", it) }
    }

    fun serializeCustomSubmission(c: CustomAuditSubmission): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("reportCode", c.reportCode)
        put("formId", c.formId)
        put("formCode", c.formCode)
        put("formTitle", c.formTitle)
        put("targetType", c.targetType)
        put("targetId", c.targetId)
        put("targetCode", c.targetCode)
        put("targetName", c.targetName)
        put("cityName", c.cityName)
        put("territoryCode", c.territoryCode)
        put("territoryName", c.territoryName)
        put("auditorId", c.auditorId)
        put("auditorName", c.auditorName)
        put("submittedDate", c.submittedDate)
        put("submittedTime", c.submittedTime)
        put("submittedAtISO", c.submittedAtISO)
        put("mobileLat", c.mobileLat)
        put("mobileLng", c.mobileLng)
        put("mobileAccuracyMeters", c.mobileAccuracyMeters)
        put("distanceMeters", c.distanceMeters)
        put("allowedRadiusMeters", c.allowedRadiusMeters)
        put("locationMatched", c.locationMatched)
        val ansObj = JSONObject()
        c.answers.forEach { (k, v) -> ansObj.put(k, v) }
        put("answers", ansObj)
        val imgsArr = JSONArray()
        c.capturedImages.forEach { imgsArr.put(serializeCapturedImage(it)) }
        put("capturedImages", imgsArr)
        put("reviewStatus", c.reviewStatus)
        c.reviewNote?.let { put("reviewNote", it) }
        c.planId?.let { put("planId", it) }
        c.planItemId?.let { put("planItemId", it) }
    }

    fun serializePendingOutboxItem(p: PendingOutboxItem): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("kind", p.kind)
        put("reportCode", p.reportCode)
        put("formId", p.formId)
        put("formTitle", p.formTitle)
        put("targetName", p.targetName)
        put("territoryCode", p.territoryCode)
        put("cityName", p.cityName)
        put("createdAtDisplay", p.createdAtDisplay)
        put("createdAtISO", p.createdAtISO)
        put("locationMatched", p.locationMatched)
        p.distributorReport?.let { put("distributorReport", serializeDistributorReport(it)) }
        p.retailReport?.let { put("retailReport", serializeRetailReport(it)) }
        p.customSubmission?.let { put("customSubmission", serializeCustomSubmission(it)) }
        p.lastError?.let { put("lastError", it) }
    }

    fun parsePendingOutboxItem(o: JSONObject): PendingOutboxItem = PendingOutboxItem(
        id = o.optString("id"),
        kind = o.optString("kind", "distributor"),
        reportCode = o.optString("reportCode"),
        formId = o.optString("formId"),
        formTitle = o.optString("formTitle"),
        targetName = o.optString("targetName"),
        territoryCode = o.optString("territoryCode"),
        cityName = o.optString("cityName"),
        createdAtDisplay = o.optString("createdAtDisplay"),
        createdAtISO = o.optString("createdAtISO"),
        locationMatched = o.optBoolean("locationMatched", false),
        distributorReport = o.optJSONObject("distributorReport")?.let { parseDistributorReport(it) },
        retailReport = o.optJSONObject("retailReport")?.let { parseRetailReport(it) },
        customSubmission = o.optJSONObject("customSubmission")?.let { parseCustomSubmission(it) },
        lastError = o.optString("lastError").takeIf { it.isNotEmpty() }
    )

    fun serializeFormDefinition(f: AuditFormDefinition): JSONObject = JSONObject().apply {
        put("id", f.id)
        put("code", f.code)
        put("title", f.title)
        put("shortTitle", f.shortTitle)
        put("category", f.category)
        put("kind", f.kind)
        put("target", f.target)
        put("description", f.description)
        put("requiresBackgroundGPS", f.requiresBackgroundGPS)
        put("allowMultipleEntriesPerProduct", f.allowMultipleEntriesPerProduct)
        put("allowPhotoCapture", f.allowPhotoCapture)
        put("status", f.status)
        put("updatedAt", f.updatedAt)
        put("version", f.version)
    }

    fun serializeFormConfig(c: FormConfigPayload): JSONObject = JSONObject().apply {
        put("formId", c.formId)
        put("version", c.version)
        put("settings", JSONObject().apply {
            put("allowPhotoCapture", c.settings.allowPhotoCapture)
            put("maxPhotos", c.settings.maxPhotos)
            put("requirePhoto", c.settings.requirePhoto)
            put("allowMultipleEntriesPerProduct", c.settings.allowMultipleEntriesPerProduct)
            put("requireGps", c.settings.requireGps)
            put("allowedRadiusMeters", c.settings.allowedRadiusMeters)
            put("maxAccuracyMeters", c.settings.maxAccuracyMeters)
        })
        val fieldsArr = JSONArray()
        c.fields.forEach { fld ->
            fieldsArr.put(JSONObject().apply {
                put("key", fld.key)
                put("label", fld.label)
                fld.helpText?.let { put("helpText", it) }
                fld.placeholder?.let { put("placeholder", it) }
                put("type", fld.type)
                put("required", fld.required)
                put("visible", fld.visible)
                put("locked", fld.locked)
                put("options", JSONArray(fld.options))
                put("optionsLocked", fld.optionsLocked)
                fld.min?.let { put("min", it) }
                fld.max?.let { put("max", it) }
            })
        }
        put("fields", fieldsArr)
    }

    fun parseFormBundle(o: JSONObject): FormBundlePayload {
        val forms = mutableListOf<AuditFormDefinition>()
        val fcMap = mutableMapOf<String, FormConfigPayload>()

        val formsArr = o.optJSONArray("forms")
        if (formsArr != null) {
            for (i in 0 until formsArr.length()) {
                val formObj = formsArr.optJSONObject(i) ?: continue
                val def = parseFormDefinition(formObj)
                if (def.id.isNotBlank()) {
                    forms.add(def)
                    // Support embedded fields/settings or nested config inside each form item
                    val nestedCfg = formObj.optJSONObject("config")
                    if (nestedCfg != null) {
                        fcMap[def.id] = parseFormConfig(nestedCfg).copy(formId = def.id)
                    } else if (formObj.has("fields") || formObj.has("settings")) {
                        fcMap[def.id] = parseFormConfig(formObj).copy(formId = def.id)
                    }
                }
            }
        }

        val fcObj = o.optJSONObject("formConfigs")
        if (fcObj != null) {
            val keys = fcObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                fcObj.optJSONObject(k)?.let { fcMap[k] = parseFormConfig(it).copy(formId = k) }
            }
        }

        // Also support single-form package: { "form": {...}, "config": {...} } or top-level form object { "id": "...", "title": "...", "fields": [...] }
        if (forms.isEmpty()) {
            val singleFormObj = o.optJSONObject("form") ?: if (o.has("id") && o.has("title")) o else null
            if (singleFormObj != null) {
                val def = parseFormDefinition(singleFormObj)
                if (def.id.isNotBlank()) {
                    forms.add(def)
                    val cfgObj = o.optJSONObject("config") ?: singleFormObj.optJSONObject("config")
                        ?: if (o.has("fields") || o.has("settings")) o
                        else if (singleFormObj.has("fields") || singleFormObj.has("settings")) singleFormObj
                        else null
                    if (cfgObj != null) {
                        fcMap[def.id] = parseFormConfig(cfgObj).copy(formId = def.id)
                    }
                }
            }
        }

        return FormBundlePayload(
            packageVersion = o.optString("packageVersion", "1.0"),
            syncedAt = o.optString("syncedAt", ""),
            bundleHash = o.optString("bundleHash", ""),
            forms = forms,
            formConfigs = fcMap
        )
    }

    fun parseFormBundleFromRawJson(rawJson: String): FormBundlePayload {
        val trimmed = rawJson.trim()
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            val wrapper = JSONObject().apply { put("forms", arr) }
            return parseFormBundle(wrapper)
        }
        return parseFormBundle(JSONObject(trimmed))
    }

    fun serializeUser(u: AppUser): JSONObject = JSONObject().apply {
        put("id", u.id)
        put("code", u.code)
        put("userType", u.userType)
        put("canLoginFromMobile", u.canLoginFromMobile)
        put("canLoginFromWeb", u.canLoginFromWeb)
        put("username", u.username)
        put("fullName", u.fullName)
        put("email", u.email)
        put("phone", u.phone)
        put("roleId", u.roleId)
        put("surfaceAccess", u.surfaceAccess)
        put("assignedCityIds", JSONArray(u.assignedCityIds))
        put("assignedTerritoryIds", JSONArray(u.assignedTerritoryIds))
        put("assignedGroupIds", JSONArray(u.assignedGroupIds))
        put("status", u.status)
        put("lastActiveAt", u.lastActiveAt)
    }

    fun serializeRole(r: UserRole): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("code", r.code)
        put("name", r.name)
        put("surface", r.surface)
        put("description", r.description)
        put("isSystemRole", r.isSystemRole)
        val permsObj = JSONObject()
        r.permissions.forEach { (k, v) -> permsObj.put(k, v) }
        put("permissions", permsObj)
    }

    fun serializeSession(s: Session): JSONObject = JSONObject().apply {
        put("user", serializeUser(s.user))
        s.role?.let { put("role", serializeRole(it)) }
        put("surface", s.surface)
        put("isGlobal", s.isGlobal)
    }

    fun parseSession(o: JSONObject): Session = Session(
        user = parseUser(o.optJSONObject("user") ?: JSONObject()),
        role = o.optJSONObject("role")?.let { parseRole(it) },
        surface = o.optString("surface", "mobile"),
        isGlobal = o.optBoolean("isGlobal", false)
    )

    fun serializeCity(c: City): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("code", c.code)
        put("name", c.name)
        put("province", c.province)
        put("region", c.region)
        put("lat", c.lat)
        put("lng", c.lng)
        put("status", c.status)
        put("createdAt", c.createdAt)
    }

    fun serializeTerritory(t: Territory): JSONObject = JSONObject().apply {
        put("id", t.id)
        put("code", t.code)
        put("name", t.name)
        put("cityId", t.cityId)
        put("cityName", t.cityName)
        if (t.distributorId.isNotBlank()) put("distributorId", t.distributorId)
        put("distributorName", t.distributorName)
        put("distributorCode", t.distributorCode)
        if (t.obCode.isNotBlank()) put("obCode", t.obCode)
        if (t.obName.isNotBlank()) put("obName", t.obName)
        put("assignedAuditorIds", JSONArray(t.assignedAuditorIds))
        put("lat", t.lat)
        put("lng", t.lng)
        put("status", t.status)
        put("updatedAt", t.updatedAt)
        put("version", t.version)
    }

    fun serializeLocation(l: LocationItem): JSONObject = JSONObject().apply {
        if (l.id.isNotBlank()) put("id", l.id)
        put("code", l.code)
        put("name", l.name)
        put("territoryId", l.territoryId)
        if (l.territoryName.isNotBlank()) put("territoryName", l.territoryName)
        put("cityId", l.cityId)
        if (l.cityName.isNotBlank()) put("cityName", l.cityName)
        put("areaClassification", l.areaClassification)
        put("subLocationsCount", l.subLocationsCount)
        put("shopsCount", l.shopsCount)
        put("gpsStatus", l.gpsStatus)
        put("lat", l.lat)
        put("lng", l.lng)
        put("status", l.status)
    }

    fun serializeSubLocation(s: SubLocation): JSONObject = JSONObject().apply {
        if (s.id.isNotBlank()) put("id", s.id)
        put("code", s.code)
        put("name", s.name)
        put("locationId", s.locationId)
        if (s.locationName.isNotBlank()) put("locationName", s.locationName)
        put("territoryId", s.territoryId)
        if (s.territoryName.isNotBlank()) put("territoryName", s.territoryName)
        put("cityId", s.cityId)
        if (s.cityName.isNotBlank()) put("cityName", s.cityName)
        put("marketType", s.marketType)
        put("postalCode", s.postalCode)
        put("shopsCount", s.shopsCount)
        put("lat", s.lat)
        put("lng", s.lng)
        put("status", s.status)
    }

    fun serializeShop(sh: Shop): JSONObject = JSONObject().apply {
        if (sh.id.isNotBlank()) put("id", sh.id)
        put("code", sh.code)
        put("name", sh.name)
        put("ownerName", sh.ownerName)
        put("phone", sh.phone)
        put("subLocationId", sh.subLocationId)
        if (sh.subLocationName.isNotBlank()) put("subLocationName", sh.subLocationName)
        put("locationId", sh.locationId)
        if (sh.locationName.isNotBlank()) put("locationName", sh.locationName)
        put("territoryId", sh.territoryId)
        if (sh.territoryName.isNotBlank()) put("territoryName", sh.territoryName)
        put("cityId", sh.cityId)
        if (sh.cityName.isNotBlank()) put("cityName", sh.cityName)
        put("shopCategory", sh.shopCategory.ifBlank { sh.shopType })
        put("shopType", sh.shopType.ifBlank { sh.shopCategory })
        put("lat", sh.lat)
        put("lng", sh.lng)
        put("allowedRadiusMeters", sh.allowedRadiusMeters)
        put("gpsStatus", sh.gpsStatus)
        sh.gpsAccuracyMeters?.let { put("gpsAccuracyMeters", it) }
        put("status", sh.status)
        put("lastVerifiedAt", sh.lastVerifiedAt)
        put("lastAudit", sh.lastAudit)
        put("addressLine", sh.addressLine.ifBlank { sh.address })
        put("address", sh.address.ifBlank { sh.addressLine })
    }

    fun serializeDistributor(d: Distributor): JSONObject = JSONObject().apply {
        put("id", d.id)
        put("code", d.code)
        put("setupName", d.setupName)
        put("distributorName", d.distributorName)
        put("fatherName", d.fatherName)
        put("cnic", d.cnic)
        put("phone", d.phone)
        put("cityId", d.cityId)
        put("territoryId", d.territoryId)
        put("completeAddress", d.completeAddress)
        put("groupIds", JSONArray(d.groupIds))
        val psObj = JSONObject()
        d.productStatuses.forEach { (k, v) -> psObj.put(k, v) }
        put("productStatuses", psObj)
        put("lat", d.lat)
        put("lng", d.lng)
        put("gpsStatus", d.gpsStatus)
        put("status", d.status)
        put("createdAt", d.createdAt)
    }

    fun serializeProductGroup(g: ProductGroup): JSONObject = JSONObject().apply {
        put("id", g.id)
        put("code", g.code)
        put("name", g.name)
        put("divisionTitle", g.divisionTitle)
        put("description", g.description)
        put("storageType", g.storageType)
        put("defaultStackingMaxCtn", g.defaultStackingMaxCtn)
        put("badgeStyle", g.badgeStyle)
        put("status", g.status)
    }

    fun serializeProduct(p: Product): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("sku", p.sku)
        put("name", p.name)
        put("groupId", p.groupId)
        put("groupName", p.groupName)
        put("unitPerBox", p.unitPerBox)
        put("unitsCountPerBox", p.unitsCountPerBox)
        put("boxesPerCtn", p.boxesPerCtn)
        put("pricePerBoxRs", p.pricePerBoxRs)
        put("stackingStandard", p.stackingStandard)
        put("shelfLifeDays", p.shelfLifeDays)
        put("status", p.status)
    }

    fun serializePlanItem(item: PlanItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("formId", item.formId)
        put("formTitle", item.formTitle)
        put("targetType", item.targetType)
        put("targetId", item.targetId)
        put("targetLabel", item.targetLabel)
        put("cityId", item.cityId)
        put("territoryId", item.territoryId)
        put("shopIds", JSONArray(item.shopIds))
        put("status", item.status)
        item.doneAt?.let { put("doneAt", it) }
        item.reportKind?.let { put("reportKind", it) }
        item.reportId?.let { put("reportId", it) }
        item.reportCode?.let { put("reportCode", it) }
        item.skipReason?.let { put("skipReason", it) }
    }

    fun serializePlan(p: AuditPlan): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("auditorId", p.auditorId)
        p.auditorName?.let { put("auditorName", it) }
        put("planDate", p.planDate)
        put("title", p.title)
        put("notes", p.notes)
        put("status", p.status)
        p.startedAt?.let { put("startedAt", it) }
        val itemsArr = JSONArray()
        p.items.forEach { itemsArr.put(serializePlanItem(it)) }
        put("items", itemsArr)
    }

    fun parseDuty(o: JSONObject): AuditorDuty {
        val cId = o.optString("cityId", o.optString("city_id"))
        val cIds = o.optJSONArray("cityIds").toStringList().ifEmpty {
            if (cId.isNotBlank()) listOf(cId) else emptyList()
        }
        val tId = o.optString("territoryId", o.optString("territory_id"))
        val tIds = o.optJSONArray("territoryIds").toStringList().ifEmpty {
            if (tId.isNotBlank()) listOf(tId) else emptyList()
        }
        val cName = o.optString("cityName")
        val cNames = o.optJSONArray("cityNames").toStringList().ifEmpty {
            if (cName.isNotBlank()) listOf(cName) else emptyList()
        }
        val tName = o.optString("territoryName")
        val tNames = o.optJSONArray("territoryNames").toStringList().ifEmpty {
            if (tName.isNotBlank()) listOf(tName) else emptyList()
        }
        val distNames = o.optJSONArray("distributorNames").toStringList()
        return AuditorDuty(
            id = o.optString("id"),
            code = o.optString("code"),
            auditorId = o.optString("auditorId", o.optString("auditor_id")),
            cityId = cId.ifBlank { cIds.firstOrNull() ?: "" },
            cityIds = cIds,
            cityName = cName.ifBlank { cNames.joinToString(", ") },
            cityNames = cNames,
            territoryId = tId.ifBlank { tIds.firstOrNull() ?: "" },
            territoryIds = tIds,
            territoryName = tName.ifBlank { tNames.joinToString(", ") },
            territoryNames = tNames,
            locationId = o.optString("locationId", o.optString("location_id")),
            distributorsCount = o.optInt("distributorsCount", distNames.size),
            distributorNames = distNames,
            shopsCount = o.optInt("shopsCount", 0),
            title = o.optString("title"),
            dutyDate = o.optString("dutyDate", o.optString("duty_date")),
            notes = o.optString("notes"),
            status = o.optString("status", "Assigned"),
            assignedByName = o.optString("assignedByName", o.optString("assigned_by_name", o.optString("assignedBy"))),
            notified = o.optBoolean("notified", true),
            createdAt = o.optString("createdAt", o.optString("created_at")),
            updatedAt = o.optString("updatedAt", o.optString("updated_at"))
        )
    }

    fun serializeDuty(d: AuditorDuty): JSONObject = JSONObject().apply {
        put("id", d.id)
        put("code", d.code)
        put("auditorId", d.auditorId)
        put("cityId", d.cityId)
        put("cityIds", JSONArray(d.cityIds))
        put("cityName", d.cityName)
        put("cityNames", JSONArray(d.cityNames))
        put("territoryId", d.territoryId)
        put("territoryIds", JSONArray(d.territoryIds))
        put("territoryName", d.territoryName)
        put("territoryNames", JSONArray(d.territoryNames))
        put("locationId", d.locationId)
        put("distributorsCount", d.distributorsCount)
        put("distributorNames", JSONArray(d.distributorNames))
        put("shopsCount", d.shopsCount)
        put("title", d.title)
        put("dutyDate", d.dutyDate)
        put("notes", d.notes)
        put("status", d.status)
        put("assignedByName", d.assignedByName)
        put("notified", d.notified)
        put("createdAt", d.createdAt)
        put("updatedAt", d.updatedAt)
    }

    fun parseNotification(o: JSONObject): AuditorNotification = AuditorNotification(
        id = o.optString("id"),
        userId = o.optString("userId", o.optString("user_id")),
        title = o.optString("title"),
        message = o.optString("message"),
        type = o.optString("type", "duty_assigned"),
        entityType = o.optString("entityType", o.optString("entity_type", "duty")),
        entityId = o.optString("entityId", o.optString("entity_id")),
        isRead = o.optBoolean("isRead", o.optBoolean("is_read", false)),
        createdAt = o.optString("createdAt", o.optString("created_at"))
    )

    fun serializeNotification(n: AuditorNotification): JSONObject = JSONObject().apply {
        put("id", n.id)
        put("userId", n.userId)
        put("title", n.title)
        put("message", n.message)
        put("type", n.type)
        put("entityType", n.entityType)
        put("entityId", n.entityId)
        put("isRead", n.isRead)
        put("createdAt", n.createdAt)
    }

    fun parseDutySyncResult(o: JSONObject): DutySyncResult {
        val dutiesArr = o.optJSONArray("duties")
        val duties = mutableListOf<AuditorDuty>()
        if (dutiesArr != null) {
            for (i in 0 until dutiesArr.length()) {
                dutiesArr.optJSONObject(i)?.let { duties.add(parseDuty(it)) }
            }
        }
        val notifArr = o.optJSONArray("notifications") ?: o.optJSONArray("unreadNotifications")
        val notifs = mutableListOf<AuditorNotification>()
        if (notifArr != null) {
            for (i in 0 until notifArr.length()) {
                notifArr.optJSONObject(i)?.let { notifs.add(parseNotification(it)) }
            }
        }
        val parsedUser = o.optJSONObject("user")?.let { parseUser(it) }
        val auditorId = o.optString("auditorId").ifBlank { parsedUser?.id ?: "" }
        val hasBootstrapFields =
            o.has("cities") || o.has("territories") || o.has("distributors") || o.has("shops") || o.has("forms")
        val parsedBootstrap = if (hasBootstrapFields) {
            val fallbackUser = parsedUser ?: AppUser(
                id = auditorId,
                code = "",
                username = "",
                fullName = "",
                roleId = "",
                assignedCityIds = o.optJSONArray("assignedCityIds").toStringList(),
                assignedTerritoryIds = o.optJSONArray("assignedTerritoryIds").toStringList(),
                assignedGroupIds = o.optJSONArray("assignedGroupIds").toStringList()
            )
            parseBootstrap(
                o,
                Session(
                    user = fallbackUser,
                    role = null,
                    surface = "mobile",
                    isGlobal = false
                )
            )
        } else null

        return DutySyncResult(
            auditorId = auditorId,
            user = parsedUser,
            assignedCityIds = o.optJSONArray("assignedCityIds").toStringList(),
            assignedTerritoryIds = o.optJSONArray("assignedTerritoryIds").toStringList(),
            assignedGroupIds = o.optJSONArray("assignedGroupIds").toStringList(),
            duties = duties,
            notifications = notifs,
            unreadCount = o.optInt("unreadCount", notifs.count { !it.isRead }),
            bootstrap = parsedBootstrap,
            syncedAt = o.optString("syncedAt")
        )
    }
}

