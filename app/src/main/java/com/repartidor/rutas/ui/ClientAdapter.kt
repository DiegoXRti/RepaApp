package com.repartidor.rutas.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.repartidor.rutas.data.Client
import com.repartidor.rutas.databinding.ItemClientBinding

class ClientAdapter(
    private val onClientClick: (Client) -> Unit,
    private val onDeleteClick: (Client) -> Unit
) : ListAdapter<Client, ClientAdapter.ClientViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClientViewHolder {
        val binding = ItemClientBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ClientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClientViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ClientViewHolder(private val binding: ItemClientBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(client: Client) {
            binding.textClientName.text = client.nombre
            binding.textClientPhone.text = client.telefono
            
            binding.imgClient.load(client.fotoDomicilioUri) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_report_image)
                transformations(CircleCropTransformation())
            }

            binding.root.setOnClickListener { onClientClick(client) }
            binding.btnDeleteClient.setOnClickListener { onDeleteClick(client) }
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Client>() {
            override fun areItemsTheSame(oldItem: Client, newItem: Client) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Client, newItem: Client) = oldItem == newItem
        }
    }
}
