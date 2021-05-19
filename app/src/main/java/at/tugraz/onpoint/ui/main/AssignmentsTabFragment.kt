package at.tugraz.onpoint.ui.main

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.tugraz.onpoint.R
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList


class AssignmentsTabFragment : Fragment() {
    private lateinit var pageViewModel: PageViewModel
    private var assignmentsList = arrayListOf<Assignment>()
    private var completeState = arrayListOf<Assignment>()
    private var adapter: AssignmentsAdapter? = null
    private var latestAssignmentId: Int = 1 // Unique ID for assignment
    // TODO latestAssignmentId replaced with one obtained from the DB (auto-incrementing integer). See how the TodoList does it.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageViewModel = ViewModelProvider(this).get(PageViewModel::class.java).apply {
            setIndex(arguments?.getInt(ARG_SECTION_NUMBER) ?: 1)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_assignments, container, false)
        // List of dummy assigments
        // TODO replace with selection of assignments from the DB on startup. See how the TodoList does it.
        for (i in 0..50) {
            addAssignmentToAssignmentList(
                Assignment(
                    "Dummy Assignment $i",
                    "Dummy Description $i",
                    // Dummy deadline:

                    Date(Date().time + (24L * 3600 * 1000 * i) + 60000L),
                    arrayListOf<URL>(
                        URL("https://www.tugraz.at"),
                        URL("https://tc.tugraz.at"),
                    )
                ),
            )
        }

        // Create the the Recyclerview, make it a linear list (not a grid), assign the list of
        // items to it and provide and adapter constructing each element of the list as a TextView
        val assignmentsRecView: RecyclerView = root.findViewById(R.id.assignmentsList)
        assignmentsRecView.layoutManager = LinearLayoutManager(this.context)
        adapter =
            this.context?.let { AssignmentsAdapter(assignmentsList, it, parentFragmentManager) }
        assignmentsRecView.adapter = this.adapter

        var searchView: SearchView = root.findViewById(R.id.assignment_searchview)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener{
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText != null) {
                    var temp = completeState.toMutableList()
                    assignmentsList.clear()
                    assignmentsList.addAll(filter(temp, newText) as ArrayList<Assignment>)
                    adapter!!.notifyDataSetChanged()
                    return true
                }
                return false
            }

            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query != null) {
                    var temp = completeState.toMutableList()
                    assignmentsList.clear()
                    assignmentsList.addAll(filter(temp, query) as ArrayList<Assignment>)
                    adapter!!.notifyDataSetChanged()
                    return true
                }
                return false
            }
        })

        return root
    }

    /**
     * Appends an assignment, refreshing the recycler view and the notifications for the deadlines.
     */
    fun addAssignmentToAssignmentList(
        assignment: Assignment,
    ) {
        latestAssignmentId += 1
        assignment.id = latestAssignmentId
        assignmentsList.add(assignment)
        completeState.add(assignment)
        adapter?.notifyDataSetChanged()

    }

     fun filter(assignments: List<Assignment>, search: String): List<Assignment>{
        val lowerCaseQuery = search.toLowerCase(Locale.ROOT)
         val found: MutableList<Assignment> = ArrayList()
        for (assi in assignments) {
            val text = assi.title.toLowerCase(Locale.ROOT)
            if (text.contains(lowerCaseQuery)) {
                found.add(assi);
            }
        }
        return found
    }

    companion object {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private const val ARG_SECTION_NUMBER = "section_number"

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        @JvmStatic
        fun newInstance(sectionNumber: Int): AssignmentsTabFragment {
            return AssignmentsTabFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SECTION_NUMBER, sectionNumber)
                }
            }
        }
    }
}

data class Assignment(
    val title: String,
    val description: String,
    val deadline: Date,
    val links: ArrayList<URL>,
    var id: Int? = null,
) {
    fun linksToMultiLineString(): String {
        val text: StringBuilder = StringBuilder()
        links.forEach {
            text.append(it)
            text.append('\n')
        }
        return text.toString()
    }

    // Call this function ONLY after the ID is set.
    fun buildAndScheduleNotification(context: Context, reminder_date : Calendar) {
        val intentToLaunchNotification = Intent(context, ScheduledNotificationReceiver::class.java)
        intentToLaunchNotification.putExtra(
            "title",
            context.getString(R.string.assignment_notification_title)
        )
        intentToLaunchNotification.putExtra("text", this.title + ": " + this.deadline.toString())
        intentToLaunchNotification.putExtra("notificationId", id)
        // Schedule notification
        val pending = PendingIntent.getBroadcast(
            context,
            id!!,
            intentToLaunchNotification,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.set(AlarmManager.RTC_WAKEUP, reminder_date.timeInMillis, pending)
    }
}

private class AssignmentsAdapter(
    val items: ArrayList<Assignment>,
    val context: Context,
    val fragmentManager: FragmentManager
) :
    RecyclerView.Adapter<ViewHolder>() {
    override fun getItemCount(): Int {
        return items.size
    }

    // Factory creating each element of the RecyclerView
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(context).inflate(R.layout.assignment_list_entry, parent, false),
            fragmentManager
        )
    }

    // Filler of the content of each TextView in the RecyclerView
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.assignmentListEntryTextView.text = items[position].title
        holder.assignment = items[position]
    }
}

private class ViewHolder(view: View, val fragmentManager: FragmentManager) :
    RecyclerView.ViewHolder(view) {
    lateinit var assignment: Assignment
    val assignmentListEntryTextView: TextView = view.findViewById(R.id.assignmentsListEntry)

    init {
        assignmentListEntryTextView.setOnClickListener {
            displayAssignmentDetailsAsDialog()
        }
    }

    private fun displayAssignmentDetailsAsDialog() {
        // At this point this.assignment should be initialised, as is is set by onBindViewHolder().
        // Otherwise it would not make much sense: how can we click (tick, mark) a view
        // which was never displayed?
        val fragment = AssignmentDetailsFragment(assignment)
        fragment.show(fragmentManager, null)
    }
}
