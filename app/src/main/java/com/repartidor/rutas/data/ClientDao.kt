package com.repartidor.rutas.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients ORDER BY nombre ASC")
    fun getAll(): Flow<List<Client>>

    @Query("SELECT * FROM clients WHERE nombre LIKE :query OR telefono LIKE :query")
    suspend fun search(query: String): List<Client>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(client: Client): Long

    @Delete
    suspend fun delete(client: Client)
}
