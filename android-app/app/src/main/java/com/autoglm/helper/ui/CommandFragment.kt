package com.autoglm.helper.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.autoglm.helper.Command
import com.autoglm.helper.CommandAdapter
import com.autoglm.helper.CommandRepository
import com.autoglm.helper.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CommandFragment : Fragment() {

    private lateinit var commandListView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var repository: CommandRepository
    private lateinit var adapter: CommandAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_commands, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repository = CommandRepository(requireContext())
        commandListView = view.findViewById(R.id.commandList)
        emptyView = view.findViewById(R.id.emptyCommandText)
        adapter = CommandAdapter(
            onPublish = { /* no-op here */ },
            onEdit = { /* no-op here */ },
            onDelete = { cmd -> deleteCommand(cmd) },
            onDetail = { cmd -> showDetail(cmd) }
        )
        commandListView.layoutManager = LinearLayoutManager(requireContext())
        commandListView.adapter = adapter

        lifecycleScope.launch(Dispatchers.IO) {
            val data = repository.getCommands()
            withContext(Dispatchers.Main) {
                adapter.submit(data)
                emptyView.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun deleteCommand(command: Command) {
        lifecycleScope.launch(Dispatchers.IO) {
            val data = repository.deleteCommand(command.id)
            withContext(Dispatchers.Main) {
                adapter.submit(data)
                emptyView.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showDetail(command: Command) {
        Toast.makeText(requireContext(), "${command.title}\n${command.content}", Toast.LENGTH_SHORT).show()
    }
}
