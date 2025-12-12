package com.autoglm.helper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CommandAdapter(
    private val onPublish: (Command) -> Unit,
    private val onEdit: (Command) -> Unit,
    private val onDelete: (Command) -> Unit
) : RecyclerView.Adapter<CommandAdapter.CommandViewHolder>() {

    private val items = mutableListOf<Command>()

    fun submit(commands: List<Command>) {
        items.clear()
        items.addAll(commands)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommandViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_command, parent, false)
        return CommandViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommandViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CommandViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.commandTitle)
        private val contentView: TextView = itemView.findViewById(R.id.commandContent)
        private val publishButton: Button = itemView.findViewById(R.id.publishCommand)
        private val editButton: Button = itemView.findViewById(R.id.editCommand)
        private val deleteButton: Button = itemView.findViewById(R.id.deleteCommand)

        fun bind(command: Command) {
            titleView.text = command.title
            contentView.text = command.content

            publishButton.setOnClickListener { onPublish(command) }
            editButton.setOnClickListener { onEdit(command) }
            deleteButton.setOnClickListener { onDelete(command) }
        }
    }
}
