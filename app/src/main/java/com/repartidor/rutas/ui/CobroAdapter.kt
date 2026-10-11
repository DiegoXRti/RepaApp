package com.repartidor.rutas.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.repartidor.rutas.data.Order
import com.repartidor.rutas.databinding.ItemCobroBinding

class CobroAdapter(
    private val onConfirmar: (Order) -> Unit
) : ListAdapter<Order, CobroAdapter.CobroViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CobroViewHolder {
        val binding = ItemCobroBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CobroViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CobroViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CobroViewHolder(private val binding: ItemCobroBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(order: Order) {
            binding.textCobroNombre.text = order.nombre
            binding.textCobroMonto.text = "Cobrar: $%.2f (%s)".format(order.precio, order.tipoPago)
            binding.btnConfirmarCobro.setOnClickListener { onConfirmar(order) }
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Order>() {
            override fun areItemsTheSame(oldItem: Order, newItem: Order) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Order, newItem: Order) = oldItem == newItem
        }
    }
}
