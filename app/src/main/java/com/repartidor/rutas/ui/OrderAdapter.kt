package com.repartidor.rutas.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.repartidor.rutas.data.Order
import com.repartidor.rutas.databinding.ItemOrderBinding

class OrderAdapter(
    private val isFormat24h: Boolean,
    private val onLlamar: (Order) -> Unit,
    private val onLongLlamar: (Order) -> Unit,
    private val onNavegar: (Order) -> Unit,
    private val onEntregado: (Order) -> Unit,
    private val onLongEntregado: (Order) -> Unit,
    private val onEliminar: (Order) -> Unit,
    private val onEditar: (Order) -> Unit
) : ListAdapter<Order, OrderAdapter.OrderViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class OrderViewHolder(private val binding: ItemOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(order: Order, position: Int) {
            // REPARACIÓN CRÍTICA: Resetear estado de animación para evitar elementos encimados o invisibles
            binding.root.animate().cancel()
            binding.root.alpha = 1f
            binding.root.translationY = 0f
            binding.root.scaleX = 1f
            binding.root.scaleY = 1f
            
            binding.btnEntregado.animate().cancel()
            binding.btnEntregado.scaleX = 1f
            binding.btnEntregado.scaleY = 1f

            binding.textNombre.text = order.nombre
            binding.textPosicion.text = "#${position + 1}"
            binding.textPrecio.text = "$%.2f".format(order.precio)
            binding.textTipoPago.text = order.tipoPago.uppercase()

            val pattern = if (isFormat24h) "HH:mm" else "hh:mm a"
            val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
            binding.textHora.text = sdf.format(java.util.Date(order.creadoEn))

            if (order.frecuenciaPropina != null) {
                binding.tagPropina.visibility = View.VISIBLE
                binding.tagPropina.text = when(order.frecuenciaPropina) {
                    "Siempre" -> "SIEMPRE 💰"
                    "A veces" -> "A VECES ⚖️"
                    "Nunca" -> "NUNCA ❌"
                    else -> order.frecuenciaPropina.uppercase()
                }
                
                val color = when(order.frecuenciaPropina) {
                    "Siempre" -> "#0B7A3E"
                    "A veces" -> "#FBC02D"
                    "Nunca" -> "#666666"
                    else -> "#666666"
                }
                binding.tagPropina.backgroundTintList = ColorStateList.valueOf(Color.parseColor(color))
            } else {
                binding.tagPropina.visibility = View.GONE
            }

            val ahora = System.currentTimeMillis()
            val minutos = (ahora - order.creadoEn) / 60000
            val emoji = when {
                minutos <= 30 -> "🟢"
                minutos <= 60 -> "🟡"
                else -> "🔴"
            }
            binding.textStatus.text = emoji

            val tieneUbicacion = order.latitud != null && order.longitud != null
            binding.textUbicacion.text = if (tieneUbicacion) {
                order.direccionTexto ?: "Ubicacion recibida"
            } else {
                "Sin ubicacion (no se puede incluir al optimizar)"
            }

            if (!order.notas.isNullOrBlank()) {
                binding.textNotas.text = "Nota: ${order.notas}"
                binding.textNotas.visibility = View.VISIBLE
            } else {
                binding.textNotas.visibility = View.GONE
            }

            if (!order.fotoUri.isNullOrBlank()) {
                binding.imgFoto.visibility = View.VISIBLE
                binding.imgFoto.load(order.fotoUri) {
                    crossfade(true)
                    placeholder(android.R.drawable.ic_menu_gallery)
                    error(android.R.drawable.ic_menu_report_image)
                }
            } else {
                binding.imgFoto.visibility = View.GONE
            }

            binding.btnLlamar.setOnClickListener { onLlamar(order) }
            binding.btnLlamar.setOnLongClickListener { 
                onLongLlamar(order)
                true
            }

            binding.btnNavegar.setOnClickListener { onNavegar(order) }
            binding.btnNavegar.isEnabled = tieneUbicacion
            
            binding.btnEntregado.setOnClickListener { 
                binding.btnEntregado.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction {
                        binding.btnEntregado.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .withEndAction {
                                onEntregado(order)
                            }
                            .start()
                    }
                    .start()
            }

            binding.btnEntregado.setOnLongClickListener {
                onLongEntregado(order)
                true
            }

            binding.root.setOnClickListener { onEditar(order) }
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Order>() {
            override fun areItemsTheSame(oldItem: Order, newItem: Order) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Order, newItem: Order) = oldItem == newItem
        }
    }
}
