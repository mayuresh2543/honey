package com.honeyfile.security.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.honeyfile.security.databinding.ItemCapturedImageBinding
import java.io.File

class CapturedImageAdapter(private val onImageClick: (File) -> Unit) :
    RecyclerView.Adapter<CapturedImageAdapter.ImageViewHolder>() {

    private var imageFiles: List<File> = emptyList()

    fun submitList(files: List<File>) {
        imageFiles = files
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemCapturedImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(imageFiles[position])
    }

    override fun getItemCount(): Int = imageFiles.size

    inner class ImageViewHolder(private val binding: ItemCapturedImageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(file: File) {
            Glide.with(binding.ivCaptured.context)
                .load(file)
                .signature(com.bumptech.glide.signature.ObjectKey(file.lastModified()))
                .centerCrop()
                .into(binding.ivCaptured)

            binding.root.setOnClickListener { onImageClick(file) }
        }
    }
}
