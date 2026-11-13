package com.hitbha.photo.ui.batch

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.hitbha.photo.R

class BatchImageAdapter(
    private val onRemove: (Int) -> Unit,
    private val onImageClick: (Int) -> Unit = {}
) : ListAdapter<Uri, BatchImageAdapter.ViewHolder>(DIFF_CALLBACK) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.iv_image)
        val tvRemove: TextView = view.findViewById(R.id.tv_remove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_batch_image_large, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val uri = getItem(position)
        Glide.with(holder.itemView.context).load(uri).into(holder.imageView)
        holder.imageView.setOnClickListener { onImageClick(holder.bindingAdapterPosition) }
        holder.tvRemove.setOnClickListener { onRemove(holder.bindingAdapterPosition) }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Uri>() {
            override fun areItemsTheSame(oldItem: Uri, newItem: Uri) = oldItem == newItem
            override fun areContentsTheSame(oldItem: Uri, newItem: Uri) = oldItem == newItem
        }
    }
}
