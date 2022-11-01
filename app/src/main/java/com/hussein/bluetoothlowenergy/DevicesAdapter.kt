package com.hussein.bluetoothlowenergy

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.hussein.bluetoothlowenergy.databinding.DeviceItemLayoutBinding

class DevicesAdapter (context: Context, private val mList: List<BluetoothDevice>, listener: OnDeviceListener) : RecyclerView.Adapter<DevicesAdapter.ViewHolder>() {

    private var mContext=context
    private var mListener:OnDeviceListener=listener

    var selectedDevicePos:Int?=null
        get() = field
        set(value) { field = value }
    // create new views
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // inflates the card_view_design view
        val binding = DeviceItemLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding);
    }

    // binds the list items to a view
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val model = mList[position]

        // sets the text to the textview from our itemHolder class
        holder.tvDevice.text = model.name


        if(selectedDevicePos==position)
        {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.purple_200))
        }
        else
        {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.white))
        }

        holder.itemView.setOnClickListener{
            holder.itemView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.purple_200))
            mListener.onSelectDevice(position)
        }

    }

    // return the number of the items in the list
    override fun getItemCount(): Int {
        return mList.size
    }

    // Holds the views for adding it to image and text
    class ViewHolder(itemview: DeviceItemLayoutBinding) : RecyclerView.ViewHolder(itemview.root) {
        var binding=itemview
        var tvDevice = binding.tvDevice
    }
}