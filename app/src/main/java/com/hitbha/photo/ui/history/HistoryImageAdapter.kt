package com.hitbha.photo.ui.history

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.hitbha.photo.databinding.ItemHistoryImageBinding

class HistoryImageAdapter(
    private val images: List<HistoryImage>,
    private val onItemClick: (HistoryImage) -> Unit
) : RecyclerView.Adapter<HistoryImageAdapter.ViewHolder>() {

    data class HistoryImage(
        val uri: Uri,
        val fileName: String,
        val dateAdded: Long,
        val fileSize: Long
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(images[position])
    }

    override fun getItemCount() = images.size

    inner class ViewHolder(private val binding: ItemHistoryImageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HistoryImage) {
            Glide.with(binding.root.context)
                .load(item.uri)
                .override(256, 256)
                .thumbnail(
                    Glide.with(binding.root.context)
                        .load(item.uri)
                        .override(64, 64)
                )
                .into(binding.ivHistoryImage)
            binding.tvFileName.text = item.fileName
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
