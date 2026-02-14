package ke.afyaclaim.pos.domain.model

/** Local replication state of an offline-created record. */
enum class SyncState(val wire: String) {
    PENDING("pending"),
    SYNCED("synced"),
    CONFLICT("conflict"),
    REJECTED("rejected");

    companion object {
        fun fromWire(value: String): SyncState = entries.firstOrNull { it.wire == value } ?: PENDING
    }
}

enum class ProductCategory(val wire: String, val label: String) {
    MALARIA_TEST("malaria_test", "Malaria tests"),
    ANTIMALARIAL("antimalarial", "Antimalarials"),
    FAMILY_PLANNING("family_planning", "Family planning"),
    ORS_ZINC("ors_zinc", "ORS & zinc"),
    OTHER("other", "Other");

    companion object {
        fun fromWire(value: String): ProductCategory = entries.firstOrNull { it.wire == value } ?: OTHER
    }
}

enum class AgeBand(val wire: String) {
    UNDER_5("0-4"),
    CHILD("5-14"),
    YOUTH("15-24"),
    ADULT("25-49"),
    SENIOR("50+");

    companion object {
        fun fromWire(value: String?): AgeBand? = entries.firstOrNull { it.wire == value }
    }
}

enum class Sex(val wire: String, val label: String) {
    FEMALE("female", "Female"),
    MALE("male", "Male");

    companion object {
        fun fromWire(value: String?): Sex? = entries.firstOrNull { it.wire == value }
    }
}

enum class TestResult(val wire: String, val label: String) {
    POSITIVE("positive", "Positive"),
    NEGATIVE("negative", "Negative");

    companion object {
        fun fromWire(value: String?): TestResult? = entries.firstOrNull { it.wire == value }
    }
}

enum class ClaimStatus(val wire: String, val label: String) {
    SUBMITTED("submitted", "Submitted"),
    UNDER_REVIEW("under_review", "Under review"),
    APPROVED("approved", "Approved"),
    REJECTED("rejected", "Rejected"),
    PAID("paid", "Paid");

    companion object {
        fun fromWire(value: String?): ClaimStatus = entries.firstOrNull { it.wire == value } ?: SUBMITTED
    }
}

enum class RecordType(val wire: String, val label: String) {
    SALE("sale", "Sale"),
    CLAIM("claim", "Claim"),
    STOCK_ADJUSTMENT("stock_adjustment", "Stock adjustment");

    companion object {
        fun fromWire(value: String): RecordType? = entries.firstOrNull { it.wire == value }
    }
}

enum class StockReason(val wire: String, val label: String) {
    RECEIVED("restock", "Received"),
    DAMAGED("damaged", "Damaged"),
    EXPIRED("expired", "Expired"),
    COUNT("count_correction", "Count correction"),
}
