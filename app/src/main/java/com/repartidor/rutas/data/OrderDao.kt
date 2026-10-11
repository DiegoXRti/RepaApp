package com.repartidor.rutas.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    @Query("SELECT * FROM orders WHERE entregado = 0 ORDER BY secuencia ASC, creadoEn ASC")
    fun observarPendientes(): Flow<List<Order>>

    @Query("SELECT * FROM orders WHERE entregado = 0")
    suspend fun obtenerPendientesLista(): List<Order>

    @Insert
    suspend fun insertar(order: Order): Long

    @Update
    suspend fun actualizar(order: Order)

    @Update
    suspend fun actualizarTodos(orders: List<Order>)

    @Query("SELECT * FROM orders WHERE entregado = 1 AND cobroEntregado = 0 ORDER BY creadoEn ASC")
    fun observarPorCobrar(): Flow<List<Order>>

    @Query("UPDATE orders SET entregado = 1, propina = :propina, entregadoEn = :entregadoEn WHERE id = :id")
    suspend fun marcarEntregado(id: Long, propina: Double, entregadoEn: Long)

    @Query("UPDATE orders SET cobroEntregado = 1 WHERE id = :id")
    suspend fun marcarCobroEntregado(id: Long)

    @Query("DELETE FROM orders")
    suspend fun eliminarTodo()

    @Query("UPDATE orders SET propina = 0.0")
    suspend fun reiniciarPropinas()

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun eliminar(id: Long)

    @Query("DELETE FROM orders WHERE entregado = 1 AND cobroEntregado = 1")
    suspend fun limpiarEntregados()

    @Query("SELECT IFNULL(SUM(propina), 0.0) FROM orders WHERE entregado = 1")
    fun observarTotalPropinas(): Flow<Double>

    @Query("SELECT IFNULL(SUM(propina), 0.0) FROM orders WHERE entregado = 1 AND entregadoEn >= :inicio AND entregadoEn <= :fin")
    fun obtenerPropinasRango(inicio: Long, fin: Long): Flow<Double>
}
