package com.vnweather.app.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vnweather.app.databinding.ItemCityBinding
import com.vnweather.app.domain.City

class CityAdapter(
    private val onClick: (City) -> Unit,
    private val onDelete: (City) -> Unit
) : RecyclerView.Adapter<CityAdapter.VH>() {

    private val items = mutableListOf<City>()
    private var showDelete = false

    fun submit(cities: List<City>, showDelete: Boolean) {
        this.showDelete = showDelete
        items.clear()
        items.addAll(cities)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemCityBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val binding: ItemCityBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(city: City) {
            binding.textName.text = city.name
            binding.textSubtitle.apply {
                text = city.subtitle
                visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            binding.buttonDelete.visibility = if (showDelete) View.VISIBLE else View.GONE
            binding.buttonDelete.setOnClickListener { onDelete(city) }
            binding.root.setOnClickListener { onClick(city) }
        }
    }
}
