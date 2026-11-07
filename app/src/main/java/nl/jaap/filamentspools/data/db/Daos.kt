package nl.jaap.filamentspools.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val COUNTS = """
    SELECT p.*,
        COALESCE(SUM(CASE WHEN s.status = 'boxed' THEN 1 ELSE 0 END), 0) AS boxed,
        COALESCE(SUM(CASE WHEN s.status = 'on_spool' THEN 1 ELSE 0 END), 0) AS onSpool
    FROM products p LEFT JOIN spools s ON s.productId = p.id
"""

private const val PRODUCT_SEARCH = """
    (:q = '' OR p.articleName LIKE '%' || :q || '%' OR p.articleNumber LIKE '%' || :q || '%'
        OR p.colorFamily LIKE '%' || :q || '%' OR p.line LIKE '%' || :q || '%'
        OR p.material LIKE '%' || :q || '%')
"""

@Dao
interface ProductDao {
    @Upsert
    suspend fun upsert(products: List<ProductEntity>)

    /** Adds products that are not cached yet; never overwrites catalog data. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(products: List<ProductEntity>)

    @Query(
        COUNTS + " WHERE " + PRODUCT_SEARCH + """
        AND (:material IS NULL OR p.material = :material)
        AND (:brand IS NULL OR p.brand = :brand)
        AND (:line IS NULL OR p.line = :line)
        AND (:color IS NULL OR p.colorFamily = :color)
        GROUP BY p.id
        HAVING CASE :owned
            WHEN 'Owned' THEN boxed + onSpool > 0
            WHEN 'NotOwned' THEN boxed + onSpool = 0
            WHEN 'Boxed' THEN boxed > 0
            WHEN 'OnSpool' THEN onSpool > 0
            WHEN 'RunningOut' THEN onSpool > 0 AND boxed = 0
            ELSE 1 END
        ORDER BY p.articleNumber""",
    )
    fun observeFilteredRaw(
        q: String, material: String?, brand: String?, line: String?, color: String?, owned: String,
    ): Flow<List<ProductWithCounts>>

    fun observeFiltered(
        q: String, material: String?, brand: String?, line: String?, color: String?, owned: OwnedFilter,
    ): Flow<List<ProductWithCounts>> = observeFilteredRaw(q.trim(), material, brand, line, color, owned.name)

    @Query(COUNTS + " WHERE p.id = :id GROUP BY p.id")
    fun observe(id: Long): Flow<ProductWithCounts?>

    @Query(COUNTS + " GROUP BY p.id HAVING onSpool > 0 AND boxed = 0 ORDER BY p.articleNumber")
    fun observeRunningOut(): Flow<List<ProductWithCounts>>

    @Query("SELECT DISTINCT material FROM products WHERE material IS NOT NULL ORDER BY material")
    fun distinctMaterials(): Flow<List<String>>

    @Query("SELECT DISTINCT brand FROM products WHERE brand IS NOT NULL ORDER BY brand")
    fun distinctBrands(): Flow<List<String>>

    @Query("SELECT DISTINCT line FROM products WHERE line IS NOT NULL ORDER BY line")
    fun distinctLines(): Flow<List<String>>

    @Query("SELECT DISTINCT colorFamily FROM products WHERE colorFamily IS NOT NULL ORDER BY colorFamily")
    fun distinctColors(): Flow<List<String>>

    @Query("DELETE FROM products")
    suspend fun clear()
}

@Dao
interface SpoolDao {
    @Upsert
    suspend fun upsert(spools: List<SpoolEntity>)

    @Query("DELETE FROM spools WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    @Query("DELETE FROM spools")
    suspend fun clear()

    @Query("SELECT uuid FROM spools")
    suspend fun allUuids(): List<String>

    /** Makes the table equal to [spools], except rows in [keep], which are left as they are. */
    @Transaction
    suspend fun replaceAll(spools: List<SpoolEntity>, keep: Collection<String> = emptyList()) {
        val wanted = spools.mapTo(HashSet()) { it.uuid } + keep
        allUuids().filter { it !in wanted }.chunked(500).forEach { deleteByUuids(it) }
        upsert(spools)
    }

    @Transaction
    @Query(
        """SELECT s.* FROM spools s LEFT JOIN products p ON p.id = s.productId
        WHERE s.status = :status AND """ + PRODUCT_SEARCH + """
        ORDER BY s.updatedAt DESC""",
    )
    fun observeByStatusRaw(status: String, q: String): Flow<List<SpoolWithProduct>>

    fun observeByStatus(status: String, q: String): Flow<List<SpoolWithProduct>> =
        observeByStatusRaw(status, q.trim())

    @Transaction
    @Query("SELECT * FROM spools WHERE uuid = :uuid")
    fun observe(uuid: String): Flow<SpoolWithProduct?>

    @Transaction
    @Query("SELECT * FROM spools WHERE productId = :productId ORDER BY updatedAt DESC")
    fun observeForProduct(productId: Long): Flow<List<SpoolWithProduct>>
}
