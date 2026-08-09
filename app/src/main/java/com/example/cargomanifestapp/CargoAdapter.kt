package com.example.cargomanifestapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cargomanifestapp.databinding.ItemCargoBinding

class CargoAdapter(
    private val onDeleteClick: (CargoItem) -> Unit
) : ListAdapter<CargoItem, CargoAdapter.CargoViewHolder>(CargoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CargoViewHolder {
        val binding = ItemCargoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CargoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CargoViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class CargoViewHolder(
        private val binding: ItemCargoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CargoItem) {
            // Judul Kartu: PTI
            binding.tvPti.text = "PTI: ${item.pti}"

            // Informasi Pcs & SubTotal
            binding.tvPcsSubtotal.text = "Pcs: ${item.pcsQty} | SubTotal: ${item.subTotal} Kg"

            // Format NO PAG
            val pagInfo = if (item.noPag.isNotBlank()) " | PAG: ${item.noPag}" else ""
            binding.tvDescCustomer.text = "Desc: ${item.description} | Cust: ${item.customer}$pagInfo"

            // Tombol Hapus Item
            binding.btnDelete.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }

    class CargoDiffCallback : DiffUtil.ItemCallback<CargoItem>() {
        override fun areItemsTheSame(oldItem: CargoItem, newItem: CargoItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CargoItem, newItem: CargoItem): Boolean {
            return oldItem == newItem
        }
    }
}
